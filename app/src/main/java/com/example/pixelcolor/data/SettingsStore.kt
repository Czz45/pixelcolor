package com.example.pixelcolor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    val completedCount: Flow<Int> = context.dataStore.data.map { prefs -> prefs[COMPLETED_COUNT] ?: 0 }
    val dailyStreak: Flow<Int> = context.dataStore.data.map { prefs -> prefs[DAILY_STREAK] ?: 0 }
    val lastDailyDate: Flow<String> = context.dataStore.data.map { prefs -> prefs[LAST_DAILY_DATE] ?: "" }
    val themeIndex: Flow<Int> = context.dataStore.data.map { prefs -> prefs[THEME_INDEX] ?: 0 }
    val totalTimeMs: Flow<Long> = context.dataStore.data.map { prefs -> prefs[TOTAL_TIME_MS] ?: 0L }
    val totalFilledCells: Flow<Long> = context.dataStore.data.map { prefs -> prefs[TOTAL_FILLED_CELLS] ?: 0L }

    suspend fun setThemeIndex(index: Int) {
        context.dataStore.edit { prefs -> prefs[THEME_INDEX] = index }
    }

    suspend fun incrementCompleted() {
        context.dataStore.edit { prefs -> prefs[COMPLETED_COUNT] = (prefs[COMPLETED_COUNT] ?: 0) + 1 }
    }

    suspend fun addPlayStats(timeMs: Long, filledCells: Int) {
        context.dataStore.edit { prefs ->
            prefs[TOTAL_TIME_MS] = (prefs[TOTAL_TIME_MS] ?: 0L) + timeMs
            prefs[TOTAL_FILLED_CELLS] = (prefs[TOTAL_FILLED_CELLS] ?: 0L) + filledCells
        }
    }

    suspend fun updateDailyChallenge(date: String, completed: Boolean) {
        context.dataStore.edit { prefs ->
            if (completed && (prefs[LAST_DAILY_DATE] ?: "") != date)
                prefs[DAILY_STREAK] = (prefs[DAILY_STREAK] ?: 0) + 1
            prefs[LAST_DAILY_DATE] = date
        }
    }

    companion object {
        private val COMPLETED_COUNT = intPreferencesKey("completed_count")
        private val DAILY_STREAK = intPreferencesKey("daily_streak")
        private val LAST_DAILY_DATE = stringPreferencesKey("last_daily_date")
        private val THEME_INDEX = intPreferencesKey("theme_index")
        private val TOTAL_TIME_MS = longPreferencesKey("total_time_ms")
        private val TOTAL_FILLED_CELLS = longPreferencesKey("total_filled_cells")
    }
}
