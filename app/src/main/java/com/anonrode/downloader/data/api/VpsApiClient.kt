package com.anonrode.downloader.data.api

import com.anonrode.downloader.data.models.EpisodeItem
import com.anonrode.downloader.data.models.DownloadRecipe
import com.anonrode.downloader.data.models.ShowItem
import com.anonrode.downloader.data.models.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import com.anonrode.downloader.data.net.HttpClient
import java.net.URLEncoder

class VpsApiClient(
    var serverUrl: String = "http://68.155.146.145",
    var apiKey: String = "",
    val clientVersion: String = "2.0.0"
) {
    val defaultUserAgent = HttpClient.DEFAULT_UA

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient: OkHttpClient by lazy { HttpClient.shared }

    private fun buildHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
            .add("x-client-version", clientVersion)
            .add("ngrok-skip-browser-warning", "1")
            .add("User-Agent", defaultUserAgent)
        if (apiKey.isNotBlank()) {
            builder.add("x-api-key", apiKey.trim())
        }
        return builder.build()
    }

    // Percent-encode a query value. Without this a title with a space
    // ("reply 1988") or a resolve URL carrying its own ?/&/= silently corrupts
    // the request line -- the #1 reason search and resolve "randomly" failed.
    // URLEncoder emits '+' for spaces (form encoding); servers accept it in the
    // query string, but normalize to %20 to be safe with stricter parsers.
    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun isDirectUrl(input: String): Boolean {
        val clean = input.trim().lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://") ||
                clean.contains("instagram.com") || clean.contains("youtube.com") ||
                clean.contains("youtu.be") || clean.contains("tiktok.com") ||
                clean.contains("twitter.com") || clean.contains("x.com")
    }

    suspend fun checkHealth(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/health")
                .headers(buildHeaders())
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { res ->
                val elapsed = System.currentTimeMillis() - start
                Pair(res.isSuccessful || res.code == 200, elapsed)
            }
        } catch (e: Exception) {
            Pair(false, 0L)
        }
    }

    suspend fun searchShows(query: String, siteFilter: String? = null): List<ShowItem> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isEmpty()) return@withContext emptyList()
        if (apiKey.isBlank()) throw Exception("API Key required. Please enter your key in Settings.")

        val urlBuilder = StringBuilder("${serverUrl.trimEnd('/')}/api/v1/search?q=${enc(cleanQ)}&key=${enc(apiKey.trim())}")
        if (!siteFilter.isNullOrBlank() && siteFilter != "all") {
            urlBuilder.append("&sites=${enc(siteFilter)}")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .headers(buildHeaders())
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw Exception("Invalid API Key. Please check your key in Settings.")
            }
            if (!response.isSuccessful) {
                throw Exception("Search failed (HTTP ${response.code})")
            }
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val resultsArray = root["results"]?.jsonArray ?: return@withContext emptyList()

            resultsArray.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    val rawUrl = obj["url"]?.jsonPrimitive?.content ?: ""
                    val site = obj["site"]?.jsonPrimitive?.content ?: "Source"
                    val title = obj["title"]?.jsonPrimitive?.content ?: ""
                    val poster = obj["poster"]?.jsonPrimitive?.content
                        ?: obj["image"]?.jsonPrimitive?.content
                        ?: obj["thumbnail"]?.jsonPrimitive?.content
                    val epCount = obj["episodes_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                    ShowItem(
                        site = site,
                        url = rawUrl,
                        title = title,
                        poster = if (!poster.isNullOrBlank()) poster else null,
                        episodeCount = epCount
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun listEpisodes(showUrl: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw Exception("API Key required. Please enter your key in Settings.")
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/episodes?url=${enc(showUrl)}&key=${enc(apiKey.trim())}")
            .headers(buildHeaders())
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw Exception("Invalid API Key. Please check your key in Settings.")
            }
            if (!response.isSuccessful) throw Exception("Episode list failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val episodesArray = root["episodes"]?.jsonArray ?: return@withContext emptyList()

            val epRegex = Regex("(?i)(?:[sS]\\d+[eE]|[eE]|episode[\\s._-]*)(\\d+)")

            episodesArray.mapIndexedNotNull { index, elem ->
                try {
                    val obj = elem.jsonObject
                    val rawLabel = obj["label"]?.jsonPrimitive?.content ?: "Episode ${index + 1}"
                    val epUrl = obj["url"]?.jsonPrimitive?.content ?: ""
                    val kind = obj["kind"]?.jsonPrimitive?.content ?: "resolve"

                    val match = epRegex.find(rawLabel)
                    val parsedEpNum = match?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                    val cleanTitle = "Episode ${String.format("%02d", parsedEpNum)}"

                    EpisodeItem(
                        episode = parsedEpNum,
                        title = cleanTitle,
                        rawLabel = rawLabel,
                        url = epUrl,
                        kind = kind
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun resolveEpisode(episodeUrl: String, quality: String = "720p"): DownloadRecipe = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw Exception("API Key required. Please enter your key in Settings.")
        val cleanQuality = quality.trim().lowercase()
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/v1/resolve?url=${enc(episodeUrl)}&quality=${enc(cleanQuality)}&kind=resolve&key=${enc(apiKey.trim())}")
            .headers(buildHeaders())
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw Exception("Invalid API Key. Please check your key in Settings.")
            }
            if (!response.isSuccessful) throw Exception("Link resolve failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: throw Exception("Empty resolve response")
            val root = json.parseToJsonElement(bodyStr).jsonObject

            val resUrl = root["url"]?.jsonPrimitive?.content ?: throw Exception("Missing download URL in recipe")
            val backend = root["backend"]?.jsonPrimitive?.content ?: "aria2c"
            val filename = root["filename"]?.jsonPrimitive?.content ?: "video.mp4"
            val sourceUrl = root["source_url"]?.jsonPrimitive?.content

            val headersMap = mutableMapOf<String, String>()
            root["headers"]?.jsonObject?.forEach { (k, v) ->
                headersMap[k] = v.jsonPrimitive.content
            }

            DownloadRecipe(
                status = "success",
                url = resUrl,
                backend = backend,
                filename = filename,
                headers = headersMap,
                source_url = sourceUrl
            )
        }
    }

    suspend fun searchSubtitles(query: String): List<SubtitleItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/api/v1/subtitles?q=${enc(query)}")
                .headers(buildHeaders())
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val results = root["results"]?.jsonArray ?: return@withContext emptyList()

                results.mapNotNull { elem ->
                    try {
                        val obj = elem.jsonObject
                        SubtitleItem(
                            id = obj["id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            source = obj["source"]?.jsonPrimitive?.content ?: "viki",
                            type = obj["type"]?.jsonPrimitive?.content ?: "pack",
                            lang = obj["lang"]?.jsonPrimitive?.content ?: "en",
                            episodes_count = obj["episodes_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            download_url = obj["download_url"]?.jsonPrimitive?.content ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
