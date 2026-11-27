package se.lyzio.lyzlarm.data

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
 */
@Database(
    entities = [Alarm::class, Night::class, EpochRow::class, EventRow::class, RawSample::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun nightDao(): NightDao

    companion object {
        const val NAME = "lyzlarm.db"

        fun builder(context: Context, name: String = NAME): Builder<AppDatabase> =
            Room.databaseBuilder(context, AppDatabase::class.java, name)

        fun create(context: Context): AppDatabase = builder(context).build()
    }
}
