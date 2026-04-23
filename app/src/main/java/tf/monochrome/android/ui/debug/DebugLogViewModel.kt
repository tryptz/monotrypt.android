package tf.monochrome.android.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import tf.monochrome.android.debug.DebugLogBuffer
import tf.monochrome.android.debug.DebugLogEntry
import javax.inject.Inject

@HiltViewModel
class DebugLogViewModel @Inject constructor(
    private val buffer: DebugLogBuffer,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _levelFilter = MutableStateFlow(LevelFilter.ALL)
    val levelFilter: StateFlow<LevelFilter> = _levelFilter.asStateFlow()

    /**
     * Snapshot emissions, throttled to 4 Hz so a burst of logcat lines doesn't
     * trigger 1 000 recompositions per second on the viewer screen.
     */
    private val snapshots: StateFlow<List<DebugLogEntry>> =
        buffer.updates
            .sample(SAMPLE_MS)
            .map { buffer.snapshot() }
            .onStart { emit(buffer.snapshot()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<DebugLogEntry>> =
        combine(snapshots, _query, _levelFilter) { list, q, filter ->
            val byLevel = if (filter == LevelFilter.ALL) list else list.filter { it.level in filter.levels }
            if (q.isBlank()) byLevel
            else {
                val needle = q.trim()
                byLevel.filter { it.tag.contains(needle, ignoreCase = true) || it.message.contains(needle, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSize: StateFlow<Int> =
        snapshots.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(value: String) { _query.value = value }
    fun setLevelFilter(value: LevelFilter) { _levelFilter.value = value }
    fun clear() = buffer.clear()
    fun exportText(): String = buffer.dumpAsText()

    private companion object {
        const val SAMPLE_MS = 250L
    }
}

enum class LevelFilter(val label: String, val levels: Set<Char>) {
    ALL("All", setOf('V', 'D', 'I', 'W', 'E', 'F')),
    INFO_PLUS("Info+", setOf('I', 'W', 'E', 'F')),
    WARN_PLUS("Warn+", setOf('W', 'E', 'F')),
    ERROR_ONLY("Errors", setOf('E', 'F')),
}
