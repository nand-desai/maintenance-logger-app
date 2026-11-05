package com.garage.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garage.tracker.data.*
import com.garage.tracker.sync.GoogleSheetsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val db = GarageDatabase.get(app)
    val sheets = GoogleSheetsManager(app)

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

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

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

    fun sync() = viewModelScope.launch {
        _syncStatus.value = "Syncing…"
        _syncStatus.value = sheets.sync().fold(
            onSuccess = { "Synced with Google Sheets" },
            onFailure = { "Sync failed: ${it.message}" },
        )
    }

    fun reportSyncStatus(msg: String) { _syncStatus.value = msg }
    fun clearSyncStatus() { _syncStatus.value = null }

    private suspend fun seedSample() {
        val v = Vehicle("CAR_001", "Volkswagen", "Golf TSI", 2016, 58400, "#3B82F6")
        db.vehicleDao().upsert(v)
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_001", v.vehicleId, "Engine Oil & Filter (5W-40)", 5000, 54000))
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_002", v.vehicleId, "Tire Rotation", 6000, 52000))
        db.scheduleDao().upsert(MaintenanceSchedule("SCH_003", v.vehicleId, "Brake Fluid", 20000, 40000))
    }

    private fun newId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}"
}
