package tf.monochrome.android.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import tf.monochrome.android.data.db.dao.FavoriteDao
import tf.monochrome.android.data.db.dao.HistoryDao
import tf.monochrome.android.data.db.dao.PlaylistDao
import tf.monochrome.android.data.db.entity.FavoriteAlbumEntity
import tf.monochrome.android.data.db.entity.FavoriteArtistEntity
import tf.monochrome.android.data.db.entity.FavoriteTrackEntity
import tf.monochrome.android.data.db.entity.HistoryTrackEntity
import tf.monochrome.android.data.db.entity.UserPlaylistEntity
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LibraryBackup(
    val version: Int = 1,
    val favoriteTracks: List<FavoriteTrackEntity> = emptyList(),
    val favoriteAlbums: List<FavoriteAlbumEntity> = emptyList(),
    val favoriteArtists: List<FavoriteArtistEntity> = emptyList(),
    val history: List<HistoryTrackEntity> = emptyList(),
    val playlists: List<PlaylistBackup> = emptyList()
)

@Serializable
data class PlaylistBackup(
    val playlist: UserPlaylistEntity,
    val tracks: List<tf.monochrome.android.data.db.entity.PlaylistTrackEntity>
)

@Singleton
class BackupManager @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val json: Json
) {
    suspend fun exportLibrary(): String {
        val favoriteTracks = favoriteDao.getFavoriteTracksSnapshot()
        val favoriteAlbums = favoriteDao.getFavoriteAlbumsSnapshot()
        val favoriteArtists = favoriteDao.getFavoriteArtistsSnapshot()
        val history = historyDao.getHistorySnapshot(500) // Export last 500 items
        
        val playlists = playlistDao.getAllPlaylistsSnapshot().map { playlist ->
            PlaylistBackup(
                playlist = playlist,
                tracks = playlistDao.getPlaylistTracksSnapshot(playlist.id)
            )
        }

        val backup = LibraryBackup(
            favoriteTracks = favoriteTracks,
            favoriteAlbums = favoriteAlbums,
            favoriteArtists = favoriteArtists,
            history = history,
            playlists = playlists
        )

        return json.encodeToString(backup)
    }

    suspend fun importLibrary(jsonStr: String): Result<Unit> = runCatching {
        // Try to parse the modern format first, or if the keys are missing, fallback to parsing manually via JsonElement
        val element = json.parseToJsonElement(jsonStr)
        if (element is JsonObject) {
            
            // ── Import Favorites ──
            val legacyFavs = element["favorites_tracks"] ?: element["favoriteTracks"]
            if (legacyFavs is JsonArray) {
                legacyFavs.forEach { item ->
                    if (item is JsonObject) {
                        try {
                            val id = item["id"]?.jsonPrimitive?.longOrNull ?: return@forEach
                            val title = item["title"]?.jsonPrimitive?.content ?: "Unknown"
                            val duration = item["duration"]?.jsonPrimitive?.intOrNull ?: 0
                            val addedAt = item["addedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                            
                            val artistObj = item["artist"] as? JsonObject
                            val artistId = artistObj?.get("id")?.jsonPrimitive?.longOrNull
                            val artistName = artistObj?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                            
                            val albumObj = item["album"] as? JsonObject
                            val albumId = albumObj?.get("id")?.jsonPrimitive?.longOrNull
                            val albumTitle = albumObj?.get("title")?.jsonPrimitive?.content
                            val albumCover = albumObj?.get("cover")?.jsonPrimitive?.content
                            
                            favoriteDao.insertFavoriteTrack(FavoriteTrackEntity(
                                id = id, title = title, duration = duration, 
                                artistId = artistId, artistName = artistName,
                                albumId = albumId, albumTitle = albumTitle, albumCover = albumCover,
                                addedAt = addedAt
                            ))
                        } catch (e: Exception) { /* skip malformed */ }
                    }
                }
            }

            // ── Import Playlists ──
            val legacyPlaylists = element["user_playlists"] ?: element["playlists"]
            if (legacyPlaylists is JsonArray) {
                legacyPlaylists.forEach { plItem ->
                    if (plItem is JsonObject) {
                        try {
                            // Some backups have `id` as top level, some might be `playlist: { id }`
                            val plObj = plItem["playlist"] as? JsonObject ?: plItem
                            
                            val id = plObj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
                            val name = plObj["name"]?.jsonPrimitive?.content ?: "Backup Playlist"
                            val createdAt = plObj["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                            
                            val playlistEntity = UserPlaylistEntity(
                                id = id,
                                name = name,
                                createdAt = createdAt,
                                updatedAt = createdAt
                            )
                            playlistDao.insertPlaylist(playlistEntity)

                            val tracksArray = plItem["tracks"] as? JsonArray
                            tracksArray?.forEachIndexed { index, trkItem ->
                                if (trkItem is JsonObject) {
                                    val trackId = trkItem["id"]?.jsonPrimitive?.longOrNull ?: return@forEachIndexed
                                    val trackTitle = trkItem["title"]?.jsonPrimitive?.content ?: "Unknown"
                                    val trackDuration = trkItem["duration"]?.jsonPrimitive?.intOrNull ?: 0
                                    val addedAt = trkItem["addedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                                    
                                    val artistObj = trkItem["artist"] as? JsonObject
                                    val artistName = artistObj?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                                    
                                    val albumObj = trkItem["album"] as? JsonObject
                                    val albumId = albumObj?.get("id")?.jsonPrimitive?.longOrNull
                                    val albumTitle = albumObj?.get("title")?.jsonPrimitive?.content
                                    val albumCover = albumObj?.get("cover")?.jsonPrimitive?.content

                                    playlistDao.insertPlaylistTrack(tf.monochrome.android.data.db.entity.PlaylistTrackEntity(
                                        playlistId = id,
                                        trackId = trackId,
                                        title = trackTitle,
                                        duration = trackDuration,
                                        artistName = artistName,
                                        albumId = albumId,
                                        albumTitle = albumTitle,
                                        albumCover = albumCover,
                                        position = index,
                                        addedAt = addedAt
                                    ))
                                }
                            }
                        } catch (e: Exception) { /* skip malformed */ }
                    }
                }
            }
        }
    }
}
