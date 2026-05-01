package tf.monochrome.android.data.api

import android.util.Base64
import android.util.LruCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import tf.monochrome.android.data.api.model.AlbumResponse
import tf.monochrome.android.data.api.model.AlbumTrackItem
import tf.monochrome.android.data.api.model.ArtistContentResponse
import tf.monochrome.android.data.api.model.ArtistResponse
import tf.monochrome.android.data.api.model.LyricsResponse
import tf.monochrome.android.data.api.model.ManifestJson
import tf.monochrome.android.data.api.model.MixResponse
import tf.monochrome.android.data.api.model.PlaylistResponse
import tf.monochrome.android.data.api.model.RecommendationsResponse
import tf.monochrome.android.data.api.model.SearchResponse
import tf.monochrome.android.data.api.model.TrackInfoResponse
import tf.monochrome.android.data.api.model.TrackStreamResponse
import tf.monochrome.android.domain.model.Album
import tf.monochrome.android.domain.model.AlbumDetail
import tf.monochrome.android.domain.model.Artist
import tf.monochrome.android.domain.model.TrackStream
import tf.monochrome.android.domain.model.ArtistDetail
import tf.monochrome.android.domain.model.AudioQuality
import tf.monochrome.android.domain.model.Lyrics
import tf.monochrome.android.domain.model.LyricLine
import tf.monochrome.android.domain.model.Playlist
import tf.monochrome.android.domain.model.PlaylistCreator
import tf.monochrome.android.domain.model.ReplayGainValues
import tf.monochrome.android.domain.model.SearchResult
import tf.monochrome.android.domain.model.Track
import tf.monochrome.android.domain.model.LyricWord
import tf.monochrome.android.util.RomajiConverter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class HiFiApiClient @Inject constructor(
    private val instanceManager: InstanceManager,
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val cache = LruCache<String, CacheEntry>(200)

    private data class CacheEntry(
        val data: Any,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(ttlMs: Long = 30 * 60 * 1000L): Boolean {
            return System.currentTimeMillis() - timestamp < ttlMs
        }
    }

    private suspend fun fetchWithRetry(
        path: String,
        instanceType: InstanceType = InstanceType.API,
        minVersion: String? = null
    ): String {
        val instances = instanceManager.getInstances(instanceType)
            .let { list ->
                if (minVersion != null) {
                    list.filter { instance ->
                        instance.version == null ||
                            (instance.version.toDoubleOrNull() ?: 0.0) >= (minVersion.toDoubleOrNull() ?: 0.0)
                    }.ifEmpty { list }
                } else list
            }

        if (instances.isEmpty()) throw Exception("No API instances available")

        var lastError: Throwable? = null
        val maxAttempts = instances.size * 2
        var instanceIndex = Random.nextInt(instances.size)

        repeat(maxAttempts) {
            val instance = instances[instanceIndex % instances.size]
            val url = instance.url.trimEnd('/') + path

            try {
                val response = httpClient.get(url)
                when {
                    response.status.value == 429 -> {
                        instanceIndex++
                        delay(500)
                    }
                    response.status.value in 500..599 -> {
                        instanceIndex++
                    }
                    response.status.value == 401 -> {
                        instanceIndex++
                    }
                    response.status.isSuccess() -> {
                        return response.bodyAsText()
                    }
                    else -> {
                        lastError = Exception("HTTP ${response.status.value}")
                        instanceIndex++
                    }
                }
            } catch (e: Exception) {
                lastError = e
                instanceIndex++
                delay(200)
            }
        }

        throw lastError ?: Exception("All instances failed for $path")
    }

    // --- Search ---

    suspend fun searchTracks(query: String): List<Track> {
        val cacheKey = "search_tracks_$query"
        cache.get(cacheKey)?.let { entry ->
            if (entry.isValid()) {
                @Suppress("UNCHECKED_CAST")
                return entry.data as List<Track>
            }
        }

        val body = fetchWithRetry("/search/?s=${query.encodeUrl()}")
        val response = parseSearchResponse(body)
        val tracks = response.items.map { it.toTrack() }
        cache.put(cacheKey, CacheEntry(tracks))
        return tracks
    }

    suspend fun searchAlbums(query: String): List<Album> {
        val cacheKey = "search_albums_$query"
        cache.get(cacheKey)?.let { entry ->
            if (entry.isValid()) {
                @Suppress("UNCHECKED_CAST")
                return entry.data as List<Album>
            }
        }

        val body = fetchWithRetry("/search/?al=${query.encodeUrl()}")
        val response = parseSearchResponse(body)
        val albums = response.items.map { it.toAlbum() }
        cache.put(cacheKey, CacheEntry(albums))
        return albums
    }

    suspend fun searchArtists(query: String): List<Artist> {
        val cacheKey = "search_artists_$query"
        cache.get(cacheKey)?.let { entry ->
            if (entry.isValid()) {
                @Suppress("UNCHECKED_CAST")
                return entry.data as List<Artist>
            }
        }

        val body = fetchWithRetry("/search/?a=${query.encodeUrl()}")
        val response = parseSearchResponse(body)
        val artists = response.items.map { it.toArtist() }
        cache.put(cacheKey, CacheEntry(artists))
        return artists
    }

    suspend fun searchPlaylists(query: String): List<Playlist> {
        val body = fetchWithRetry("/search/?p=${query.encodeUrl()}")
        val response = parseSearchResponse(body)
        return response.items.map { it.toPlaylist() }
    }

    suspend fun search(query: String): SearchResult {
        return SearchResult(
            tracks = runCatching { searchTracks(query) }.getOrDefault(emptyList()),
            albums = runCatching { searchAlbums(query) }.getOrDefault(emptyList()),
            artists = runCatching { searchArtists(query) }.getOrDefault(emptyList()),
            playlists = runCatching { searchPlaylists(query) }.getOrDefault(emptyList())
        )
    }

    // Qobuz catalog search — hits the user-configured Qobuz instance via
    // InstanceType.DOWNLOAD (same pool used for downloads). Returns an empty
    // SearchResult when the instance isn't set or the request fails, so the
    // TIDAL search flow keeps working even if the Qobuz endpoint is offline.
    suspend fun searchQobuz(query: String): SearchResult {
        suspend fun parsedSearch(path: String) =
            runCatching {
                val body = fetchWithRetry(path, instanceType = InstanceType.DOWNLOAD)
                parseSearchResponse(body)
            }.getOrNull()

        val tracks = parsedSearch("/search/?s=${query.encodeUrl()}")?.items?.map { it.toTrack() }
            ?: emptyList()
        val albums = parsedSearch("/search/?al=${query.encodeUrl()}")?.items?.map { it.toAlbum() }
            ?: emptyList()
        val artists = parsedSearch("/search/?a=${query.encodeUrl()}")?.items?.map { it.toArtist() }
            ?: emptyList()
        val playlists = parsedSearch("/search/?p=${query.encodeUrl()}")?.items?.map { it.toPlaylist() }
            ?: emptyList()
        return SearchResult(tracks = tracks, albums = albums, artists = artists, playlists = playlists)
    }

    // --- Album ---

    suspend fun getAlbum(albumId: Long): AlbumDetail {
        val cacheKey = "album_$albumId"
        cache.get(cacheKey)?.let { entry ->
            if (entry.isValid()) return entry.data as AlbumDetail
        }

        val body = fetchWithRetry("/album/?id=$albumId")
        val response = json.decodeFromString<AlbumResponse>(unwrapResponse(body))

        val album = Album(
            id = response.id,
            title = response.title,
            artist = response.artist?.toDomain(),
            artists = response.artists.map { it.toDomain() },
            numberOfTracks = response.numberOfTracks,
            releaseDate = response.releaseDate,
            cover = response.cover,
            explicit = response.explicit,
            type = response.type,
            duration = response.duration
        )

        val trackItems = response.tracks?.items ?: response.items ?: emptyList()
        var tracks = trackItems.map { it.toTrack(album) }

        // Handle pagination for large albums
        val total = response.tracks?.totalNumberOfItems ?: response.numberOfTracks ?: tracks.size
        if (tracks.size < total) {
            val remaining = mutableListOf<Track>()
            var offset = tracks.size
            while (offset < total) {
                val pageBody = fetchWithRetry("/album/?id=$albumId&offset=$offset&limit=100")
                val pageResponse = json.decodeFromString<AlbumResponse>(unwrapResponse(pageBody))
                val pageItems = pageResponse.tracks?.items ?: pageResponse.items ?: emptyList()
                remaining.addAll(pageItems.map { it.toTrack(album) })
                offset += pageItems.size
                if (pageItems.isEmpty()) break
            }
            tracks = tracks + remaining
        }

        val detail = AlbumDetail(album = album, tracks = tracks)
        cache.put(cacheKey, CacheEntry(detail))
        return detail
    }

    // --- Artist ---

    suspend fun getArtist(artistId: Long): ArtistDetail {
        val cacheKey = "artist_$artistId"
        cache.get(cacheKey)?.let { entry ->
            if (entry.isValid()) return entry.data as ArtistDetail
        }

        // Fetch artist info
        val infoBody = fetchWithRetry("/artist/?id=$artistId")
        val artistResponse = json.decodeFromString<ArtistResponse>(unwrapResponse(infoBody))
        val artist = Artist(
            id = artistResponse.id,
            name = artistResponse.name,
            picture = artistResponse.picture,
            artistTypes = artistResponse.artistTypes
        )

        // Fetch artist content (albums, tracks)
        val contentBody = fetchWithRetry("/artist/?f=$artistId&skip_tracks=true")
        val contentResponse = json.decodeFromString<ArtistContentResponse>(unwrapResponse(contentBody))

        val allAlbums = (contentResponse.albums?.items ?: contentResponse.items?.map { item ->
            tf.monochrome.android.data.api.model.ApiAlbum(
                id = item.id,
                title = item.title,
                artist = item.artist,
                artists = item.artists,
                numberOfTracks = item.numberOfTracks,
                releaseDate = item.releaseDate,
                cover = item.cover,
                explicit = item.explicit,
                type = item.type
            )
        } ?: emptyList()).map { it.toDomain() }

        val albums = allAlbums.filter { it.type?.equals("ALBUM", ignoreCase = true) != false }
        val eps = allAlbums.filter { it.type?.equals("EP", ignoreCase = true) == true }
        val singles = allAlbums.filter { it.type?.equals("SINGLE", ignoreCase = true) == true }

        val topTracks = contentResponse.topTracks?.items?.map { it.toDomain() } ?: emptyList()

        // Try fetching similar artists
        val similarArtists = try {
            val similarBody = fetchWithRetry("/artist/similar/?id=$artistId", minVersion = "2.3")
            val items = json.decodeFromString<SearchResponse>(unwrapResponse(similarBody))
            items.items.map { it.toArtist() }
        } catch (_: Exception) {
            emptyList()
        }

        // Fetch unreleased/community tracks (ArtistGrid)
        val unreleasedTracks = try {
            val unreleasedBody = fetchWithRetry("/artist/unreleased/?id=$artistId", minVersion = "2.5")
            val items = json.decodeFromString<SearchResponse>(unwrapResponse(unreleasedBody))
            items.items.map { it.toTrack() }
        } catch (_: Exception) {
            emptyList()
        }

        val detail = ArtistDetail(
            artist = artist,
            topTracks = topTracks,
            albums = albums,
            eps = eps,
            singles = singles,
            unreleasedTracks = unreleasedTracks,
            similarArtists = similarArtists
        )
        cache.put(cacheKey, CacheEntry(detail))
        return detail
    }

    // --- Playlist ---

    suspend fun getPlaylist(playlistId: String): Playlist {
        val body = fetchWithRetry("/playlist/?id=$playlistId")
        val response = json.decodeFromString<PlaylistResponse>(unwrapResponse(body))

        val trackItems = response.items ?: response.tracks ?: emptyList()
        var tracks = trackItems.mapNotNull { it.toDomain() }

        // Handle pagination
        val total = response.numberOfTracks ?: tracks.size
        if (tracks.size < total) {
            var offset = tracks.size
            while (offset < total) {
                val pageBody = fetchWithRetry("/playlist/?id=$playlistId&offset=$offset")
                val pageResponse = json.decodeFromString<PlaylistResponse>(unwrapResponse(pageBody))
                val pageItems = pageResponse.items ?: pageResponse.tracks ?: emptyList()
                tracks = tracks + pageItems.mapNotNull { it.toDomain() }
                offset += pageItems.size
                if (pageItems.isEmpty()) break
            }
        }

        return Playlist(
            uuid = response.uuid,
            title = response.title,
            description = response.description,
            numberOfTracks = response.numberOfTracks,
            duration = response.duration,
            cover = response.cover ?: response.squareImage ?: response.image,
            creator = response.creator?.let { PlaylistCreator(it.id, it.name) },
            tracks = tracks
        )
    }

    // --- Streaming ---

    suspend fun getTrackStream(
        trackId: Long,
        quality: AudioQuality,
        forDownload: Boolean = false
    ): TrackStream {
        val body = fetchWithRetry(
            "/track/?id=$trackId&quality=${quality.apiValue}",
            instanceType = if (forDownload) InstanceType.DOWNLOAD else InstanceType.STREAMING
        )
        val streamResponse = json.decodeFromString<TrackStreamResponse>(unwrapResponse(body))

        val streamUrl = extractStreamUrlFromManifest(streamResponse.manifest)
        val isDash = streamUrl?.contains("<MPD") == true || streamUrl?.endsWith(".mpd") == true

        if (streamUrl == null) {
            // Fallback to lower quality
            if (quality == AudioQuality.HI_RES) {
                return getTrackStream(trackId, AudioQuality.LOSSLESS, forDownload)
            }
            throw Exception("Could not extract stream URL for track $trackId")
        }

        // Get track info for metadata
        val trackInfo = try {
            val infoBody = fetchWithRetry("/info/?id=$trackId")
            json.decodeFromString<TrackInfoResponse>(unwrapResponse(infoBody))
        } catch (_: Exception) {
            null
        }

        val track = Track(
            id = trackId,
            title = trackInfo?.title ?: "",
            duration = trackInfo?.duration ?: 0,
            artist = trackInfo?.artist?.toDomain(),
            artists = trackInfo?.artists?.map { it.toDomain() } ?: emptyList(),
            album = trackInfo?.album?.toDomain(),
            audioQuality = trackInfo?.audioQuality,
            explicit = trackInfo?.explicit ?: false,
            trackNumber = trackInfo?.trackNumber,
            volumeNumber = trackInfo?.volumeNumber
        )

        return TrackStream(
            track = track,
            streamUrl = streamUrl,
            isDash = isDash,
            replayGain = ReplayGainValues(
                trackReplayGain = streamResponse.trackReplayGain,
                trackPeakAmplitude = streamResponse.trackPeakAmplitude,
                albumReplayGain = streamResponse.albumReplayGain,
                albumPeakAmplitude = streamResponse.albumPeakAmplitude
            )
        )
    }

    // --- Recommendations ---

    suspend fun getRecommendations(trackId: Long): List<Track> {
        val body = fetchWithRetry("/recommendations/?id=$trackId", minVersion = "2.4")
        val response = json.decodeFromString<RecommendationsResponse>(unwrapResponse(body))
        return response.items.map { apiTrack ->
            // Recommendations may return incomplete metadata, so fetch full info
            if (apiTrack.title.isBlank() && apiTrack.id != 0L) {
                try {
                    val infoBody = fetchWithRetry("/info/?id=${apiTrack.id}")
                    val info = json.decodeFromString<TrackInfoResponse>(unwrapResponse(infoBody))
                    Track(
                        id = apiTrack.id,
                        title = info.title,
                        duration = info.duration,
                        artist = info.artist?.toDomain(),
                        artists = info.artists.map { it.toDomain() },
                        album = info.album?.toDomain(),
                        audioQuality = info.audioQuality,
                        explicit = info.explicit,
                        trackNumber = info.trackNumber,
                        volumeNumber = info.volumeNumber
                    )
                } catch (_: Exception) {
                    apiTrack.toDomain()
                }
            } else {
                apiTrack.toDomain()
            }
        }
    }

    // --- Mix ---

    suspend fun getMix(mixId: String): List<Track> {
        val body = fetchWithRetry("/mix/?id=$mixId", minVersion = "2.3")
        val response = json.decodeFromString<MixResponse>(unwrapResponse(body))
        return response.items.map { it.toDomain() }
    }

    // --- Lyrics ---

    suspend fun getLyrics(trackId: Long, convertToRomaji: Boolean = false): Lyrics? {
        return try {
            val body = fetchWithRetry("/lyrics/?id=$trackId")
            val response = json.decodeFromString<LyricsResponse>(unwrapResponse(body))
            parseLyrics(response, convertToRomaji)
        } catch (_: Exception) {
            null
        }
    }

    // --- Helper Methods ---

    fun extractStreamUrlFromManifest(manifest: String?): String? {
        if (manifest == null) return null

        // Try base64 decode
        val decoded = try {
            String(Base64.decode(manifest, Base64.DEFAULT))
        } catch (_: Exception) {
            manifest
        }

        // Check if DASH XML
        if (decoded.contains("<MPD")) {
            return decoded // Return raw MPD for ExoPlayer DashMediaSource
        }

        // Try JSON parse
        try {
            val manifestJson = json.decodeFromString<ManifestJson>(decoded)
            if (manifestJson.urls.isNotEmpty()) {
                return manifestJson.urls.sortedByQuality().first()
            }
        } catch (_: Exception) {
            // Not JSON
        }

        // Try as plain URL
        val urlRegex = Regex("https?://[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=%]+")
        urlRegex.find(decoded)?.let { return it.value }

        return null
    }

    private fun List<String>.sortedByQuality(): List<String> {
        val keywords = listOf("flac", "lossless", "hi-res", "high")
        return sortedBy { url ->
            val lower = url.lowercase()
            keywords.indexOfFirst { lower.contains(it) }.let { if (it >= 0) it else 999 }
        }
    }

    private fun unwrapResponse(body: String): String {
        // Some API responses wrap data in { data: ... }
        return try {
            val element = json.parseToJsonElement(body)
            if (element is kotlinx.serialization.json.JsonObject && element.containsKey("data")) {
                element["data"].toString()
            } else {
                body
            }
        } catch (_: Exception) {
            body
        }
    }

    private fun parseSearchResponse(body: String): SearchResponse {
        val unwrapped = unwrapResponse(body)
        return try {
            json.decodeFromString<SearchResponse>(unwrapped)
        } catch (_: Exception) {
            // Try parsing as array
            try {
                val items = json.decodeFromString<List<tf.monochrome.android.data.api.model.SearchItem>>(unwrapped)
                SearchResponse(items = items)
            } catch (_: Exception) {
                SearchResponse()
            }
        }
    }

    private fun parseLyrics(response: LyricsResponse, convertToRomaji: Boolean): Lyrics? {
        val subtitles = response.subtitles ?: return response.lyrics?.let { raw ->
            val text = if (convertToRomaji) RomajiConverter.convert(raw) else raw
            Lyrics(lines = text.split("\n").map { line -> LyricLine(0, line) }, isSynced = false)
        }

        // Parse LRC-style subtitles (including enhanced word-level [mm:ss.ms]<mm:ss.ms>word)
        val lines = mutableListOf<LyricLine>()
        val lrcLineRegex = Regex("\\[(\\d+):(\\d+\\.\\d+)](.*)")
        val lrcWordRegex = Regex("<(\\d+):(\\d+\\.\\d+)>([^<]*)")

        subtitles.split("\n").forEach { rawLine ->
            lrcLineRegex.find(rawLine)?.let { lineMatch ->
                val minutes = lineMatch.groupValues[1].toLongOrNull() ?: 0
                val seconds = lineMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                val lineTimeMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                val lineContent = lineMatch.groupValues[3]

                // Extract word-level sync if present
                val words = mutableListOf<LyricWord>()
                var lastTime = lineTimeMs
                
                lrcWordRegex.findAll(lineContent).forEach { wordMatch ->
                    val wMinutes = wordMatch.groupValues[1].toLongOrNull() ?: 0
                    val wSeconds = wordMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                    val wordTimeMs = (wMinutes * 60 * 1000) + (wSeconds * 1000).toLong()
                    var wordText = wordMatch.groupValues[3].trim()
                    
                    if (convertToRomaji) wordText = RomajiConverter.convert(wordText)
                    
                    words.add(LyricWord(lastTime, wordTimeMs, wordText))
                    lastTime = wordTimeMs
                }

                var finalText = if (words.isEmpty()) lineContent.trim() else words.joinToString(" ") { it.text }
                if (convertToRomaji && words.isEmpty()) finalText = RomajiConverter.convert(finalText)

                if (finalText.isNotBlank()) {
                    lines.add(LyricLine(timeMs = lineTimeMs, text = finalText, words = words))
                }
            }
        }

        return if (lines.isNotEmpty()) {
            Lyrics(lines = lines, isSynced = true)
        } else {
            val raw = subtitles.split("\n")
            Lyrics(
                lines = raw.map { LyricLine(0, if (convertToRomaji) RomajiConverter.convert(it) else it) },
                isSynced = false
            )
        }
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}

// --- Extension functions for API model → Domain model conversion ---

private fun tf.monochrome.android.data.api.model.ApiArtist.toDomain() = Artist(
    id = id,
    name = name,
    picture = picture,
    artistTypes = artistTypes
)

private fun tf.monochrome.android.data.api.model.ApiAlbum.toDomain() = Album(
    id = id,
    title = title,
    artist = artist?.toDomain(),
    artists = artists.map { it.toDomain() },
    numberOfTracks = numberOfTracks,
    releaseDate = releaseDate,
    cover = cover,
    explicit = explicit,
    type = type,
    duration = duration
)

private fun tf.monochrome.android.data.api.model.ApiTrack.toDomain() = Track(
    id = id,
    title = title,
    duration = duration,
    artist = artist?.toDomain(),
    artists = artists.map { it.toDomain() },
    album = album?.toDomain(),
    audioQuality = audioQuality,
    explicit = explicit,
    trackNumber = trackNumber,
    volumeNumber = volumeNumber,
    popularity = popularity,
    type = type ?: "track",
    isUnavailable = unavailable,
    streamStartDate = streamStartDate
)

private fun tf.monochrome.android.data.api.model.SearchItem.toTrack() = Track(
    id = id,
    title = title,
    duration = duration,
    artist = artist?.toDomain(),
    artists = artists.map { it.toDomain() },
    album = album?.toDomain(),
    audioQuality = audioQuality,
    explicit = explicit,
    trackNumber = trackNumber,
    volumeNumber = volumeNumber,
    popularity = popularity,
    type = type ?: "track",
    streamStartDate = streamStartDate
)

private fun tf.monochrome.android.data.api.model.SearchItem.toAlbum() = Album(
    id = id,
    title = title.ifBlank { name ?: "" },
    artist = artist?.toDomain(),
    artists = artists.map { it.toDomain() },
    numberOfTracks = numberOfTracks,
    releaseDate = releaseDate,
    cover = cover ?: picture,
    explicit = explicit,
    type = type
)

private fun tf.monochrome.android.data.api.model.SearchItem.toArtist() = Artist(
    id = id,
    name = name ?: title,
    picture = picture,
    artistTypes = artistTypes
)

private fun tf.monochrome.android.data.api.model.SearchItem.toPlaylist() = Playlist(
    uuid = uuid ?: id.toString(),
    title = title.ifBlank { name ?: "" },
    description = description,
    numberOfTracks = numberOfTracks,
    cover = cover ?: squareImage ?: image,
    creator = creator?.let { PlaylistCreator(it.id, it.name) }
)

private fun tf.monochrome.android.data.api.model.AlbumTrackItem.toTrack(album: Album) = Track(
    id = item?.id ?: id,
    title = item?.title ?: title,
    duration = item?.duration ?: duration,
    artist = (item?.artist ?: artist)?.toDomain(),
    artists = (item?.artists ?: artists).map { it.toDomain() },
    album = album,
    audioQuality = item?.audioQuality ?: audioQuality,
    explicit = item?.explicit ?: explicit,
    trackNumber = item?.trackNumber ?: trackNumber,
    volumeNumber = item?.volumeNumber ?: volumeNumber,
    popularity = item?.popularity ?: popularity
)

private fun tf.monochrome.android.data.api.model.PlaylistTrackItem.toDomain(): Track? {
    if (item != null) return item.toDomain()
    val trackId = id ?: return null
    return Track(
        id = trackId,
        title = title ?: "",
        duration = duration ?: 0,
        artist = artist?.toDomain(),
        artists = artists?.map { it.toDomain() } ?: emptyList(),
        album = album?.toDomain(),
        trackNumber = trackNumber,
        audioQuality = audioQuality,
        explicit = explicit ?: false
    )
}
