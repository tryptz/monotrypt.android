package tf.monochrome.android.radio.planner

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeoutOrNull
import tf.monochrome.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioPlannerClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun plan(request: RadioPlannerRequest): RadioPlan? {
        val baseUrl = BuildConfig.RADIO_PLANNER_URL.trim().trimEnd('/')
        val apiKey = BuildConfig.RADIO_PLANNER_API_KEY.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val response = httpClient.post("$baseUrl/api/radio/plan") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                if (response.status.value !in 200..299) {
                    Log.w(TAG, "Radio planner unavailable: ${response.failureSummary()}")
                    null
                } else {
                    response.body<RadioPlan>().takeIf { it.isUseful }
                }
            }.onFailure { error ->
                Log.w(TAG, "Radio planner unavailable: ${error.safeMessage()}")
            }.getOrNull()
        }
    }

    suspend fun songList(request: RadioSongListRequest): RadioSongListResponse? {
        val baseUrl = BuildConfig.RADIO_PLANNER_URL.trim().trimEnd('/')
        val apiKey = BuildConfig.RADIO_PLANNER_API_KEY.trim()
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                val response = httpClient.post("$baseUrl/api/radio/song-list") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when {
                    response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                        songListAuthorizationFailure(request.query)
                    response.status.value !in 200..299 ->
                        songListHttpFailure(request.query, response.failureSummary())
                    else -> response.body<RadioSongListResponse>()
                }
            }.onFailure { error ->
                Log.w(TAG, "Radio song-list tester unavailable: ${error.safeMessage()}")
            }.getOrNull()?.takeIf { it.songs.isNotEmpty() || it.message.isNotBlank() }
        }
    }

    private suspend fun HttpResponse.failureSummary(): String {
        val detail = runCatching { bodyAsText().trim().take(180) }.getOrDefault("")
        return buildString {
            append("HTTP ")
            append(status.value)
            if (detail.isNotBlank()) {
                append(": ")
                append(detail)
            }
        }
    }

    private fun Throwable.safeMessage(): String =
        this::class.java.simpleName + (message?.take(180)?.let { ": $it" } ?: "")

    private companion object {
        const val TAG = "RadioPlanner"
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}

internal fun songListAuthorizationFailure(query: String): RadioSongListResponse =
    RadioSongListResponse(
        query = query,
        message = "Tryptify-Playlist authorization failed",
        safety = SongListSafety(
            modelBacked = false,
            confidence = 0f,
            fallbackReason = "The planner rejected the Bearer key. Rebuild the APK with the current planner key or update the Railway API key.",
        ),
    )

internal fun songListHttpFailure(query: String, failureSummary: String): RadioSongListResponse =
    RadioSongListResponse(
        query = query,
        message = "Tryptify-Playlist request failed",
        safety = SongListSafety(
            modelBacked = false,
            confidence = 0f,
            fallbackReason = failureSummary.ifBlank { "Planner request failed before the LLM returned songs." },
        ),
    )
