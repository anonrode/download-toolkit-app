package com.anonrode.downloader.engine

import android.os.Environment
import android.os.StatFs
import com.anonrode.downloader.data.api.VpsApiClient
import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import com.anonrode.downloader.data.net.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Aria2Engine private constructor() {

    companion object {
        val instance: Aria2Engine by lazy { Aria2Engine() }
        private val DEFAULT_UA = HttpClient.DEFAULT_UA
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // The repository owns the task list + persistence; the engine owns download
    // orchestration. Re-exposed as `tasks` so existing callers (ViewModel,
    // DownloadService) that observe engine.tasks are unchanged.
    private val repo = DownloadRepository()
    val tasks: StateFlow<List<DownloadTask>> = repo.tasks

    // Shared reference — set by MainViewModel on init so every
    // resolve call uses the user's configured key + server URL.
    var apiClient: VpsApiClient? = null

    var maxConcurrentDownloads = 2
    var parallelSocketsPerFile = 16
    var minSplitSizeMb = 1
    var diskCacheMb = 32
    var fileAllocation = "trunc"
    var defaultQuality = "720p"
    var autoOrganizeByShow = true
    var instantSocialDownload = false

    /** Wire the persistence location and reload a previous run's tasks. */
    fun initPersistence(dir: File) = repo.initPersistence(dir)

    private val okHttpClient: OkHttpClient get() = HttpClient.download

    fun getAnonStorageDir(showName: String? = null): File {
        val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Anon")
        if (!base.exists()) base.mkdirs()
        return if (autoOrganizeByShow && !showName.isNullOrBlank()) {
            val sanitized = showName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val showDir = File(base, sanitized)
            if (!showDir.exists()) showDir.mkdirs()
            showDir
        } else {
            base
        }
    }

    fun getStorageStats(): Pair<Double, Double> {
        return try {
            val path = Environment.getExternalStorageDirectory().path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availBlocks = stat.availableBlocksLong

            val totalGb = (totalBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)
            val freeGb = (availBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)
            Pair(freeGb, totalGb)
        } catch (e: Exception) {
            Pair(45.2, 128.0)
        }
    }

    fun isEpisodeDownloaded(showName: String, episodeNumber: Int): Boolean {
        val dir = getAnonStorageDir(showName)
        if (!dir.exists()) return false
        val epPrefix = String.format("E%02d", episodeNumber)
        val files = dir.listFiles() ?: return false
        return files.any { it.name.contains(epPrefix) && !it.name.endsWith(".part") && !it.name.contains(".chunk") }
    }

    fun enqueue(
        showName: String,
        episodeNumber: Int,
        episodeTitle: String,
        originalUrl: String,
        // Social/direct links (share sheet, SocialModal) are already the media
        // URL and must NOT go through the drama resolver. Passing isDirect pins
        // the resolved URL to the source and forces the yt-dlp backend, so
        // startTask skips resolve and routes straight to segment+mux.
        isDirect: Boolean = false
    ): DownloadTask {
        val showDir = getAnonStorageDir(showName)
        val sanitizedShow = showName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val sanitizedEp = episodeTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fileName = "${sanitizedShow}_E${String.format("%02d", episodeNumber)}_${sanitizedEp}.mp4"

        val targetPath = File(showDir, fileName).absolutePath
        val tempPath = "$targetPath.part"

        val task = DownloadTask(
            id = "${originalUrl.hashCode()}_${System.currentTimeMillis()}",
            showName = showName,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            originalUrl = originalUrl,
            resolvedUrl = if (isDirect) originalUrl else null,
            backend = if (isDirect) "yt-dlp" else "aria2c",
            targetFilePath = targetPath,
            tempFilePath = tempPath,
            status = TaskStatus.QUEUED,
            createdAt = System.currentTimeMillis()
        )

        repo.addFirst(task)
        processQueue()
        return task
    }

    fun pause(taskId: String) {
        if (repo.find(taskId) == null) return
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        // A yt-dlp task runs as a native subprocess that the coroutine cancel
        // above won't stop; kill it explicitly (no-op for native-engine tasks).
        YoutubeDlDownloader.cancel(taskId)
        repo.update(taskId) { it.copy(status = TaskStatus.PAUSED, speedBytesPerSec = 0.0) }
        repo.persist()
        processQueue()
    }

    fun resume(taskId: String) {
        val task = repo.find(taskId) ?: return
        if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.FAILED) {
            repo.update(taskId) { it.copy(status = TaskStatus.QUEUED, errorMessage = null) }
            repo.persist()
            processQueue()
        }
    }

    fun cancel(taskId: String) {
        val task = repo.find(taskId) ?: return
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        YoutubeDlDownloader.cancel(taskId)

        File(task.tempFilePath).delete()
        for (i in 0..32) {
            File("${task.tempFilePath}.part$i").delete()
        }

        repo.remove(taskId)
        processQueue()
    }

    fun deleteCompleted(task: DownloadTask) {
        File(task.targetFilePath).delete()
        File(task.tempFilePath).delete()
        repo.remove(task.id)
    }

    private fun processQueue() {
        val running = repo.snapshot().count { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING }
        val slots = maxConcurrentDownloads - running
        if (slots > 0) {
            val queued = repo.snapshot().filter { it.status == TaskStatus.QUEUED }.take(slots)
            queued.forEach { startTask(it) }
        }
    }

    private fun startTask(task: DownloadTask) {
        val taskId = task.id
        val job = scope.launch {
            // Shared byte counter for the parallel segments. AtomicLong instead of
            // synchronized(task): the task is now immutable, so there is no shared
            // object to lock, and progress is a running total the workers bump.
            val downloaded = AtomicLong(0L)
            try {
                repo.update(taskId) { it.copy(status = TaskStatus.RESOLVING) }

                // Use the shared VpsApiClient that has the user's key
                val client = apiClient
                    ?: throw Exception("API client not configured. Open Settings and enter your API Key.")

                var headers = task.headers
                var resolvedUrl = task.resolvedUrl
                var backend = task.backend
                if (resolvedUrl.isNullOrBlank()) {
                    val recipe = client.resolveEpisode(task.originalUrl, defaultQuality)
                    resolvedUrl = recipe.url
                    headers = recipe.headers
                    backend = recipe.backend
                    repo.update(taskId) {
                        it.copy(resolvedUrl = recipe.url, headers = recipe.headers, backend = recipe.backend)
                    }
                }

                repo.update(taskId) { it.copy(status = TaskStatus.DOWNLOADING) }

                val directUrl = resolvedUrl ?: throw Exception("Failed to resolve link")

                // HLS / social links can't be pulled as a plain byte stream --
                // an .m3u8 is a playlist, not the media. Route those to yt-dlp
                // (bundled) which fetches the segments and muxes a real mp4; keep
                // the fast native segment engine below for plain files.
                if (YoutubeDlDownloader.handles(backend, directUrl)) {
                    repo.update(taskId) {
                        it.copy(totalBytes = YoutubeDlDownloader.scaleTotal(), downloadedBytes = 0L)
                    }
                    var lastPctTime = System.currentTimeMillis()
                    var lastPct = 0f
                    YoutubeDlDownloader.download(taskId, directUrl, task.targetFilePath, headers) { pct ->
                        val now = System.currentTimeMillis()
                        val dt = now - lastPctTime
                        if (dt >= 500 || pct >= 100f) {
                            // Synthetic speed off the % delta and the scale, so the
                            // UI shows motion without real byte counts from yt-dlp.
                            val speed = ((pct - lastPct) / 100f *
                                YoutubeDlDownloader.scaleTotal()) / (dt / 1000.0).coerceAtLeast(0.001)
                            lastPct = pct; lastPctTime = now
                            repo.update(taskId) {
                                it.copy(downloadedBytes = YoutubeDlDownloader.scaleDownloaded(pct),
                                        speedBytesPerSec = speed)
                            }
                        }
                    }
                    if (isActive) {
                        repo.update(taskId) {
                            it.copy(status = TaskStatus.COMPLETED,
                                    downloadedBytes = YoutubeDlDownloader.scaleTotal(),
                                    speedBytesPerSec = 0.0)
                        }
                    }
                    return@launch
                }

                // Header applier shared by probe + every segment/single request,
                // so the User-Agent fallback lives in one place.
                fun Request.Builder.applyHeaders(): Request.Builder {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                    if (!headers.containsKey("User-Agent")) addHeader("User-Agent", DEFAULT_UA)
                    return this
                }

                // 1. Probe to detect Content-Length & Accept-Ranges. HEAD, not
                //    GET -- a GET here would open the whole media stream just to
                //    read two headers, then throw it away.
                var totalSize = 0L
                var acceptsRanges = false
                try {
                    val headRes = okHttpClient.newCall(
                        Request.Builder().url(directUrl).applyHeaders().head().build()
                    ).execute()
                    headRes.header("Content-Length")?.let { totalSize = it.toLongOrNull() ?: 0L }
                    headRes.header("Accept-Ranges")?.let {
                        if (it.equals("bytes", ignoreCase = true)) acceptsRanges = true
                    }
                    headRes.close()
                } catch (_: Exception) {}

                val tempFile = File(task.tempFilePath)
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                // Emit throttled progress (>=500ms) from the current byte total.
                fun reportProgress() {
                    val now = System.currentTimeMillis()
                    val diff = now - lastTime
                    if (diff >= 500) {
                        val soFar = downloaded.get()
                        val speed = (soFar - lastBytes).toDouble() / (diff / 1000.0)
                        lastBytes = soFar
                        lastTime = now
                        repo.update(taskId) { it.copy(downloadedBytes = soFar, speedBytesPerSec = speed) }
                    }
                }

                // 2. Multi-Segment Parallel Download (files > 4MB with range support)
                val segments = parallelSocketsPerFile.coerceIn(4, 16)
                if (acceptsRanges && totalSize > 4 * 1024 * 1024L) {
                    repo.update(taskId) { it.copy(totalBytes = totalSize) }
                    val chunkSize = (totalSize + segments - 1) / segments
                    val chunkFiles = mutableListOf<File>()
                    val expectedLens = mutableListOf<Long>()
                    val workerJobs = mutableListOf<Job>()

                    // Count bytes already on disk from a previous run so resumed
                    // progress starts from the right place, not zero.
                    for (i in 0 until segments) {
                        val cf = File("${task.tempFilePath}.part$i")
                        if (cf.exists()) downloaded.addAndGet(cf.length())
                    }

                    for (i in 0 until segments) {
                        val start = i * chunkSize
                        val end = if (i == segments - 1) totalSize - 1 else ((i + 1) * chunkSize - 1)
                        val chunkFile = File("${task.tempFilePath}.part$i")
                        chunkFiles.add(chunkFile)
                        expectedLens.add(end - start + 1)

                        val chunkJob = launch {
                            val chunkExisting = if (chunkFile.exists()) chunkFile.length() else 0L
                            val workerStart = start + chunkExisting
                            if (workerStart <= end) {
                                val segRes = okHttpClient.newCall(
                                    Request.Builder().url(directUrl).applyHeaders()
                                        .addHeader("Range", "bytes=$workerStart-$end").build()
                                ).execute()
                                val body = segRes.body ?: return@launch
                                val buffer = ByteArray(64 * 1024)

                                body.byteStream().use { input ->
                                    FileOutputStream(chunkFile, chunkExisting > 0L).use { o ->
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            if (!isActive) break
                                            o.write(buffer, 0, read)
                                            downloaded.addAndGet(read.toLong())
                                            // Only one worker reports at a time so
                                            // the throttle state isn't raced.
                                            synchronized(downloaded) { reportProgress() }
                                        }
                                    }
                                }
                                segRes.close()
                            }
                        }
                        workerJobs.add(chunkJob)
                    }

                    workerJobs.joinAll()

                    // Merge all chunks sequentially into target file
                    if (isActive) {
                        // Guard against silent corruption: a segment whose socket
                        // dropped leaves a short .part file, but the old code merged
                        // whatever was there and marked the task COMPLETED -- a
                        // truncated video that plays until it cuts out. Verify every
                        // chunk is exactly the length its byte-range demands.
                        chunkFiles.forEachIndexed { i, chunk ->
                            val actual = if (chunk.exists()) chunk.length() else 0L
                            if (actual != expectedLens[i]) {
                                throw Exception(
                                    "Segment ${i + 1}/$segments incomplete " +
                                    "($actual/${expectedLens[i]} bytes) - link may have expired")
                            }
                        }

                        val finalFile = File(task.targetFilePath)
                        if (finalFile.exists()) finalFile.delete()

                        FileOutputStream(finalFile).use { out ->
                            val mergeBuffer = ByteArray(128 * 1024)
                            chunkFiles.forEach { chunk ->
                                if (chunk.exists()) {
                                    FileInputStream(chunk).use { input ->
                                        var read: Int
                                        while (input.read(mergeBuffer).also { read = it } != -1) {
                                            out.write(mergeBuffer, 0, read)
                                        }
                                    }
                                    chunk.delete()
                                }
                            }
                        }

                        // Final sanity check: merged size must equal Content-Length.
                        val mergedLen = finalFile.length()
                        if (mergedLen != totalSize) {
                            finalFile.delete()
                            throw Exception("Merged file size mismatch ($mergedLen/$totalSize bytes)")
                        }

                        repo.update(taskId) {
                            it.copy(status = TaskStatus.COMPLETED,
                                    downloadedBytes = totalSize, speedBytesPerSec = 0.0)
                        }
                    }
                } else {
                    // Fallback: Single-Connection Resilient Streaming
                    var existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                    downloaded.set(existingBytes)

                    val singleReqBuilder = Request.Builder().url(directUrl).applyHeaders()
                    if (existingBytes > 0L) singleReqBuilder.addHeader("Range", "bytes=$existingBytes-")

                    val response = okHttpClient.newCall(singleReqBuilder.build()).execute()
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val knownTotal = if (response.code == 206) {
                        existingBytes + contentLength
                    } else {
                        existingBytes = 0L
                        downloaded.set(0L)
                        contentLength
                    }
                    repo.update(taskId) { it.copy(totalBytes = knownTotal, downloadedBytes = downloaded.get()) }

                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { ins ->
                        FileOutputStream(tempFile, existingBytes > 0L).use { out ->
                            var read: Int
                            while (ins.read(buffer).also { read = it } != -1) {
                                if (!isActive) break
                                out.write(buffer, 0, read)
                                downloaded.addAndGet(read.toLong())
                                reportProgress()
                            }
                        }
                    }
                    response.close()

                    if (isActive) {
                        // Same corruption guard as the multi-segment path: if the
                        // stream dropped mid-download, read() returns -1 and the loop
                        // exits normally, so without this a half-file would be renamed
                        // and marked COMPLETED. Enforce only when the server gave a
                        // size (a chunked response reports -1).
                        if (knownTotal > 0L && tempFile.length() != knownTotal) {
                            throw Exception(
                                "Incomplete download (${tempFile.length()}/$knownTotal bytes) - connection dropped")
                        }
                        val finalFile = File(task.targetFilePath)
                        if (finalFile.exists()) finalFile.delete()
                        tempFile.renameTo(finalFile)

                        repo.update(taskId) {
                            it.copy(status = TaskStatus.COMPLETED,
                                    downloadedBytes = if (knownTotal > 0L) knownTotal else downloaded.get(),
                                    speedBytesPerSec = 0.0)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Gracefully handled — paused or cancelled
            } catch (e: Exception) {
                repo.update(taskId) {
                    it.copy(status = TaskStatus.FAILED,
                            errorMessage = e.message ?: "Download failed", speedBytesPerSec = 0.0)
                }
            } finally {
                activeJobs.remove(taskId)
                // Persist the settled state (COMPLETED/FAILED, or PAUSED via
                // cancellation) so it survives a restart. Runs on every exit path.
                repo.persist()
                processQueue()
            }
        }
        activeJobs[taskId] = job
    }
}
