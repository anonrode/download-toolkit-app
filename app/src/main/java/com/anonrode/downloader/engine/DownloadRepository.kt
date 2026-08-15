package com.anonrode.downloader.engine

import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The single source of truth for the download task list and its durability.
 *
 * Split out of Aria2Engine so ownership is clear: the repository holds the state
 * (the StateFlow), the atomic-copy mutation mechanism, and JSON persistence,
 * while the engine keeps only download orchestration -- what to fetch, how many
 * at once, byte pumping. Nothing outside here touches _tasks.
 *
 * Persistence is caller-driven on purpose. Structural changes (add/remove)
 * persist inline, but [update] does NOT: it is called on every ~500ms progress
 * tick, and writing JSON that often would thrash the disk. The engine calls
 * [persist] at lifecycle points (pause, terminal state) instead.
 */
class DownloadRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var stateFile: File? = null

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /**
     * Wire a persistence file and reload tasks from a previous run. Interrupted
     * downloads (DOWNLOADING/RESOLVING when the app died) are demoted to PAUSED
     * so they can be resumed rather than shown frozen with no live coroutine.
     */
    fun initPersistence(dir: File) {
        if (stateFile != null) return
        val f = File(dir, "download_tasks.json")
        stateFile = f
        try {
            if (f.exists()) {
                _tasks.value = json.decodeFromString<List<DownloadTask>>(f.readText())
                    .map {
                        if (it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING) {
                            it.copy(status = TaskStatus.PAUSED, speedBytesPerSec = 0.0)
                        } else it
                    }
            }
        } catch (_: Exception) {
            // Corrupt state file: start clean rather than crash on launch.
            _tasks.value = emptyList()
        }
    }

    /** Serialize the current list. No-op until a file is wired (e.g. in tests). */
    fun persist() {
        val f = stateFile ?: return
        scope.launch {
            try {
                f.writeText(json.encodeToString(_tasks.value))
            } catch (_: Exception) {}
        }
    }

    /** Immutable snapshot of the current list. */
    fun snapshot(): List<DownloadTask> = _tasks.value

    /** A task by id, or null if gone. */
    fun find(taskId: String): DownloadTask? = _tasks.value.find { it.id == taskId }

    /** Prepend a new task (newest first) and persist -- a structural change. */
    fun addFirst(task: DownloadTask) {
        _tasks.update { listOf(task) + it }
        persist()
    }

    /** Remove a task and persist -- a structural change. */
    fun remove(taskId: String) {
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        persist()
    }

    /**
     * Atomically replace a task with a modified copy. Does NOT persist -- see
     * the class note; the engine persists explicitly at lifecycle points. The
     * copy is a new object so Compose item equality actually sees the change,
     * which is what keeps the progress UI live.
     */
    fun update(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
