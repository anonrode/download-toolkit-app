package com.anonrode.downloader.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anonrode.downloader.data.api.VpsApiClient
import com.anonrode.downloader.data.models.EpisodeItem
import com.anonrode.downloader.data.models.ShowItem
import com.anonrode.downloader.data.models.SubtitleItem
import com.anonrode.downloader.engine.Aria2Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val query: String = "",
    val searchResults: List<ShowItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedFilter: String = "all",
    val activeShowForDrawer: ShowItem? = null,
    val drawerEpisodes: List<EpisodeItem> = emptyList(),
    val isEpisodesLoading: Boolean = false,
    val episodesError: String? = null,
    val freeStorageGb: Double = 45.2,
    val totalStorageGb: Double = 128.0,
    val isVpsOnline: Boolean = true,
    val vpsLatencyMs: Long = 42L,
    val isKeyConfigured: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val apiClient = VpsApiClient()
    val engine = Aria2Engine.instance

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("anon_prefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
        // Wire the shared apiClient into the engine so resolves use the user's key
        engine.apiClient = apiClient
        // Restore any downloads from a previous run (app-private storage).
        engine.initPersistence(application.filesDir)
        refreshStorage()
        checkServerPing()
    }

    private fun loadSettings() {
        apiClient.serverUrl = prefs.getString("server_url", "http://68.155.146.145") ?: "http://68.155.146.145"
        apiClient.apiKey = prefs.getString("api_key", "") ?: ""
        _uiState.value = _uiState.value.copy(isKeyConfigured = apiClient.apiKey.isNotBlank())

        engine.maxConcurrentDownloads = prefs.getInt("max_concurrent", 2)
        engine.parallelSocketsPerFile = prefs.getInt("parallel_sockets", 16)
        engine.minSplitSizeMb = prefs.getInt("min_split_mb", 1)
        engine.diskCacheMb = prefs.getInt("disk_cache_mb", 32)
        engine.fileAllocation = prefs.getString("file_allocation", "trunc") ?: "trunc"
        engine.defaultQuality = prefs.getString("default_quality", "720p") ?: "720p"
        engine.autoOrganizeByShow = prefs.getBoolean("auto_organize", true)
        engine.instantSocialDownload = prefs.getBoolean("instant_social", false)
    }

    fun saveFullSettings(
        url: String,
        key: String,
        concurrent: Int,
        sockets: Int,
        minSplit: Int,
        diskCache: Int,
        fileAlloc: String,
        quality: String,
        autoOrg: Boolean,
        instantSoc: Boolean
    ) {
        apiClient.serverUrl = url.trimEnd('/')
        apiClient.apiKey = key.trim()
        engine.maxConcurrentDownloads = concurrent
        engine.parallelSocketsPerFile = sockets
        engine.minSplitSizeMb = minSplit
        engine.diskCacheMb = diskCache
        engine.fileAllocation = fileAlloc
        engine.defaultQuality = quality
        engine.autoOrganizeByShow = autoOrg
        engine.instantSocialDownload = instantSoc

        // Keep engine's apiClient reference in sync
        engine.apiClient = apiClient

        _uiState.value = _uiState.value.copy(isKeyConfigured = apiClient.apiKey.isNotBlank())

        prefs.edit()
            .putString("server_url", apiClient.serverUrl)
            .putString("api_key", apiClient.apiKey)
            .putInt("max_concurrent", concurrent)
            .putInt("parallel_sockets", sockets)
            .putInt("min_split_mb", minSplit)
            .putInt("disk_cache_mb", diskCache)
            .putString("file_allocation", fileAlloc)
            .putString("default_quality", quality)
            .putBoolean("auto_organize", autoOrg)
            .putBoolean("instant_social", instantSoc)
            .apply()

        checkServerPing()
    }

    fun refreshStorage() {
        val (free, total) = engine.getStorageStats()
        _uiState.value = _uiState.value.copy(freeStorageGb = free, totalStorageGb = total)
    }

    fun checkServerPing() {
        viewModelScope.launch {
            val (online, latency) = apiClient.checkHealth()
            _uiState.value = _uiState.value.copy(isVpsOnline = online, vpsLatencyMs = latency)
        }
    }

    fun onQueryChanged(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
    }

    fun onFilterSelected(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        if (_uiState.value.query.isNotBlank()) {
            performSearch()
        }
    }

    fun isSuspicious(q: String): Boolean {
        val clean = q.trim().lowercase()
        if (clean.length < 3) return true
        if (!clean.contains(Regex("[aeiouy]"))) return true
        if (clean.contains(Regex("[^aeiouy\\s]{5,}"))) return true
        return false
    }

    fun performSearch() {
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) return

        if (apiClient.apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchError = "API Key not configured. Please enter your API Key in Settings to start downloading."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
            try {
                val results = apiClient.searchShows(q, _uiState.value.selectedFilter)
                _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(searchError = e.message ?: "Search failed", isSearching = false)
            }
        }
    }

    fun openEpisodeDrawer(show: ShowItem) {
        _uiState.value = _uiState.value.copy(
            activeShowForDrawer = show,
            drawerEpisodes = emptyList(),
            isEpisodesLoading = true,
            episodesError = null
        )
        viewModelScope.launch {
            try {
                val eps = apiClient.listEpisodes(show.url)
                _uiState.value = _uiState.value.copy(drawerEpisodes = eps, isEpisodesLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(episodesError = e.message ?: "Failed to load episodes", isEpisodesLoading = false)
            }
        }
    }

    fun closeEpisodeDrawer() {
        _uiState.value = _uiState.value.copy(activeShowForDrawer = null)
    }

    fun toggleEpisodeSelection(ep: EpisodeItem) {
        val updated = _uiState.value.drawerEpisodes.map {
            if (it.episode == ep.episode) it.copy(isSelected = !it.isSelected) else it
        }
        _uiState.value = _uiState.value.copy(drawerEpisodes = updated)
    }

    fun applyBatchSelection(filter: String) {
        val eps = _uiState.value.drawerEpisodes
        val updated = eps.map { ep ->
            val select = when (filter) {
                "all" -> true
                "none" -> false
                "1-8" -> ep.episode in 1..8
                "9-16" -> ep.episode in 9..16
                "17-24" -> ep.episode in 17..24
                else -> false
            }
            ep.copy(isSelected = select)
        }
        _uiState.value = _uiState.value.copy(drawerEpisodes = updated)
    }

    fun downloadSingleEpisode(show: ShowItem, ep: EpisodeItem) {
        engine.enqueue(
            showName = show.displayTitle,
            episodeNumber = ep.episode,
            episodeTitle = ep.displayTitle,
            originalUrl = ep.url
        )
    }

    fun downloadSelectedEpisodes(show: ShowItem) {
        val selected = _uiState.value.drawerEpisodes.filter { it.isSelected }
        selected.forEach { ep ->
            engine.enqueue(
                showName = show.displayTitle,
                episodeNumber = ep.episode,
                episodeTitle = ep.displayTitle,
                originalUrl = ep.url
            )
        }
        closeEpisodeDrawer()
    }
}
