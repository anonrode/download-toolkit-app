package com.anonrode.downloader.engine

import com.anonrode.downloader.AnonApp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal download path powered by bundled yt-dlp + ffmpeg + aria2c.
 *
 * Routing logic:
 * 1. Social Media / Extractor Pages (Instagram, TikTok, YouTube, Twitter) & HLS (.m3u8):
 *    yt-dlp runs natively (no external aria2c) so yt-dlp's Python extractors and
 *    ffmpeg muxers can extract and mux real MP4 media files.
 *
 * 2. Resolved Direct CDN Files (NKiri, DramaKey, Pluto, 9jaRocks, downloadwella):
 *    yt-dlp drives the bundled libaria2c.so with full command-line Referer,
 *    Origin, and User-Agent flags, plus connection sensitivity (1 connection
 *    for file-locker hosts, 16 parallel sockets for fast CDNs).
 */
object YoutubeDlDownloader {

    private const val PROGRESS_SCALE = 100L
    private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Download [sourceUrl] to [targetFilePath] via yt-dlp, reporting progress
     * through [onProgress].
     */
    suspend fun download(
        taskId: String,
        sourceUrl: String,
        targetFilePath: String,
        headers: Map<String, String>,
        backend: String = "aria2c",
        parallelSockets: Int = 16,
        onProgress: (percent: Float) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        if (!AnonApp.ensureReady()) {
            throw IllegalStateException(
                "Video engine still starting up. Try again in a moment.")
        }

        val target = File(targetFilePath)
        target.parentFile?.mkdirs()

        val stem = target.absolutePath.removeSuffix(".mp4").removeSuffix(".part")

        val referer = headers["Referer"] ?: headers["referer"] ?: ""
        val ua = headers["User-Agent"] ?: headers["user-agent"] ?: DEFAULT_UA
        val origin = headers["Origin"] ?: headers["origin"] ?: ""

        val isSocialUrl = isSocial(sourceUrl)
        val isHlsUrl = isHls(sourceUrl)
        val isExtractorTask = backend == "yt-dlp" || isSocialUrl || isHlsUrl

        val request = YoutubeDLRequest(sourceUrl).apply {
            addOption("-o", "$stem.%(ext)s")
            addOption("--no-playlist")
            addOption("--no-mtime")

            if (referer.isNotBlank()) addOption("--referer", referer)
            if (ua.isNotBlank()) addOption("--user-agent", ua)
            headers.forEach { (k, v) ->
                if (!k.equals("Referer", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                    addOption("--add-header", "$k:$v")
                }
            }

            if (isExtractorTask) {
                // Social / HLS / Extractor mode: native yt-dlp extraction + ffmpeg muxing
                // NEVER pass aria2c here, or aria2c will download the 0.6MB HTML webpage!
                addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                addOption("--merge-output-format", "mp4")
            } else {
                // Direct CDN file mode (NKiri, DramaKey, Pluto, etc.): use aria2c with flags
                addOption("--downloader", "libaria2c.so")
                val conns = if (isConnectionSensitive(sourceUrl)) 1 else parallelSockets.coerceIn(1, 16)
                val aria2Args = buildString {
                    append("aria2c:-x $conns -s $conns --min-split-size=1M --continue=true")
                    if (origin.isNotBlank()) append(" --header=\"Origin: $origin\"")
                    append(" --header=\"Accept: video/mp4,video/x-matroska,video/*,*/*\"")
                    append(" --check-certificate=false")
                    append(" --summary-interval=1")
                }
                addOption("--downloader-args", aria2Args)
            }
        }

        YoutubeDL.getInstance().execute(request, taskId) { progress, _, _ ->
            if (progress >= 0f) onProgress(progress)
        }

        // yt-dlp wrote "$stem.<ext>"; normalise to targetFilePath (.mp4)
        if (!target.exists()) {
            val produced = target.parentFile
                ?.listFiles { f ->
                    f.name.startsWith(File(stem).name + ".") &&
                    !f.name.endsWith(".aria2") &&
                    !f.name.endsWith(".part") &&
                    !f.name.endsWith(".ytdl")
                }
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

    /** Synthetic totalBytes for the progress scale. */
    fun scaleTotal(): Long = PROGRESS_SCALE

    /** Map a 0-100 percent onto the synthetic byte scale for DownloadTask. */
    fun scaleDownloaded(percent: Float): Long =
        (percent.coerceIn(0f, 100f) / 100f * PROGRESS_SCALE).toLong()

    /** Social media / video platforms that require yt-dlp extractors. */
    fun isSocial(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("instagram.com") || u.contains("youtube.com") ||
                u.contains("youtu.be") || u.contains("tiktok.com") ||
                u.contains("twitter.com") || u.contains("x.com") ||
                u.contains("facebook.com") || u.contains("fb.watch") ||
                u.contains("threads.net") || u.contains("reddit.com")
    }

    /** HLS streams are muxed by yt-dlp itself, not handed to aria2c. */
    fun isHls(url: String): Boolean {
        val u = url.lowercase()
        return u.contains(".m3u8") || u.contains("/hls/") || u.contains("manifest")
    }

    /**
     * File-locker CDNs that throttle or drop multi-connection requests.
     */
    fun isConnectionSensitive(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("kissorgrab") || u.contains("downloadwella") ||
                u.contains("wella") || u.contains("streamwish") ||
                u.contains("filelions") || u.contains("vidhide")
    }
}
