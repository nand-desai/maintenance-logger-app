package com.garage.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** A service turns amber when it is due within this many km. */
    val dueSoonKm: Int = DUE_SOON_KM,
    /** Hour of day (0-23) that new reminders start at. */
    val reminderHour: Int = 9,
)

/** App-wide preferences, kept in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(
            dueSoonKm = prefs.getInt("due_soon_km", DUE_SOON_KM),
            reminderHour = prefs.getInt("reminder_hour", 9),
        ),
    )
    val state: StateFlow<AppSettings> = _state

    fun setDueSoonKm(km: Int) {
        prefs.edit().putInt("due_soon_km", km).apply()
        _state.value = _state.value.copy(dueSoonKm = km)
    }

    fun setReminderHour(hour: Int) {
        prefs.edit().putInt("reminder_hour", hour).apply()
        _state.value = _state.value.copy(reminderHour = hour)
    }
}
