package com.anonrode.downloader.engine

import com.anonrode.downloader.AnonApp
import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * HLS / social download path, backed by the bundled yt-dlp + ffmpeg.
 *
 * The native OkHttp segment engine in Aria2Engine can only fetch plain byte
 * streams; an HLS `.m3u8` is a playlist of segments that must be pulled and
 * muxed into a container. Rather than reimplement that (the ffmpeg-in-Kotlin
 * route), we hand the URL to yt-dlp, which does exactly this and writes a real,
 * portable mp4 -- the same approach Seal uses.
 *
 * Progress: yt-dlp reports a 0-100 float, not byte counts, so we drive the
 * task's totalBytes/downloadedBytes as a synthetic 0..100 scale. progressPercent
 * (downloaded/total) then renders correctly without changing the model or UI.
 */
object YoutubeDlDownloader {

    private const val PROGRESS_SCALE = 100L

    /**
     * Download [sourceUrl] to [targetFilePath] via yt-dlp, reporting progress
     * through [onProgress]. Runs on IO. Throws on failure (caller marks FAILED)
     * and honours cancellation via [YoutubeDL.destroyProcessById] keyed on
     * [taskId], which the engine calls from pause()/cancel().
     */
    suspend fun download(
        taskId: String,
        sourceUrl: String,
        targetFilePath: String,
        headers: Map<String, String>,
        onProgress: (percent: Float) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        if (!AnonApp.ensureReady()) {
            throw IllegalStateException(
                "Video engine still starting up. Try again in a moment.")
        }

        val target = File(targetFilePath)
        target.parentFile?.mkdirs()
        // Strip our own ".mp4"; yt-dlp fills the real container via %(ext)s, then
        // we normalise to the intended name below so the rest of the app (player,
        // gallery scan) finds it where it expects.
        val stem = target.absolutePath.removeSuffix(".mp4").removeSuffix(".part")

        val request = YoutubeDLRequest(sourceUrl).apply {
            // Merge to mp4 so the output is one portable, gallery-playable file.
            addOption("-o", "$stem.%(ext)s")
            addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            addOption("--merge-output-format", "mp4")
            addOption("--no-playlist")
            addOption("--no-mtime")
            // Pass through any hotlink headers the resolver supplied (Referer,
            // User-Agent) so the CDN doesn't 403 mid-stream.
            headers.forEach { (k, v) -> addOption("--add-header", "$k:$v") }

            // Download plain files with the bundled aria2c, exactly like the
            // Termux tool did: it writes ONE file (+ a .aria2 control file) with
            // real multi-connection congestion control, instead of the old
            // engine's 16 stray .part fragments that also collapsed to ~40kB/s
            // when a file-locker CDN throttled the many-connections-per-IP.
            // yt-dlp still fetches+muxes HLS itself; only progressive files go
            // through aria2c.
            if (!isHls(sourceUrl)) {
                addOption("--downloader", "libaria2c.so")
                // Termux learned some file-locker CDNs (kissorgrab et al.)
                // throttle or drop many-connection requests -- it dropped those
                // to a single connection. Mirror that: few connections for the
                // locker hosts, the usual 16 elsewhere.
                val conns = if (isConnectionSensitive(sourceUrl)) "1" else "16"
                addOption("--downloader-args",
                    "aria2c:-x $conns -s $conns --min-split-size=1M --continue=true")
            }
        }

        YoutubeDL.getInstance().execute(request, taskId) { progress, _, _ ->
            if (progress >= 0f) onProgress(progress)
        }

        // yt-dlp wrote "$stem.<ext>"; land it exactly at targetFilePath (.mp4).
        if (!target.exists()) {
            val produced = target.parentFile
                ?.listFiles { f -> f.name.startsWith(File(stem).name + ".") }
                ?.maxByOrNull { it.length() }
            if (produced != null && produced.absolutePath != target.absolutePath) {
                produced.renameTo(target)
            }
        }
        if (!target.exists() || target.length() <= 0L) {
            throw IllegalStateException("yt-dlp produced no output file")
        }
    }

    /** Cancel a running yt-dlp process (best-effort). */
    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {}
    }

    /** Synthetic totalBytes for the progress scale (see class note). */
    fun scaleTotal(): Long = PROGRESS_SCALE

    /** Map a 0-100 percent onto the synthetic byte scale for DownloadTask. */
    fun scaleDownloaded(percent: Float): Long =
        (percent.coerceIn(0f, 100f) / 100f * PROGRESS_SCALE).toLong()

    /** HLS streams are muxed by yt-dlp itself, not handed to aria2c. */
    private fun isHls(url: String): Boolean {
        val u = url.lowercase()
        return u.contains(".m3u8") || u.contains("/hls/") || u.contains("manifest")
    }

    /**
     * File-locker CDNs that throttle or drop many-connection requests, so aria2c
     * must use a single connection (the exact lesson the Termux tool encoded for
     * kissorgrab). Matches the known locker hosts by substring; everything else
     * gets the full 16 connections.
     */
    private fun isConnectionSensitive(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("kissorgrab") || u.contains("downloadwella") ||
                u.contains("wella") || u.contains("streamwish") ||
                u.contains("filelions") || u.contains("vidhide")
    }
}
