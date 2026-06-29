package tf.monochrome.android.data.analysis

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.first
import tf.monochrome.android.data.api.QobuzIdRegistry
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.UnifiedTrack
import tf.monochrome.android.domain.usecase.toUnifiedTrackAuto
import tf.monochrome.android.player.ResolvedMedia
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget analysis entry point for tracks that just started playback.
 *
 * The bulk background worker still catches up library/history entries, but this
 * path makes a freshly streamed song usable as a radio seed as soon as its
 * stream URI is resolved. It deliberately skips DASH manifests and encrypted
 * collection links because MediaExtractor cannot decode those URI shapes.
 */
@Singleton
class AudioFeatureAnalysisCoordinator @Inject constructor(
    private val repository: AudioFeatureRepository,
    private val analyzer: AudioFeatureAnalyzer,
    private val preferences: PreferencesManager,
    private val qobuzIdRegistry: QobuzIdRegistry,
) {
    private val inFlight = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @OptIn(UnstableApi::class)
    suspend fun analyzeIfNeeded(track: UnifiedTrack, resolved: ResolvedMedia) {
        analyzeIfNeeded(
            track = track,
            mediaItem = resolved.mediaItem,
            isDash = resolved.isDash,
            isEncrypted = resolved.isEncrypted,
        )
    }

    @OptIn(UnstableApi::class)
    suspend fun analyzeIfNeeded(
        track: Track,
        mediaItem: MediaItem?,
        isDash: Boolean = false,
        isEncrypted: Boolean = false,
    ) {
        analyzeIfNeeded(
            track = track.toUnifiedTrackAuto(qobuzIdRegistry),
            mediaItem = mediaItem,
            isDash = isDash,
            isEncrypted = isEncrypted,
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun analyzeIfNeeded(
        track: UnifiedTrack,
        mediaItem: MediaItem?,
        isDash: Boolean,
        isEncrypted: Boolean,
    ) {
        if (!preferences.analyzeAudioFeatures.first()) return
        if (isDash || isEncrypted) return

        val key = repository.normKeyOf(track)
        if (key.length <= 1 || !key.contains('|')) return
        if (repository.featuresForKey(key) != null) return
        if (!inFlight.add(key)) return

        try {
            val uri = mediaItem?.localConfiguration?.uri ?: return
            val analyzed = repository.analyzedCount()
            val currentTarget = preferences.audioAnalysisTarget.first()
            if (currentTarget <= analyzed) preferences.setAudioAnalysisTarget(analyzed + 1)

            val features = analyzer.analyze(uri) ?: return
            repository.save(track, features, System.currentTimeMillis())
        } finally {
            inFlight.remove(key)
        }
    }
}
