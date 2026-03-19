package tf.monochrome.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tf.monochrome.android.data.db.entity.CachedLyricsEntity
import tf.monochrome.android.data.db.entity.DownloadedTrackEntity

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :trackId")
    suspend fun getDownloadedTrack(trackId: Long): DownloadedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :trackId)")
    suspend fun isDownloaded(trackId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :trackId)")
    fun isDownloadedFlow(trackId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteDownloadedTrack(trackId: Long)

    @Query("SELECT SUM(sizeBytes) FROM downloaded_tracks")
    fun getTotalDownloadSize(): Flow<Long?>

    // Lyrics cache
    @Query("SELECT * FROM cached_lyrics WHERE trackId = :trackId")
    suspend fun getCachedLyrics(trackId: Long): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedLyrics(lyrics: CachedLyricsEntity)

    @Query("DELETE FROM cached_lyrics WHERE cachedAt < :beforeTimestamp")
    suspend fun clearOldLyricsCache(beforeTimestamp: Long)
}
