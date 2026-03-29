package tf.monochrome.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tf.monochrome.android.data.db.entity.HistoryTrackEntity

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_tracks ORDER BY playedAt DESC LIMIT :limit")
    fun getHistory(limit: Int = 100): Flow<List<HistoryTrackEntity>>

    @Query("SELECT * FROM history_tracks ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getHistorySnapshot(limit: Int = 100): List<HistoryTrackEntity>

    @Query("SELECT COUNT(*) FROM history_tracks")
    fun getHistoryCount(): Flow<Int>

    @Insert
    suspend fun insert(track: HistoryTrackEntity)

    @Query("DELETE FROM history_tracks WHERE id = :trackId")
    suspend fun deleteByTrackId(trackId: Long)

    @Transaction
    suspend fun addToHistory(track: HistoryTrackEntity) {
        deleteByTrackId(track.id)
        insert(track)
    }

    @Query("DELETE FROM history_tracks")
    suspend fun clearHistory()

    // Get most played tracks for radio mode seeding
    @Query("""
        SELECT id, title, duration, artistId, artistName, albumId, albumTitle, albumCover,
               audioQuality, COUNT(*) as playCount, MAX(playedAt) as playedAt
        FROM history_tracks
        GROUP BY id
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getMostPlayed(limit: Int = 50): List<HistoryTrackEntity>
}
