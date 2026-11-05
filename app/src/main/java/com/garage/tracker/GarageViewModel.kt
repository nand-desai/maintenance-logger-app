package com.garage.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garage.tracker.data.*
import com.garage.tracker.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val db = GarageDatabase.get(app)
    private val scheduler = ReminderScheduler(app)

    private val selectedId = MutableStateFlow<String?>(null)

    val vehicles: StateFlow<List<Vehicle>> =
        db.vehicleDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeVehicle: StateFlow<Vehicle?> =
        combine(vehicles, selectedId) { list, id -> list.firstOrNull { it.vehicleId == id } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val schedules: StateFlow<List<MaintenanceSchedule>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.scheduleDao().observeFor(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val logs: StateFlow<List<ExpenseLog>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.expenseDao().observeFor(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reminders: StateFlow<List<Reminder>> =
        db.reminderDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot message for the snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            if (db.vehicleDao().count() == 0) seedSample()
        }
    }

    fun select(id: String) { selectedId.value = id }

    fun addVehicle(make: String, model: String, year: Int, odometer: Int, colorHex: String) =
        viewModelScope.launch {
            val v = Vehicle(newId("CAR"), make, model, year, odometer, colorHex)
            db.vehicleDao().upsert(v)
            selectedId.value = v.vehicleId
        }

    fun bumpOdometer(delta: Int) = viewModelScope.launch {
        activeVehicle.value?.let { db.vehicleDao().setOdometer(it.vehicleId, it.currentOdometer + delta) }
    }

    fun setOdometer(value: Int) = viewModelScope.launch {
        activeVehicle.value?.let { db.vehicleDao().setOdometer(it.vehicleId, value) }
    }

    fun addSchedule(name: String, interval: Int, lastDone: Int) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        db.scheduleDao().upsert(MaintenanceSchedule(newId("SCH"), v.vehicleId, name, interval, lastDone))
    }

    fun markDone(s: MaintenanceSchedule) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        db.scheduleDao().upsert(s.copy(lastDoneOdometer = v.currentOdometer))
    }

    fun updateSchedule(s: MaintenanceSchedule) = viewModelScope.launch { db.scheduleDao().upsert(s) }
    fun deleteSchedule(s: MaintenanceSchedule) = viewModelScope.launch { db.scheduleDao().delete(s) }

    fun addExpense(date: String, odometer: Int, category: String, cost: Double, gallons: Double) =
        viewModelScope.launch {
            val v = activeVehicle.value ?: return@launch
            db.expenseDao().upsert(ExpenseLog(newId("LOG"), v.vehicleId, date, odometer, category, cost, gallons))
            if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
        }

    fun deleteExpense(log: ExpenseLog) = viewModelScope.launch { db.expenseDao().delete(log) }

    fun addReminder(title: String, note: String, triggerAt: Long) = viewModelScope.launch {
        val r = Reminder(newId("REM"), activeVehicle.value?.vehicleId, title, note, triggerAt)
        db.reminderDao().upsert(r)
        scheduler.schedule(r)
    }

    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        scheduler.cancel(r)
        db.reminderDao().delete(r)
    }

    /** Writes the backup JSON to the cache dir and returns the file, ready to be shared. */
    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "pitstop-backup-${LocalDate.now()}.json").also { it.writeText(Backup.toJson(db)) }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val counts = Backup.restore(db, text)
            scheduler.scheduleAll(db.reminderDao().getAll())
            "Imported $counts"
        }.getOrElse { "Import failed: ${it.message ?: "unreadable file"}" }
    }

    fun say(msg: String) { _message.value = msg }
    fun clearMessage() { _message.value = null }

    private suspend fun seedSample() {
        val v = Vehicle("CAR_001", "Volkswagen", "Golf TSI", 2016, 58400, "#3B82F6")
        db.vehicleDao().upsert(v)
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_001", v.vehicleId, "Engine Oil & Filter (5W-40)", 5000, 54000))
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_002", v.vehicleId, "Tire Rotation", 6000, 52000))
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_003", v.vehicleId, "Brake Fluid", 20000, 40000))
    }

    private fun newId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}"
}
