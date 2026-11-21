package se.lyzio.lyzlarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The one local database. Schema version history is exported to app/schemas/
 * and kept in the repo; every future schema change ships with a tested migration.
 */
@Database(entities = [Alarm::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lyzlarm.db").build()
    }
}
