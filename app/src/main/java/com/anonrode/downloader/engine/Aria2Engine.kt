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

    private val repo = DownloadRepository()
    val tasks: StateFlow<List<DownloadTask>> = repo.tasks

    var apiClient: VpsApiClient? = null

    var maxConcurrentDownloads = 2
    var parallelSocketsPerFile = 16
    var minSplitSizeMb = 1
    var diskCacheMb = 32
    var fileAllocation = "trunc"
    var defaultQuality = "720p"
    var autoOrganizeByShow = true
    var instantSocialDownload = false

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
            backend = if (isDirect || YoutubeDlDownloader.isSocial(originalUrl)) "yt-dlp" else "aria2c",
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
        File(task.targetFilePath).delete()
        File("${task.targetFilePath}.aria2").delete()

        repo.remove(taskId)
        processQueue()
    }

    fun deleteCompleted(task: DownloadTask) {
        File(task.targetFilePath).delete()
        File(task.tempFilePath).delete()
        File("${task.targetFilePath}.aria2").delete()
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

                val client = apiClient
                    ?: throw Exception("API client not configured. Open Settings and enter your API Key.")

                var headers = task.headers
                var resolvedUrl = task.resolvedUrl
                var currentBackend = task.backend

                if (resolvedUrl.isNullOrBlank()) {
                    val recipe = client.resolveEpisode(task.originalUrl, defaultQuality)
                    resolvedUrl = recipe.url
                    headers = recipe.headers
                    currentBackend = recipe.backend
                    repo.update(taskId) {
                        it.copy(resolvedUrl = recipe.url, headers = recipe.headers, backend = recipe.backend)
                    }
                }

                repo.update(taskId) { it.copy(status = TaskStatus.DOWNLOADING) }

                val directUrl = resolvedUrl ?: throw Exception("Failed to resolve link")

                repo.update(taskId) {
                    it.copy(totalBytes = YoutubeDlDownloader.scaleTotal(), downloadedBytes = 0L)
                }

                var lastPctTime = System.currentTimeMillis()
                var lastPct = 0f
                YoutubeDlDownloader.download(
                    taskId = taskId,
                    sourceUrl = directUrl,
                    targetFilePath = task.targetFilePath,
                    headers = headers,
                    backend = currentBackend,
                    parallelSockets = parallelSocketsPerFile
                ) { pct ->
                    val now = System.currentTimeMillis()
                    val dt = now - lastPctTime
                    if (dt >= 500 || pct >= 100f) {
                        val speed = ((pct - lastPct) / 100f *
                            YoutubeDlDownloader.scaleTotal()) / (dt / 1000.0).coerceAtLeast(0.001)
                        lastPct = pct
                        lastPctTime = now
                        repo.update(taskId) {
                            it.copy(
                                downloadedBytes = YoutubeDlDownloader.scaleDownloaded(pct),
                                speedBytesPerSec = speed
                            )
                        }
                    }
                }

                if (isActive) {
                    repo.update(taskId) {
                        it.copy(
                            status = TaskStatus.COMPLETED,
                            downloadedBytes = YoutubeDlDownloader.scaleTotal(),
                            speedBytesPerSec = 0.0
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Gracefully handled — paused or cancelled
            } catch (e: Exception) {
                repo.update(taskId) {
                    it.copy(
                        status = TaskStatus.FAILED,
                        errorMessage = e.message ?: "Download failed",
                        speedBytesPerSec = 0.0
                    )
                }
            } finally {
                activeJobs.remove(taskId)
                repo.persist()
                processQueue()
            }
        }
        activeJobs[taskId] = job
    }
}
