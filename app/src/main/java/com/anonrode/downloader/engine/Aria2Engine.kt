package com.anonrode.downloader.engine

import android.os.Environment
import android.os.StatFs
import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class Aria2Engine private constructor() {

    companion object {
        val instance: Aria2Engine by lazy { Aria2Engine() }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    var maxConcurrentDownloads = 2
    var parallelSocketsPerFile = 16
    var minSplitSizeMb = 1
    var diskCacheMb = 32
    var fileAllocation = "trunc"
    var defaultQuality = "720p"
    var autoOrganizeByShow = true
    var instantSocialDownload = false

    private val okHttpClient: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("SSL")
        ssl.init(null, trustAll, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun getAnonStorageDir(showName: String? = null): File {
        val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Anon")
        if (!base.exists()) base.mkdirs()
        return if (autoOrganizeByShow && !showName.isNullOrBlank()) {
            val sanitized = showName.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
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
        originalUrl: String
    ): DownloadTask {
        val showDir = getAnonStorageDir(showName)
        val sanitizedShow = showName.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
        val sanitizedEp = episodeTitle.replace(Regex("[\\/:*?\"<>|]"), "_").trim()
        val fileName = "${sanitizedShow}_E${String.format("%02d", episodeNumber)}_${sanitizedEp}.mp4"

        val targetPath = File(showDir, fileName).absolutePath
        val tempPath = "$targetPath.part"

        val task = DownloadTask(
            id = "${originalUrl}_${System.currentTimeMillis()}",
            showName = showName,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
            originalUrl = originalUrl,
            targetFilePath = targetPath,
            tempFilePath = tempPath,
            status = TaskStatus.QUEUED
        )

        val updated = _tasks.value.toMutableList()
        updated.add(0, task)
        _tasks.value = updated

        processQueue()
        return task
    }

    fun pause(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        task.status = TaskStatus.PAUSED
        task.speedBytesPerSec = 0.0
        _tasks.value = _tasks.value.toList()
        processQueue()
    }

    fun resume(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.FAILED) {
            task.status = TaskStatus.QUEUED
            task.errorMessage = null
            _tasks.value = _tasks.value.toList()
            processQueue()
        }
    }

    fun cancel(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        File(task.tempFilePath).delete()
        for (i in 0..32) {
            File("${task.tempFilePath}.part$i").delete()
        }

        val updated = _tasks.value.toMutableList()
        updated.removeAll { it.id == taskId }
        _tasks.value = updated

        processQueue()
    }

    fun deleteCompleted(task: DownloadTask) {
        File(task.targetFilePath).delete()
        File(task.tempFilePath).delete()
        val updated = _tasks.value.toMutableList()
        updated.removeAll { it.id == task.id }
        _tasks.value = updated
    }

    private fun processQueue() {
        val running = _tasks.value.count { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING }
        val slots = maxConcurrentDownloads - running
        if (slots > 0) {
            val queued = _tasks.value.filter { it.status == TaskStatus.QUEUED }.take(slots)
            queued.forEach { startTask(it) }
        }
    }

    private fun startTask(task: DownloadTask) {
        val job = scope.launch {
            try {
                task.status = TaskStatus.RESOLVING
                _tasks.value = _tasks.value.toList()

                if (task.resolvedUrl.isNullOrBlank()) {
                    val recipe = com.anonrode.downloader.data.api.VpsApiClient().resolveEpisode(task.originalUrl)
                    task.resolvedUrl = recipe.url
                    task.headers = recipe.headers
                }

                task.status = TaskStatus.DOWNLOADING
                _tasks.value = _tasks.value.toList()

                val directUrl = task.resolvedUrl ?: throw Exception("Failed to resolve link")
                val reqBuilder = Request.Builder().url(directUrl)
                task.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                if (!task.headers.containsKey("User-Agent")) {
                    reqBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }

                // 1. Probe Head to detect Content-Length & Accept-Ranges
                var totalSize = 0L
                var acceptsRanges = false
                try {
                    val headReq = reqBuilder.build()
                    val headRes = okHttpClient.newCall(headReq).execute()
                    val clHeader = headRes.header("Content-Length")
                    if (clHeader != null) totalSize = clHeader.toLongOrNull() ?: 0L
                    val arHeader = headRes.header("Accept-Ranges")
                    if (arHeader != null && arHeader.equals("bytes", ignoreCase = true)) {
                        acceptsRanges = true
                    }
                    headRes.close()
                } catch (_: Exception) {}

                val tempFile = File(task.tempFilePath)
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                // 2. Multi-Segment Parallel Download (for files > 4MB with range support)
                val segments = parallelSocketsPerFile.coerceIn(4, 16)
                if (acceptsRanges && totalSize > 4 * 1024 * 1024L) {
                    task.totalBytes = totalSize
                    val chunkSize = (totalSize + segments - 1) / segments
                    val chunkFiles = mutableListOf<File>()
                    val workerJobs = mutableListOf<Job>()

                    for (i in 0 until segments) {
                        val start = i * chunkSize
                        val end = if (i == segments - 1) totalSize - 1 else ((i + 1) * chunkSize - 1)
                        val chunkFile = File("${task.tempFilePath}.part$i")
                        chunkFiles.add(chunkFile)

                        val chunkJob = launch {
                            var chunkExisting = if (chunkFile.exists()) chunkFile.length() else 0L
                            val workerStart = start + chunkExisting
                            if (workerStart <= end) {
                                val segReq = reqBuilder.addHeader("Range", "bytes=$workerStart-$end").build()
                                val segRes = okHttpClient.newCall(segReq).execute()
                                val body = segRes.body ?: return@launch
                                val buffer = ByteArray(64 * 1024)
                                val ins = body.byteStream()
                                val out = FileOutputStream(chunkFile, chunkExisting > 0L)

                                out.use { o ->
                                    ins.use { input ->
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            if (!isActive || task.status != TaskStatus.DOWNLOADING) break
                                            o.write(buffer, 0, read)
                                            synchronized(task) {
                                                task.downloadedBytes += read
                                            }

                                            val now = System.currentTimeMillis()
                                            val diff = now - lastTime
                                            if (diff >= 500) {
                                                val bytesDiff = task.downloadedBytes - lastBytes
                                                task.speedBytesPerSec = (bytesDiff.toDouble() / (diff.toDouble() / 1000.0))
                                                lastBytes = task.downloadedBytes
                                                lastTime = now
                                                _tasks.value = _tasks.value.toList()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        workerJobs.add(chunkJob)
                    }

                    workerJobs.joinAll()

                    // Merge all chunks sequentially into target file
                    if (isActive && task.status == TaskStatus.DOWNLOADING) {
                        val finalFile = File(task.targetFilePath)
                        if (finalFile.exists()) finalFile.delete()
                        val finalOut = FileOutputStream(finalFile)
                        val mergeBuffer = ByteArray(128 * 1024)

                        finalOut.use { out ->
                            chunkFiles.forEach { chunk ->
                                if (chunk.exists()) {
                                    val ins = FileInputStream(chunk)
                                    ins.use { input ->
                                        var read: Int
                                        while (input.read(mergeBuffer).also { read = it } != -1) {
                                            out.write(mergeBuffer, 0, read)
                                        }
                                    }
                                    chunk.delete()
                                }
                            }
                        }

                        task.status = TaskStatus.COMPLETED
                        task.speedBytesPerSec = 0.0
                        _tasks.value = _tasks.value.toList()
                    }
                } else {
                    // Fallback: Single-Connection Resilient Streaming
                    var existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                    task.downloadedBytes = existingBytes

                    if (existingBytes > 0L) {
                        reqBuilder.addHeader("Range", "bytes=$existingBytes-")
                    }

                    val response = okHttpClient.newCall(reqBuilder.build()).execute()
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    if (response.code == 206) {
                        task.totalBytes = existingBytes + contentLength
                    } else {
                        task.totalBytes = contentLength
                        task.downloadedBytes = 0L
                        existingBytes = 0L
                    }

                    val buffer = ByteArray(64 * 1024)
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(tempFile, existingBytes > 0L)

                    outputStream.use { out ->
                        inputStream.use { ins ->
                            var read: Int
                            while (ins.read(buffer).also { read = it } != -1) {
                                if (!isActive || task.status != TaskStatus.DOWNLOADING) break
                                out.write(buffer, 0, read)
                                task.downloadedBytes += read

                                val now = System.currentTimeMillis()
                                val diff = now - lastTime
                                if (diff >= 500) {
                                    val bytesDiff = task.downloadedBytes - lastBytes
                                    task.speedBytesPerSec = (bytesDiff.toDouble() / (diff.toDouble() / 1000.0))
                                    lastBytes = task.downloadedBytes
                                    lastTime = now
                                    _tasks.value = _tasks.value.toList()
                                }
                            }
                        }
                    }

                    if (isActive && task.status == TaskStatus.DOWNLOADING) {
                        val finalFile = File(task.targetFilePath)
                        if (finalFile.exists()) finalFile.delete()
                        tempFile.renameTo(finalFile)

                        task.status = TaskStatus.COMPLETED
                        task.speedBytesPerSec = 0.0
                        _tasks.value = _tasks.value.toList()
                    }
                }
            } catch (e: CancellationException) {
                // Gracefully handled
            } catch (e: Exception) {
                task.status = TaskStatus.FAILED
                task.errorMessage = e.message ?: "Download failed"
                task.speedBytesPerSec = 0.0
                _tasks.value = _tasks.value.toList()
            } finally {
                activeJobs.remove(task.id)
                processQueue()
            }
        }
        activeJobs[task.id] = job
    }
}
