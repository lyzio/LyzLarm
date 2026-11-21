package se.lyzio.lyzlarm.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App settings live in DataStore, not in the database (design doc). */
class Settings(private val context: Context) {
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val testAlarmPassedKey = longPreferencesKey("test_alarm_passed_at")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[onboardingDoneKey] ?: false }

    /** Epoch millis of the last test alarm that rang and was dismissed; 0 = never. */
    val testAlarmPassedAt: Flow<Long> = context.dataStore.data.map { it[testAlarmPassedKey] ?: 0L }

    suspend fun isOnboardingDone(): Boolean = onboardingDone.first()

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[onboardingDoneKey] = true }
    }

    suspend fun markTestAlarmPassed(at: Long) {
        context.dataStore.edit { it[testAlarmPassedKey] = at }
    }
}
