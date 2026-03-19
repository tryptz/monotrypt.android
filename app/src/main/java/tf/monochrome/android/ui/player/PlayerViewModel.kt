package tf.monochrome.android.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tf.monochrome.android.data.downloads.DownloadManager
import tf.monochrome.android.data.repository.LibraryRepository
import tf.monochrome.android.data.repository.MusicRepository
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.Lyrics
import tf.monochrome.android.domain.model.NowPlayingViewMode
import tf.monochrome.android.domain.model.RepeatMode
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.player.PlaybackService
import tf.monochrome.android.player.QueueManager
import tf.monochrome.android.player.StreamResolver
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val streamResolver: StreamResolver,
    private val repository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager,
    private val preferences: PreferencesManager
) : ViewModel() {

    // --- State from QueueManager (runs in-process, no IPC needed) ---
    val currentTrack: StateFlow<Track?> = queueManager.currentTrack
    val queue: StateFlow<List<Track>> = queueManager.queue
    val currentIndex: StateFlow<Int> = queueManager.currentIndex
    val shuffleEnabled: StateFlow<Boolean> = queueManager.shuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = queueManager.repeatMode

    // --- Player state (observed from MediaController) ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    val progress: StateFlow<Float>
        get() = MutableStateFlow(
            if (_durationMs.value > 0) _positionMs.value.toFloat() / _durationMs.value.toFloat()
            else 0f
        ).asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    // --- Active Track Meta (Lyrics / Liked) ---
    private val _isCurrentTrackLiked = MutableStateFlow(false)
    val isCurrentTrackLiked: StateFlow<Boolean> = _isCurrentTrackLiked.asStateFlow()

    private val _currentLyrics = MutableStateFlow<Lyrics?>(null)
    val currentLyrics: StateFlow<Lyrics?> = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    // --- Parity Settings ---
    val visualizerSensitivity: StateFlow<Int> = preferences.visualizerSensitivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)
    val visualizerBrightness: StateFlow<Int> = preferences.visualizerBrightness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80)
    val nowPlayingViewMode: StateFlow<NowPlayingViewMode> = preferences.nowPlayingViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowPlayingViewMode.COVER_ART)
    val romajiLyrics: StateFlow<Boolean> = preferences.romajiLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Global Favorites State ---
    val favoriteTrackIds: StateFlow<Set<Long>> = libraryRepository.getFavoriteTracks()
        .map { tracks -> tracks.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val playlists: StateFlow<List<tf.monochrome.android.data.db.entity.UserPlaylistEntity>> = libraryRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null


    init {
        connectToService()
        startPositionPolling()
        observeCurrentTrackMeta()
    }

    private fun observeCurrentTrackMeta() {
        viewModelScope.launch {
            // Re-fetch when track OR romaji setting changes
            kotlinx.coroutines.flow.combine(currentTrack, romajiLyrics) { track, _ -> track }
                .collectLatest { track ->
                    if (track != null) {
                        // Start fetching lyrics
                        _isLyricsLoading.value = true
                        _currentLyrics.value = null
                        
                        launch {
                            _currentLyrics.value = repository.getLyrics(track.id).getOrNull()
                            _isLyricsLoading.value = false
                        }

                        // Observe liked status
                        libraryRepository.isFavoriteTrack(track.id).collectLatest { isLiked ->
                            _isCurrentTrackLiked.value = isLiked
                        }
                    } else {
                        _currentLyrics.value = null
                        _isLyricsLoading.value = false
                        _isCurrentTrackLiked.value = false
                    }
                }
        }
    }

    fun toggleLikeCurrentTrack() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            libraryRepository.toggleFavoriteTrack(track)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            libraryRepository.toggleFavoriteTrack(track)
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().also { future ->
            future.addListener({
                try {
                    mediaController = future.get()
                    setupPlayerListener()
                    syncState()
                } catch (_: Exception) {
                    // Service not yet available — will retry or user will trigger playback
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                syncState()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                syncState()
            }
        })
    }

    private fun syncState() {
        mediaController?.let { mc ->
            _isPlaying.value = mc.isPlaying
            _durationMs.value = mc.duration.coerceAtLeast(0)
            _positionMs.value = mc.currentPosition.coerceAtLeast(0)
        }
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { mc ->
                    if (mc.isPlaying) {
                        _positionMs.value = mc.currentPosition.coerceAtLeast(0)
                        _durationMs.value = mc.duration.coerceAtLeast(0)
                    }
                }
                delay(250) // 4 updates/sec for smooth progress
            }
        }
    }

    // --- Actions ---

    /** Play a single track (sets a 1-item queue). */
    fun playTrack(track: Track) {
        queueManager.setQueue(listOf(track), 0)
        resolveAndPlay()
    }

    /** Play a track within a list (sets the full list as queue). */
    fun playTrack(track: Track, trackList: List<Track>) {
        queueManager.playTrackInQueue(track, trackList)
        resolveAndPlay()
    }

    /** Play all tracks starting from index 0. */
    fun playAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        queueManager.setQueue(tracks, 0)
        resolveAndPlay()
    }

    /** Shuffle-play all tracks. */
    fun shufflePlay(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        queueManager.setQueue(tracks, 0)
        queueManager.toggleShuffle()
        resolveAndPlay()
    }

    /** Add tracks to end of current queue. */
    fun addToQueue(tracks: List<Track>) {
        queueManager.addToQueue(tracks)
    }

    /** Insert a track right after the current one. */
    fun playNext(track: Track) {
        queueManager.addNextInQueue(track)
    }

    fun togglePlayPause() {
        mediaController?.let { mc ->
            if (mc.isPlaying) mc.pause() else mc.play()
        }
    }

    fun skipToNext() {
        val next = queueManager.next()
        if (next != null) {
            resolveAndPlay()
        } else {
            mediaController?.stop()
        }
    }

    fun skipToPrevious() {
        val position = mediaController?.currentPosition ?: 0
        if (position > 3000) {
            mediaController?.seekTo(0)
            return
        }
        val prev = queueManager.previous()
        if (prev != null) {
            resolveAndPlay()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun seekToFraction(fraction: Float) {
        val pos = (fraction * _durationMs.value).toLong()
        seekTo(pos)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    fun cycleNowPlayingViewMode() {
        viewModelScope.launch {
            val current = nowPlayingViewMode.value
            val next = when (current) {
                NowPlayingViewMode.COVER_ART -> NowPlayingViewMode.VISUALIZER
                NowPlayingViewMode.VISUALIZER -> NowPlayingViewMode.LYRICS
                NowPlayingViewMode.LYRICS -> NowPlayingViewMode.QUEUE
                NowPlayingViewMode.QUEUE -> NowPlayingViewMode.COVER_ART
            }
            preferences.setNowPlayingViewMode(next)
        }
    }

    fun skipToQueueIndex(index: Int) {
        queueManager.skipToIndex(index)
        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        val track = queueManager.currentTrack.value ?: return
        viewModelScope.launch {
            try {
                val (mediaItem, trackStream) = streamResolver.resolveMediaItem(track)

                mediaController?.let { mc ->
                    if (trackStream != null && trackStream.streamUrl.isNotBlank()) {
                        mc.setMediaItem(mediaItem)
                    } else {
                        mc.setMediaItem(mediaItem)
                    }
                    mc.prepare()
                    mc.play()
                }
            } catch (_: Exception) {
                // On error skip to next
                skipToNext()
            }
        }
    }

    // --- Downloads ---
    fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
        android.widget.Toast.makeText(
            context,
            "Downloading: ${track.title}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            libraryRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            libraryRepository.createPlaylist(name, description)
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }

    // --- Formatting helpers ---
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
