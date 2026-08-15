package com.anonrode.downloader.data.models

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
data class ShowItem(
    val site: String = "Source",
    val url: String = "",
    val title: String = "",
    val poster: String? = null,
    val episodeCount: Int = 0
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            if (url.isBlank()) return "Untitled Drama"
            val slug = url.trimEnd('/').substringAfterLast('/')
            val cleaned = slug.replace(Regex("[-_]+"), " ")
                .replace(Regex("(?i)\\b(korean|drama|season|\\d+p|movie|download|complete|hd)\\b"), "")
                .trim()
            return cleaned.split(" ").filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                .ifEmpty { "Drama Series" }
        }

    val siteColor: Color
        get() = when (site.lowercase()) {
            "nkiri" -> Color(0xFF10B981) // Emerald
            "dramakey" -> Color(0xFF38BDF8) // Cyan
            "plutomovies", "pluto" -> Color(0xFFF59E0B) // Amber
            "9jarocks" -> Color(0xFFE11D48) // Rose
            "viki" -> Color(0xFFA855F7) // Purple
            else -> Color(0xFF00E5FF)
        }
}

@Serializable
data class EpisodeItem(
    val episode: Int = 1,
    val title: String = "",
    val rawLabel: String = "",
    val url: String = "",
    val kind: String = "resolve",
    var isSelected: Boolean = false
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            val epNum = String.format("%02d", episode)
            return "Episode $epNum"
        }
}

@Serializable
data class DownloadRecipe(
    val status: String = "success",
    val url: String = "",
    val backend: String = "aria2c",
    val filename: String = "",
    val headers: Map<String, String> = emptyMap(),
    val source_url: String? = null
)

@Serializable
enum class TaskStatus {
    QUEUED, RESOLVING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

@Serializable
@Serializable
data class DownloadTask(
    val id: String,
    val showName: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val originalUrl: String,
    val resolvedUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val targetFilePath: String,
    val tempFilePath: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: TaskStatus = TaskStatus.QUEUED,
    @Transient val speedBytesPerSec: Double = 0.0,
    val errorMessage: String? = null,
    val createdAt: Long = 0L
) {
    val progressPercent: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val fileName: String
        get() = File(targetFilePath).name

    val formattedSpeed: String
        get() = when {
            speedBytesPerSec <= 0.0 || status != TaskStatus.DOWNLOADING -> ""
            speedBytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", speedBytesPerSec / (1024 * 1024))
            else -> String.format("%.0f KB/s", speedBytesPerSec / 1024)
        }

    val formattedSize: String
        get() {
            val currMb = downloadedBytes.toDouble() / (1024 * 1024)
            val totalMb = totalBytes.toDouble() / (1024 * 1024)
            return if (totalBytes > 0L) String.format("%.1f / %.1f MB", currMb, totalMb) else String.format("%.1f MB", currMb)
        }
}

@Serializable
data class SubtitleItem(
    val id: String = "",
    val name: String = "",
    val source: String = "opensubtitles",
    val type: String = "pack",
    val lang: String = "en",
    val episodes_count: Int = 0,
    val rank_score: Int = 0,
    val preview: List<String> = emptyMap<String, String>().keys.toList(),
    val download_url: String = ""
)
