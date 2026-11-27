package se.lyzio.anatole.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The one local database. Schema version history is exported to app/schemas/
 * and kept in the repo; every schema change ships with a tested migration.
 *
 * v1 (v0.1): alarms. v2 (v0.2): nights, epochs, events, raw_samples — new tables only,
 * so Room's automatic migration from the exported v1 schema is enough (see MigrationTest).
 * v3 (v0.3): Snooze lock columns on alarms, noise on epochs, empty_bed_tests, scanned_codes.
 * Added columns have defaults or are nullable, so the automatic migration works here too.
 */
@Database(
    entities = [
        Alarm::class, Night::class, EpochRow::class, EventRow::class, RawSample::class,
        EmptyBedTest::class, ScannedCode::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun nightDao(): NightDao
    abstract fun extrasDao(): ExtrasDao

    companion object {
        const val NAME = "lyzlarm.db"

        fun builder(context: Context, name: String = NAME): Builder<AppDatabase> =
            Room.databaseBuilder(context, AppDatabase::class.java, name)

        fun create(context: Context): AppDatabase = builder(context).build()
    }
}
