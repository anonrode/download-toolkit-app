package com.anonrode.downloader.engine

import android.os.Environment
import android.os.StatFs
import com.anonrode.downloader.data.api.VpsApiClient
import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class Aria2Engine private constructor() {

    companion object {
        val instance: Aria2Engine by lazy { Aria2Engine() }
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
            try {
                repo.update(taskId) { it.copy(status = TaskStatus.RESOLVING) }

                // Use the shared VpsApiClient that has the user's key
                val client = apiClient
                    ?: throw Exception("API client not configured. Open Settings and enter your API Key.")

                var headers = task.headers
                var resolvedUrl = task.resolvedUrl
                if (resolvedUrl.isNullOrBlank()) {
                    val recipe = client.resolveEpisode(task.originalUrl, defaultQuality)
                    resolvedUrl = recipe.url
                    headers = recipe.headers
                    // backend is persisted (drives resume routing) even though the
                    // single yt-dlp path no longer branches on it at run time.
                    repo.update(taskId) {
                        it.copy(resolvedUrl = recipe.url, headers = recipe.headers, backend = recipe.backend)
                    }
                }

                repo.update(taskId) { it.copy(status = TaskStatus.DOWNLOADING) }

                val directUrl = resolvedUrl ?: throw Exception("Failed to resolve link")

                // One download path for everything. yt-dlp muxes HLS/social, and
                // for plain files it drives the bundled aria2c -- which writes a
                // single file with real multi-connection congestion control,
                // matching the Termux tool. This replaced a hand-rolled 16-socket
                // OkHttp engine that left .part fragments on pause and collapsed
                // to ~40kB/s when file-locker CDNs throttled the many connections.
                repo.update(taskId) {
                    it.copy(totalBytes = YoutubeDlDownloader.scaleTotal(), downloadedBytes = 0L)
                }
                var lastPctTime = System.currentTimeMillis()
                var lastPct = 0f
                YoutubeDlDownloader.download(taskId, directUrl, task.targetFilePath, headers) { pct ->
                    val now = System.currentTimeMillis()
                    val dt = now - lastPctTime
                    if (dt >= 500 || pct >= 100f) {
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
