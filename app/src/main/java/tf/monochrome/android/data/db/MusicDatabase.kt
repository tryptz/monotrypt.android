package tf.monochrome.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import tf.monochrome.android.data.db.dao.DownloadDao
import tf.monochrome.android.data.db.dao.FavoriteDao
import tf.monochrome.android.data.db.dao.HistoryDao
import tf.monochrome.android.data.db.dao.PlaylistDao
import tf.monochrome.android.data.db.entity.CachedLyricsEntity
import tf.monochrome.android.data.db.entity.DownloadedTrackEntity
import tf.monochrome.android.data.db.entity.FavoriteAlbumEntity
import tf.monochrome.android.data.db.entity.FavoriteArtistEntity
import tf.monochrome.android.data.db.entity.FavoriteTrackEntity
import tf.monochrome.android.data.db.entity.HistoryTrackEntity
import tf.monochrome.android.data.db.entity.PlaylistTrackEntity
import tf.monochrome.android.data.db.entity.UserPlaylistEntity

@Database(
    entities = [
        FavoriteTrackEntity::class,
        FavoriteAlbumEntity::class,
        FavoriteArtistEntity::class,
        HistoryTrackEntity::class,
        UserPlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadedTrackEntity::class,
        CachedLyricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}
