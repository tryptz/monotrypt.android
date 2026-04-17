package tf.monochrome.android.player

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import tf.monochrome.android.data.auth.SupabaseAuthManager
import tf.monochrome.android.data.device.DeviceRegistry
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

private const val TAG = "PlaySession"
private val IDLE_CUTOFF_MS = 30.minutes.inWholeMilliseconds

/**
 * Groups consecutive plays into `play_sessions` rows. A new session starts on:
 *   - first play of the app lifecycle
 *   - a play that arrives > 30 min after the previous one (Tidal-like)
 *   - sign-in/user change
 *
 * Returns the session UUID for the just-observed play event so the cloud
 * push can foreign-key-attach it. Best-effort — errors never block playback.
 */
@Singleton
class PlaySessionManager @Inject constructor(
    private val authManager: SupabaseAuthManager,
    private val deviceRegistry: DeviceRegistry,
) {
    @Serializable
    private data class SbPlaySessionInsert(
        val user_id: String,
        val device_id: String? = null,
    )

    @Serializable
    private data class SbPlaySessionRow(
        val id: String,
    )

    private val mutex = Mutex()
    private var currentSessionId: String? = null
    private var currentUserId: String? = null
    private var lastPlayAtMs: Long = 0L
    private var trackCount: Int = 0

    /**
     * Resolve the session UUID this play belongs to. Call once per scrobble,
     * before pushing the play event.
     */
    suspend fun sessionFor(playedAtMs: Long): String? = mutex.withLock {
        val uid = authManager.userProfile.value?.id ?: run {
            currentSessionId = null
            return null
        }

        val idleGap = playedAtMs - lastPlayAtMs
        val userChanged = uid != currentUserId
        val idleTooLong = lastPlayAtMs > 0 && idleGap > IDLE_CUTOFF_MS
        val needNewSession = currentSessionId == null || userChanged || idleTooLong

        if (needNewSession) {
            // Close the previous session (best-effort touch of ended_at).
            val prev = currentSessionId
            if (prev != null && !userChanged) {
                runCatching {
                    val nowIso = Instant.now().toString()
                    val finalCount = trackCount
                    authManager.supabase.postgrest["play_sessions"].update({
                        set("ended_at", nowIso)
                        set("track_count", finalCount)
                    }) {
                        filter { eq("id", prev) }
                    }
                }.onFailure { Log.w(TAG, "close session $prev: ${it.message}") }
            }
            currentSessionId = createSession(uid)
            currentUserId = uid
            trackCount = 0
        }
        lastPlayAtMs = playedAtMs
        trackCount += 1
        currentSessionId
    }

    private suspend fun createSession(uid: String): String? {
        return runCatching {
            authManager.supabase.postgrest["play_sessions"]
                .insert(
                    SbPlaySessionInsert(
                        user_id = uid,
                        device_id = deviceRegistry.snapshotRemoteId(),
                    )
                ) { select() }
                .decodeSingleOrNull<SbPlaySessionRow>()
                ?.id
        }.getOrElse {
            Log.e(TAG, "createSession failed: ${it.message}")
            null
        }
    }

    suspend fun reset() = mutex.withLock {
        currentSessionId = null
        currentUserId = null
        lastPlayAtMs = 0L
        trackCount = 0
    }
}
