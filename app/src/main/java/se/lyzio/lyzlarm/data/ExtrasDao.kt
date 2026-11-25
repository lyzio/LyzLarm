package se.lyzio.lyzlarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Empty bed tests and Snooze lock barcode fingerprints. */
@Dao
interface ExtrasDao {
    @Insert
    suspend fun insertEmptyBedTest(test: EmptyBedTest): Long

    @Query("SELECT * FROM empty_bed_tests ORDER BY start_time DESC LIMIT 1")
    suspend fun latestEmptyBedTest(): EmptyBedTest?

    @Query("SELECT * FROM empty_bed_tests ORDER BY start_time DESC LIMIT 1")
    fun observeLatestEmptyBedTest(): Flow<EmptyBedTest?>

    @Query("SELECT time FROM scanned_codes WHERE hash = :hash")
    suspend fun scannedAt(hash: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedCode(code: ScannedCode)

    @Query("DELETE FROM scanned_codes WHERE time < :before")
    suspend fun deleteScannedBefore(before: Long): Int
}
