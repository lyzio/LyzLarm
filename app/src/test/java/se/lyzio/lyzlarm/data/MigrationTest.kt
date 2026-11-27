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
 * Builds a database exactly as an older version left it (schema v1 or v2, from
 * app/schemas/…/N.json), then opens it with the current app database. Room runs the migration and checks that the
 * result matches the v2 schema; then we check the alarms survived and the new tables work.
 */
@RunWith(AndroidJUnit4::class)
// Plain Application: the test must not start AnatoleApp's background work.
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

    @Test fun v2ToV3KeepsAlarmsAndNights() = runBlocking {
        createV2Database()

        val db = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        try {
            val alarms = db.alarmDao().getAll().sortedBy { it.id }
            assertEquals(2, alarms.size)
            // Existing alarms keep their settings and get the v0.3 defaults: no Snooze lock.
            assertEquals(Alarm(id = 1, hour = 7, minute = 0, repeatDays = 0b0011111), alarms[0])
            assertEquals(false, alarms[0].snoozeLock)
            assertEquals(Challenges.DEFAULT, alarms[0].challengeTypes)
            assertEquals(1, alarms[0].mathDifficulty)
            assertEquals(false, alarms[0].stayAwakeCheck)
            assertEquals(0, alarms[0].windowMinutes)
            assertEquals(30, alarms[1].windowMinutes)

            val nights = db.nightDao()
            val night = nights.night(5)!!
            assertEquals("FIXED", night.wakeReason)
            assertEquals(2, night.engineVersion)
            assertEquals(85, night.batteryStart)
            val epochs = nights.epochs(5)
            assertEquals(2, epochs.size)
            assertEquals(3.5f, epochs[1].movement, 0f)
            assertNull(epochs[1].noise)
            assertEquals(2, nights.events(5).size)
            assertEquals(1, nights.finishedCount())

            // New columns and tables are usable.
            nights.insertEpoch(EpochRow(5, 60_000L, 0f, noise = 0.004f))
            assertEquals(0.004f, nights.epochsSince(5, 60_000L).single().noise!!, 1e-6f)
            val extras = db.extrasDao()
            assertNull(extras.latestEmptyBedTest())
            extras.insertEmptyBedTest(EmptyBedTest(startTime = 1L, endTime = 2L, noiseP50 = 0.003f, noiseP90 = 0.0035f, movementMax = 0f, epochCount = 17, epochs = "0,0,0.003"))
            assertEquals(17, extras.latestEmptyBedTest()?.epochCount)
            extras.insertScannedCode(ScannedCode("abc", 100L))
            assertEquals(100L, extras.scannedAt("abc"))
            assertEquals(1, extras.deleteScannedBefore(200L))
            assertNull(extras.scannedAt("abc"))
        } finally {
            db.close()
        }
    }

    /** The v2 schema and identity hash, copied from app/schemas/se.lyzio.lyzlarm.data.AppDatabase/2.json. */
    private fun createV2Database() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        listOf(
            "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `window_minutes` INTEGER NOT NULL, `repeat_days` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `sound_uri` TEXT, `gradual_volume` INTEGER NOT NULL, `fade_minutes` INTEGER NOT NULL, `vibration` INTEGER NOT NULL, `vibrate_first` INTEGER NOT NULL, `snooze_minutes` INTEGER NOT NULL, `sound_tracking_mode` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `nights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alarm_id` INTEGER, `start_time` INTEGER NOT NULL, `end_time` INTEGER, `wake_time` INTEGER, `wake_reason` TEXT, `battery_start` INTEGER, `battery_end` INTEGER, `engine_version` INTEGER NOT NULL, `time_zone` TEXT NOT NULL, `onset_estimate` INTEGER, `rating` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_nights_start_time` ON `nights` (`start_time`)",
            "CREATE TABLE IF NOT EXISTS `epochs` (`night_id` INTEGER NOT NULL, `time` INTEGER NOT NULL, `movement` REAL NOT NULL, `sound_level` REAL, `sound_events` INTEGER, PRIMARY KEY(`night_id`, `time`))",
            "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `night_id` INTEGER NOT NULL, `time` INTEGER NOT NULL, `type` TEXT NOT NULL, `detail` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_events_night_id` ON `events` (`night_id`)",
            "CREATE TABLE IF NOT EXISTS `raw_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `night_id` INTEGER NOT NULL, `time` INTEGER NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `z` REAL NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_raw_samples_night_id` ON `raw_samples` (`night_id`)",
            "CREATE INDEX IF NOT EXISTS `index_raw_samples_time` ON `raw_samples` (`time`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ca5b24fce47997d4bf0265f5b344ad28')",
            "INSERT INTO alarms VALUES (1, 7, 0, 0, 31, 1, NULL, 1, 3, 1, 1, 9, 0)",
            "INSERT INTO alarms VALUES (2, 6, 30, 30, 0, 0, 'content://media/alarm/12', 0, 3, 1, 0, 5, 0)",
            "INSERT INTO nights VALUES (5, 1, 1000, 30000000, 30000000, 'FIXED', 85, 80, 2, 'Europe/Stockholm', NULL, NULL)",
            "INSERT INTO epochs VALUES (5, 0, 0.0, NULL, NULL)",
            "INSERT INTO epochs VALUES (5, 30000, 3.5, NULL, NULL)",
            "INSERT INTO events (night_id, time, type, detail) VALUES (5, 1000, 'tracking_started', '85')",
            "INSERT INTO events (night_id, time, type, detail) VALUES (5, 30000000, 'rang', NULL)",
        ).forEach(db::execSQL)
        db.version = 2
        db.close()
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
