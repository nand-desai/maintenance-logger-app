package com.garage.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    /** A service turns amber when it is due within this many km. */
    val dueSoonKm: Int = DUE_SOON_KM,
    /** Hour of day (0-23) that new reminders start at. */
    val reminderHour: Int = 9,
    /** "system", "light" or "dark". */
    val themeMode: String = "system",
    /** Colours the user saved from the colour picker, as #RRGGBB. */
    val savedColors: List<String> = emptyList(),
    /** The folder (a SAF tree URI) that automatic backups are written to. */
    val backupUri: String? = null,
    val lastBackup: Long = 0L,
)

/** App-wide preferences, kept in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(
            dueSoonKm = prefs.getInt("due_soon_km", DUE_SOON_KM),
            reminderHour = prefs.getInt("reminder_hour", 9),
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            savedColors = prefs.getString("saved_colors", "")?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            backupUri = prefs.getString("backup_uri", null),
            lastBackup = prefs.getLong("last_backup", 0L),
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

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun addSavedColor(hex: String) {
        val colors = (_state.value.savedColors + hex.uppercase()).distinct()
        prefs.edit().putString("saved_colors", colors.joinToString(",")).apply()
        _state.value = _state.value.copy(savedColors = colors)
    }

    fun removeSavedColor(hex: String) {
        val colors = _state.value.savedColors.filterNot { it.equals(hex, true) }
        prefs.edit().putString("saved_colors", colors.joinToString(",")).apply()
        _state.value = _state.value.copy(savedColors = colors)
    }

    fun setBackupUri(uri: String?) {
        prefs.edit().apply { if (uri == null) remove("backup_uri") else putString("backup_uri", uri) }.apply()
        _state.value = _state.value.copy(backupUri = uri)
    }

    fun setLastBackup(millis: Long) {
        prefs.edit().putLong("last_backup", millis).apply()
        _state.value = _state.value.copy(lastBackup = millis)
    }

    /** The settings that travel inside a backup file. */
    fun toJson(): JSONObject = JSONObject()
        .put("due_soon_km", _state.value.dueSoonKm)
        .put("reminder_hour", _state.value.reminderHour)
        .put("theme", _state.value.themeMode)
        .put("saved_colors", JSONArray(_state.value.savedColors))

    /** Applies the settings found in a backup (missing values are left alone). */
    fun applyBackup(obj: JSONObject?) {
        if (obj == null) return
        if (obj.has("due_soon_km")) setDueSoonKm(obj.getInt("due_soon_km"))
        if (obj.has("reminder_hour")) setReminderHour(obj.getInt("reminder_hour"))
        if (obj.has("theme")) setThemeMode(obj.getString("theme"))
        obj.optJSONArray("saved_colors")?.let { arr -> for (i in 0 until arr.length()) addSavedColor(arr.getString(i)) }
    }
}
