package se.lyzio.lyzlarm.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as v0.1 left it (schema v1, from app/schemas/…/1.json),
 * then opens it with the v0.2 app database. Room runs the migration and checks that the
 * result matches the v2 schema; then we check the alarms survived and the new tables work.
 */
@RunWith(AndroidJUnit4::class)
// Plain Application: the test must not start LyzLarmApp's background work.
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before fun clean() {
        context.deleteDatabase(name)
    }

    @After fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test fun v1ToV2KeepsAlarms() = runBlocking {
        createV1Database()

        val db = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        try {
            val alarms = db.alarmDao().getAll().sortedBy { it.id }
            assertEquals(2, alarms.size)
            assertEquals(Alarm(id = 1, hour = 7, minute = 0, repeatDays = 0b0011111), alarms[0])
            assertEquals(6, alarms[1].hour)
            assertEquals(30, alarms[1].minute)
            assertEquals(false, alarms[1].enabled)
            assertEquals("content://media/alarm/12", alarms[1].soundUri)
            assertEquals(5, alarms[1].snoozeMinutes)

            val tables = db.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            assertTrue(tables.containsAll(listOf("alarms", "nights", "epochs", "events", "raw_samples")))

            // New tables are usable.
            val nights = db.nightDao()
            assertNull(nights.openNight())
            val id = nights.insertNight(
                Night(alarmId = 1, startTime = 1_000L, engineVersion = 2, timeZone = "Europe/Stockholm", batteryStart = 80),
            )
            nights.insertEpoch(EpochRow(id, 0L, 1.5f))
            nights.insertEvent(EventRow(nightId = id, time = 1_000L, type = EventType.TRACKING_STARTED, detail = "80"))
            nights.insertRaw(listOf(RawSample(nightId = id, time = 1_000L, x = 0f, y = 0f, z = 9.8f)))
            assertEquals(id, nights.openNight()?.id)
            assertEquals(1, nights.epochs(id).size)
            assertEquals(1, nights.rawCount(id))

            nights.deleteNight(id)
            assertNull(nights.night(id))
            assertEquals(0, nights.epochs(id).size)
            assertEquals(0, nights.events(id).size)
            assertEquals(0, nights.rawCount(id))
        } finally {
            db.close()
        }
    }

    /** The v1 schema and identity hash, copied from app/schemas/se.lyzio.lyzlarm.data.AppDatabase/1.json. */
    private fun createV1Database() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `window_minutes` INTEGER NOT NULL, " +
                "`repeat_days` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `sound_uri` TEXT, " +
                "`gradual_volume` INTEGER NOT NULL, `fade_minutes` INTEGER NOT NULL, `vibration` INTEGER NOT NULL, " +
                "`vibrate_first` INTEGER NOT NULL, `snooze_minutes` INTEGER NOT NULL, `sound_tracking_mode` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bd965e86638aebef75cd7785f66029be')")
        db.execSQL(
            "INSERT INTO alarms VALUES (1, 7, 0, 0, 31, 1, NULL, 1, 3, 1, 1, 9, 0)",
        )
        db.execSQL(
            "INSERT INTO alarms VALUES (2, 6, 30, 30, 0, 0, 'content://media/alarm/12', 0, 3, 1, 0, 5, 0)",
        )
        db.version = 1
        db.close()
    }
}
