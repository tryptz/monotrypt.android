package tf.monochrome.android.data.scrobbling

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import tf.monochrome.android.data.preferences.PreferencesManager
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.BuildConfig
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobblingService @Inject constructor(
    private val httpClient: HttpClient,
    private val preferences: PreferencesManager
) {
    companion object {
        private const val LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/"
        private const val LISTENBRAINZ_API_URL = "https://api.listenbrainz.org/1/submit-listens"
        
        private val LASTFM_API_KEY: String get() = BuildConfig.LASTFM_API_KEY
        private val LASTFM_API_SECRET: String get() = BuildConfig.LASTFM_API_SECRET
    }

    private fun getMd5Hash(input: String): String {
        return MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun generateLastFmSignature(params: Map<String, String>): String {
        val sortedParams = params.toSortedMap().entries.joinToString("") { "${it.key}${it.value}" }
        return getMd5Hash(sortedParams + LASTFM_API_SECRET)
    }

    suspend fun updateNowPlaying(track: Track) {
        val lastFmEnabled = preferences.lastFmEnabled.first()
        val listenBrainzEnabled = preferences.listenBrainzEnabled.first()

        if (lastFmEnabled && lastFmConfigured()) {
            updateLastFmNowPlaying(track)
        }

        if (listenBrainzEnabled) {
            submitListenBrainz(track, "playing_now")
        }
    }

    suspend fun scrobbleTrack(track: Track, timestampUnix: Long = System.currentTimeMillis() / 1000) {
        val lastFmEnabled = preferences.lastFmEnabled.first()
        val listenBrainzEnabled = preferences.listenBrainzEnabled.first()

        if (lastFmEnabled && lastFmConfigured()) {
            scrobbleLastFmTrack(track, timestampUnix)
        }

        if (listenBrainzEnabled) {
            submitListenBrainz(track, "single")
        }
    }

    private suspend fun updateLastFmNowPlaying(track: Track) {
        val sessionKey = preferences.lastFmSessionKey.first() ?: return
        
        try {
            val params = mapOf(
                "method" to "track.updateNowPlaying",
                "api_key" to LASTFM_API_KEY,
                "sk" to sessionKey,
                "track" to track.title,
                "artist" to (track.artist?.name ?: "Unknown Artist")
            ).toMutableMap()
            
            track.album?.title?.let { params["album"] = it }
            
            val sig = generateLastFmSignature(params)
            params["api_sig"] = sig
            params["format"] = "json"
            
            val response = httpClient.post(LASTFM_API_URL) {
                setBody(FormDataContent(Parameters.build {
                    params.forEach { (k, v) -> append(k, v) }
                }))
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun scrobbleLastFmTrack(track: Track, timestampUnix: Long) {
        val sessionKey = preferences.lastFmSessionKey.first() ?: return
        
        try {
            val params = mapOf(
                "method" to "track.scrobble",
                "api_key" to LASTFM_API_KEY,
                "sk" to sessionKey,
                "timestamp[0]" to timestampUnix.toString(),
                "track[0]" to track.title,
                "artist[0]" to (track.artist?.name ?: "Unknown Artist")
            ).toMutableMap()
            
            track.album?.title?.let { params["album[0]"] = it }
            
            val sig = generateLastFmSignature(params)
            params["api_sig"] = sig
            params["format"] = "json"
            
            val response = httpClient.post(LASTFM_API_URL) {
                setBody(FormDataContent(Parameters.build {
                    params.forEach { (k, v) -> append(k, v) }
                }))
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun submitListenBrainz(track: Track, listenType: String) {
        val token = preferences.listenBrainzToken.first() ?: return
        
        try {
            val payload = buildJsonObject {
                put("listen_type", listenType)
                put("payload", buildJsonArray {
                    add(buildJsonObject {
                        put("listened_at", System.currentTimeMillis() / 1000)
                        put("track_metadata", buildJsonObject {
                            put("artist_name", track.artist?.name ?: "Unknown Artist")
                            put("track_name", track.title)
                            track.album?.title?.let { put("release_name", it) }
                        })
                    })
                })
            }
            
            val response = httpClient.post(LISTENBRAINZ_API_URL) {
                header("Authorization", "Token $token")
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (_: Exception) {
        }
    }

    private fun lastFmConfigured(): Boolean =
        LASTFM_API_KEY.isNotBlank() && LASTFM_API_SECRET.isNotBlank()
}
