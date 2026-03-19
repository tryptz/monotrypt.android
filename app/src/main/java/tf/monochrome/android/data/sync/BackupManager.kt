package tf.monochrome.android.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val backup = json.decodeFromString<LibraryBackup>(jsonStr)
        
        // Import favorites
        backup.favoriteTracks.forEach { favoriteDao.insertFavoriteTrack(it) }
        backup.favoriteAlbums.forEach { favoriteDao.insertFavoriteAlbum(it) }
        backup.favoriteArtists.forEach { favoriteDao.insertFavoriteArtist(it) }
        
        // Import history
        backup.history.forEach { historyDao.insert(it) }
        
        // Import playlists
        backup.playlists.forEach { pb ->
            playlistDao.insertPlaylist(pb.playlist)
            pb.tracks.forEach { playlistDao.insertPlaylistTrack(it) }
        }
    }
}
