package tf.monochrome.android.spotify.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import tf.monochrome.android.spotify.api.model.SpotifyArtistDetails
import tf.monochrome.android.spotify.api.model.SpotifyArtistSearchResponse
import tf.monochrome.android.spotify.api.model.SpotifyCurrentlyPlayingResponse
import tf.monochrome.android.spotify.api.model.SpotifyPlaylistItemsResponse
import tf.monochrome.android.spotify.api.model.SpotifyPlaylistsResponse
import tf.monochrome.android.spotify.api.model.SpotifyRecentlyPlayedResponse
import tf.monochrome.android.spotify.api.model.SpotifySavedTracksResponse
import tf.monochrome.android.spotify.api.model.SpotifyTopTracksResponse
import tf.monochrome.android.spotify.api.model.SpotifyTrack
import tf.monochrome.android.spotify.api.model.SpotifyTrackSearchResponse
import tf.monochrome.android.spotify.api.model.SpotifyUserProfile
import javax.inject.Singleton

class SpotifyApiException(
    val endpoint: String,
    val statusCode: Int,
    private val responseBody: String,
) : IllegalStateException(
    "Spotify $endpoint returned HTTP $statusCode" +
        responseBody.takeIf { it.isNotBlank() }?.let { ": ${it.sanitizeSpotifyErrorBody()}" }.orEmpty()
) {
    val isOwnerPremiumRequired: Boolean
        get() = statusCode == 403 &&
            responseBody.contains("Active premium subscription required", ignoreCase = true)

    val isScopeMissing: Boolean
        get() = statusCode == 403 && responseBody.contains("insufficient_scope", ignoreCase = true)

    val isRateLimited: Boolean
        get() = statusCode == 429

    val isEndpointUnavailable: Boolean
        get() = statusCode == 403 || statusCode == 404 || statusCode == 410
}

@Singleton
class SpotifyApi(
    private val httpClient: HttpClient,
    private val apiClient: SpotifyApiClient,
) {
    suspend fun searchTrack(query: String, limit: Int = 1): SpotifyTrack? =
        searchTracks(query = query, limit = limit).firstOrNull()

    suspend fun searchTracks(
        query: String,
        limit: Int = SEARCH_PAGE_LIMIT,
        offset: Int = 0,
    ): List<SpotifyTrack> {
        if (query.isBlank()) return emptyList()
        val response = authorizedGet("/search") {
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", spotifySearchPageLimit(limit))
            parameter("offset", spotifySearchOffset(offset))
        }
        return response.body<SpotifyTrackSearchResponse>()
            .tracks
            ?.items
            .orEmpty()
            .filter { it.isUsable }
    }

    suspend fun searchTracksPaged(query: String, targetCount: Int = 30): List<SpotifyTrack> {
        if (query.isBlank() || targetCount <= 0) return emptyList()
        val out = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (out.size < targetCount) {
            val page = searchTracks(query = query, limit = SEARCH_PAGE_LIMIT, offset = offset)
            if (page.isEmpty()) break
            out += page
            offset += SEARCH_PAGE_LIMIT
        }
        return out.distinctBy { it.stableId }.take(targetCount)
    }

    suspend fun artist(artistId: String): SpotifyArtistDetails =
        authorizedGet("/artists/$artistId").body()

    suspend fun searchArtists(query: String, limit: Int = SEARCH_PAGE_LIMIT): List<SpotifyArtistDetails> =
        authorizedGet("/search") {
            parameter("q", query)
            parameter("type", "artist")
            parameter("limit", spotifySearchPageLimit(limit))
        }.body<SpotifyArtistSearchResponse>().artists?.items.orEmpty()

    suspend fun recentlyPlayed(limit: Int = 50): List<SpotifyTrack> =
        authorizedGet("/me/player/recently-played") {
            parameter("limit", limit.coerceIn(1, 50))
        }.body<SpotifyRecentlyPlayedResponse>().items.mapNotNull { it.track }.filter { it.isUsable }

    suspend fun topTracks(limit: Int = 50, offset: Int = 0): List<SpotifyTrack> =
        authorizedGet("/me/top/tracks") {
            parameter("limit", limit.coerceIn(1, 50))
            parameter("offset", offset.coerceAtLeast(0))
        }.body<SpotifyTopTracksResponse>().items.filter { it.isUsable }

    suspend fun savedTracks(limit: Int = 50, offset: Int = 0): List<SpotifyTrack> =
        authorizedGet("/me/tracks") {
            parameter("limit", limit.coerceIn(1, 50))
            parameter("offset", offset.coerceAtLeast(0))
        }.body<SpotifySavedTracksResponse>().items.mapNotNull { it.track }.filter { it.isUsable }

    suspend fun userPlaylists(limit: Int = 50, offset: Int = 0): SpotifyPlaylistsResponse =
        authorizedGet("/me/playlists") {
            parameter("limit", limit.coerceIn(1, 50))
            parameter("offset", offset.coerceAtLeast(0))
        }.body()

    suspend fun playlistItems(playlistId: String, limit: Int = 50, offset: Int = 0): List<SpotifyTrack> {
        if (playlistId.isBlank()) return emptyList()
        return authorizedGet("/playlists/$playlistId/items") {
            parameter("limit", limit.coerceIn(1, 50))
            parameter("offset", offset.coerceAtLeast(0))
            parameter("fields", "items(item(id,name,uri,duration_ms,external_ids,artists(id,name),album(id,name,images)))")
        }.body<SpotifyPlaylistItemsResponse>()
            .items
            .mapNotNull { it.item ?: it.track }
            .filter { it.isUsable }
    }

    suspend fun currentlyPlaying(): SpotifyTrack? {
        val response = authorizedGet("/me/player/currently-playing")
        if (response.status == HttpStatusCode.NoContent) return null
        return response.body<SpotifyCurrentlyPlayingResponse>().item?.takeIf { it.isUsable }
    }

    suspend fun me(): SpotifyUserProfile =
        authorizedGet("/me").body()

    private suspend fun authorizedGet(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val first = getAuthorized(path, block)
        if (first.status == HttpStatusCode.TooManyRequests) {
            delay(first.retryAfterMillis())
            val afterRateLimit = getAuthorized(path, block)
            if (afterRateLimit.status != HttpStatusCode.Unauthorized) {
                afterRateLimit.throwIfFailed(path)
                return afterRateLimit
            }
            return retryAfterUnauthorized(path, block, afterRateLimit)
        }
        if (first.status != HttpStatusCode.Unauthorized) {
            first.throwIfFailed(path)
            return first
        }
        return retryAfterUnauthorized(path, block, first)
    }

    private suspend fun retryAfterUnauthorized(
        path: String,
        block: HttpRequestBuilder.() -> Unit,
        unauthorizedResponse: HttpResponse,
    ): HttpResponse {
        if (!apiClient.refreshToken()) {
            unauthorizedResponse.throwIfFailed(path)
            return unauthorizedResponse
        }
        val second = getAuthorized(path, block)
        if (second.status == HttpStatusCode.TooManyRequests) {
            delay(second.retryAfterMillis())
            return getAuthorized(path, block).also { it.throwIfFailed(path) }
        }
        return second.also { it.throwIfFailed(path) }
    }

    private suspend fun getAuthorized(
        path: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse = httpClient.get(BASE_URL + path) {
        header(HttpHeaders.Authorization, "Bearer ${apiClient.accessTokenOrThrow()}")
        block()
    }

    private suspend fun HttpResponse.throwIfFailed(path: String) {
        if (status.value in 200..299) return
        throw SpotifyApiException(
            endpoint = path,
            statusCode = status.value,
            responseBody = runCatching { bodyAsText() }.getOrDefault("")
        )
    }

    private fun HttpResponse.retryAfterMillis(): Long =
        (headers["Retry-After"]?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS)
            .coerceAtLeast(1L) * 1000L

    private companion object {
        const val BASE_URL = "https://api.spotify.com/v1"
        const val SEARCH_PAGE_LIMIT = 10
        const val DEFAULT_RETRY_AFTER_SECONDS = 2L
    }
}

private fun String.sanitizeSpotifyErrorBody(): String =
    replace("\\n", " ")
        .replace("\\r", " ")
        .take(240)

internal fun spotifySearchPageLimit(limit: Int): Int = limit.coerceIn(1, 10)

internal fun spotifySearchOffset(offset: Int): Int = offset.coerceAtLeast(0)
