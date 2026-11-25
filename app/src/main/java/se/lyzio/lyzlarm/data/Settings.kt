package se.lyzio.lyzlarm.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import se.lyzio.lyzlarm.engine.Sensitivity

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Tracking-related settings (Settings › Tracking). Times are minutes after midnight. */
data class TrackingSettings(
    val reminderOn: Boolean = true,
    /** null = automatic (learned usual start, until learned alarm − 9 h). */
    val reminderFixedMinute: Int? = null,
    val warningOn: Boolean = true,
    val warningThreshold: Int = 40,
    /** null = automatic (1 h before the reminder time). */
    val warningFixedMinute: Int? = null,
    val rawOn: Boolean = false,
)

/** Settings › Alarm defaults (v0.3): used for new alarms; sensitivity applies to all alarms. */
data class AlarmDefaults(
    /** Wake window for new alarms; 0 = fixed time. */
    val windowMinutes: Int = 30,
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val snoozeMinutes: Int = 9,
)

/** App settings live in DataStore, not in the database (design doc). */
class Settings(private val context: Context) {
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val testAlarmPassedKey = longPreferencesKey("test_alarm_passed_at")

    private val reminderOnKey = booleanPreferencesKey("reminder_on")
    private val reminderFixedKey = intPreferencesKey("reminder_fixed_minute")
    private val warningOnKey = booleanPreferencesKey("battery_warning_on")
    private val warningThresholdKey = intPreferencesKey("battery_warning_threshold")
    private val warningFixedKey = intPreferencesKey("battery_warning_fixed_minute")
    private val rawOnKey = booleanPreferencesKey("raw_samples_on")
    private val defaultWindowKey = intPreferencesKey("default_window_minutes")
    private val sensitivityKey = stringPreferencesKey("wake_sensitivity")
    private val defaultSnoozeKey = intPreferencesKey("default_snooze_minutes")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    /** Epoch millis of the last test alarm that rang and was dismissed; 0 = never. */
    val testAlarmPassedAt: Flow<Long> = context.dataStore.data.map { it[testAlarmPassedKey] ?: 0L }

    val tracking: Flow<TrackingSettings> = context.dataStore.data.map(::toTracking)

    suspend fun tracking(): TrackingSettings = tracking.first()

    val alarmDefaults: Flow<AlarmDefaults> = context.dataStore.data.map {
        AlarmDefaults(
            windowMinutes = it[defaultWindowKey] ?: 30,
            sensitivity = Sensitivity.parse(it[sensitivityKey]),
            snoozeMinutes = it[defaultSnoozeKey] ?: 9,
        )
    }

    suspend fun alarmDefaults(): AlarmDefaults = alarmDefaults.first()

    suspend fun setDefaultWindow(minutes: Int) = context.dataStore.edit { it[defaultWindowKey] = minutes }

    suspend fun setSensitivity(s: Sensitivity) = context.dataStore.edit { it[sensitivityKey] = s.name }

    suspend fun setDefaultSnooze(minutes: Int) = context.dataStore.edit { it[defaultSnoozeKey] = minutes }

    suspend fun isOnboardingDone(): Boolean = onboardingDone.first()

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[onboardingDoneKey] = true }
    }

    suspend fun markTestAlarmPassed(at: Long) {
        context.dataStore.edit { it[testAlarmPassedKey] = at }
    }

    suspend fun setReminderOn(on: Boolean) = context.dataStore.edit { it[reminderOnKey] = on }

    /** null = automatic. */
    suspend fun setReminderFixedMinute(minute: Int?) = context.dataStore.edit {
        if (minute == null) {
            it.remove(reminderFixedKey)
        } else {
            it[reminderFixedKey] = minute
        }
    }

    suspend fun setWarningOn(on: Boolean) = context.dataStore.edit { it[warningOnKey] = on }

    suspend fun setWarningThreshold(percent: Int) = context.dataStore.edit { it[warningThresholdKey] = percent }

    /** null = automatic. */
    suspend fun setWarningFixedMinute(minute: Int?) = context.dataStore.edit {
        if (minute == null) {
            it.remove(warningFixedKey)
        } else {
            it[warningFixedKey] = minute
        }
    }

    suspend fun setRawOn(on: Boolean) = context.dataStore.edit { it[rawOnKey] = on }

    private fun toTracking(p: Preferences) = TrackingSettings(
        reminderOn = p[reminderOnKey] ?: true,
        reminderFixedMinute = p[reminderFixedKey],
        warningOn = p[warningOnKey] ?: true,
        warningThreshold = p[warningThresholdKey] ?: 40,
        warningFixedMinute = p[warningFixedKey],
        rawOn = p[rawOnKey] ?: false,
    )
}
