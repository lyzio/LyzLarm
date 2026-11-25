package se.lyzio.lyzlarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {
    // Nights

    @Insert
    suspend fun insertNight(night: Night): Long

    @Update
    suspend fun updateNight(night: Night)

    @Query("SELECT * FROM nights WHERE id = :id")
    suspend fun night(id: Long): Night?

    @Query("SELECT * FROM nights WHERE id = :id")
    fun observeNight(id: Long): Flow<Night?>

    /** The night being tracked right now, if any. */
    @Query("SELECT * FROM nights WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun openNight(): Night?

    @Query("SELECT * FROM nights WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    fun observeOpenNight(): Flow<Night?>

    /** Finished nights, newest first. */
    @Query("SELECT * FROM nights WHERE end_time IS NOT NULL ORDER BY start_time DESC")
    fun observeFinished(): Flow<List<Night>>

    @Query("SELECT * FROM nights WHERE end_time IS NOT NULL ORDER BY start_time DESC LIMIT 1")
    fun observeLatestFinished(): Flow<Night?>

    /** Finished nights, for the smart-wake learning mode ("night 3 of 7"). */
    @Query("SELECT COUNT(*) FROM nights WHERE end_time IS NOT NULL")
    suspend fun finishedCount(): Int

    @Query("SELECT COUNT(*) FROM nights WHERE end_time IS NOT NULL")
    fun observeFinishedCount(): Flow<Int>

    @Query("SELECT start_time FROM nights WHERE start_time >= :since ORDER BY start_time DESC")
    suspend fun startTimesSince(since: Long): List<Long>

    /** Finished nights shorter than [minMs]: discarded (see Tracker.MIN_NIGHT_MS). */
    @Query("SELECT id FROM nights WHERE end_time IS NOT NULL AND end_time - start_time < :minMs")
    suspend fun shortNightIds(minMs: Long): List<Long>

    // Epochs

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpoch(epoch: EpochRow)

    @Query("SELECT * FROM epochs WHERE night_id = :nightId ORDER BY time")
    suspend fun epochs(nightId: Long): List<EpochRow>

    @Query("SELECT * FROM epochs WHERE night_id = :nightId AND time >= :from ORDER BY time")
    suspend fun epochsSince(nightId: Long, from: Long): List<EpochRow>

    @Query("SELECT time FROM epochs WHERE night_id = :nightId ORDER BY time")
    suspend fun epochTimes(nightId: Long): List<Long>

    @Query("SELECT MAX(time) FROM epochs WHERE night_id = :nightId")
    suspend fun lastEpochTime(nightId: Long): Long?

    /** Changes whenever an epoch is written; used for live screens. */
    @Query("SELECT COUNT(*) FROM epochs WHERE night_id = :nightId")
    fun observeEpochCount(nightId: Long): Flow<Int>

    // Events

    @Insert
    suspend fun insertEvent(event: EventRow): Long

    @Query("SELECT * FROM events WHERE night_id = :nightId ORDER BY time")
    suspend fun events(nightId: Long): List<EventRow>

    @Query("SELECT * FROM events WHERE night_id = :nightId AND type = :type ORDER BY time LIMIT 1")
    suspend fun firstEvent(nightId: Long, type: String): EventRow?

    @Query("SELECT * FROM events WHERE night_id = :nightId ORDER BY time DESC")
    fun observeEvents(nightId: Long): Flow<List<EventRow>>

    // Raw samples

    @Insert
    suspend fun insertRaw(samples: List<RawSample>)

    @Query("SELECT * FROM raw_samples WHERE night_id = :nightId ORDER BY time")
    suspend fun rawSamples(nightId: Long): List<RawSample>

    @Query("SELECT COUNT(*) FROM raw_samples WHERE night_id = :nightId")
    suspend fun rawCount(nightId: Long): Int

    @Query("DELETE FROM raw_samples WHERE time < :before")
    suspend fun deleteRawBefore(before: Long): Int

    // Delete

    @Query("DELETE FROM epochs WHERE night_id = :nightId")
    suspend fun deleteEpochs(nightId: Long)

    @Query("DELETE FROM events WHERE night_id = :nightId")
    suspend fun deleteEvents(nightId: Long)

    @Query("DELETE FROM raw_samples WHERE night_id = :nightId")
    suspend fun deleteRaw(nightId: Long)

    @Query("DELETE FROM nights WHERE id = :nightId")
    suspend fun deleteNightRow(nightId: Long)

    @Transaction
    suspend fun deleteNight(nightId: Long) {
        deleteEpochs(nightId)
        deleteEvents(nightId)
        deleteRaw(nightId)
        deleteNightRow(nightId)
    }
}
