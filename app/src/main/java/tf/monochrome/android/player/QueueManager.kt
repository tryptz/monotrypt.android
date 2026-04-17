package tf.monochrome.android.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.RepeatMode
import tf.monochrome.android.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val preferences: PreferencesManager
) {
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var originalQueue: List<Track> = emptyList()

    val currentQueue: List<Track> get() = _queue.value
    val currentQueueIndex: Int get() = _currentIndex.value

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        originalQueue = tracks
        _currentIndex.value = startIndex
        _shuffleEnabled.value = false
        updateCurrentTrack()
    }

    fun playTrackInQueue(track: Track, tracks: List<Track>) {
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setQueue(tracks, index)
    }

    fun addToQueue(tracks: List<Track>) {
        _queue.value = _queue.value + tracks
        if (_shuffleEnabled.value) {
            originalQueue = originalQueue + tracks
        }
    }

    fun addNextInQueue(track: Track) {
        val currentList = _queue.value.toMutableList()
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, track)
        _queue.value = currentList
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= _queue.value.size) return
        val currentList = _queue.value.toMutableList()
        currentList.removeAt(index)
        _queue.value = currentList

        if (index < _currentIndex.value) {
            _currentIndex.value = _currentIndex.value - 1
        } else if (index == _currentIndex.value) {
            // Current track removed, stay at same index (next track slides in)
            _currentIndex.value = _currentIndex.value.coerceAtMost(currentList.size - 1)
        }
        updateCurrentTrack()
    }

    fun skipToIndex(index: Int) {
        if (index < 0 || index >= _queue.value.size) return
        _currentIndex.value = index
        updateCurrentTrack()
    }

    fun next(): Track? {
        val queue = _queue.value
        if (queue.isEmpty()) return null

        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Replay same track
                updateCurrentTrack()
                _currentTrack.value
            }
            RepeatMode.ALL -> {
                val nextIndex = (_currentIndex.value + 1) % queue.size
                _currentIndex.value = nextIndex
                updateCurrentTrack()
                _currentTrack.value
            }
            RepeatMode.OFF -> {
                val nextIndex = _currentIndex.value + 1
                if (nextIndex >= queue.size) {
                    null // End of queue
                } else {
                    _currentIndex.value = nextIndex
                    updateCurrentTrack()
                    _currentTrack.value
                }
            }
        }
    }

    fun previous(): Track? {
        val queue = _queue.value
        if (queue.isEmpty()) return null

        val prevIndex = if (_currentIndex.value <= 0) {
            if (_repeatMode.value == RepeatMode.ALL) queue.size - 1 else 0
        } else {
            _currentIndex.value - 1
        }

        _currentIndex.value = prevIndex
        updateCurrentTrack()
        return _currentTrack.value
    }

    fun toggleShuffle() {
        if (_shuffleEnabled.value) {
            // Disable shuffle - restore original queue
            val currentTrack = _currentTrack.value
            _queue.value = originalQueue
            _currentIndex.value = if (currentTrack != null) {
                originalQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
            } else 0
            _shuffleEnabled.value = false
        } else {
            // Enable shuffle - Fisher-Yates preserving current track at index 0
            originalQueue = _queue.value
            val currentTrack = _currentTrack.value
            val remaining = _queue.value.toMutableList()

            if (currentTrack != null) {
                remaining.removeAll { it.id == currentTrack.id }
            }
            remaining.shuffle()

            _queue.value = if (currentTrack != null) {
                listOf(currentTrack) + remaining
            } else {
                remaining
            }
            _currentIndex.value = 0
            _shuffleEnabled.value = true
        }
        updateCurrentTrack()
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun clearQueue() {
        _queue.value = emptyList()
        originalQueue = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
    }

    fun hasNext(): Boolean {
        return when (_repeatMode.value) {
            RepeatMode.ONE, RepeatMode.ALL -> _queue.value.isNotEmpty()
            RepeatMode.OFF -> _currentIndex.value < _queue.value.size - 1
        }
    }

    fun hasPrevious(): Boolean {
        return when (_repeatMode.value) {
            RepeatMode.ALL -> _queue.value.isNotEmpty()
            else -> _currentIndex.value > 0
        }
    }

    private fun updateCurrentTrack() {
        val index = _currentIndex.value
        val queue = _queue.value
        _currentTrack.value = if (index in queue.indices) queue[index] else null
    }
}
