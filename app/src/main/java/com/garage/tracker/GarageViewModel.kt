package com.garage.tracker

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.garage.tracker.data.*
import com.garage.tracker.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel(app: Application) : AndroidViewModel(app) {
    private val db = GarageDatabase.get(app)
    private val scheduler = ReminderScheduler(app)
    private val settingsStore = SettingsStore(app)

    val settings: StateFlow<AppSettings> = settingsStore.state

    private val selectedId = MutableStateFlow<String?>(null)

    /** null until the first database read, so the UI doesn't flash the welcome screen. */
    val vehicles: StateFlow<List<Vehicle>?> =
        db.vehicleDao().observeAll().map<List<Vehicle>, List<Vehicle>?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeVehicle: StateFlow<Vehicle?> =
        combine(vehicles, selectedId) { list, id ->
            list?.let { l -> l.firstOrNull { it.vehicleId == id } ?: l.firstOrNull() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val schedules: StateFlow<List<MaintenanceSchedule>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.scheduleDao().observeFor(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val logs: StateFlow<List<ExpenseLog>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.expenseDao().observeFor(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tireSets: StateFlow<List<TireSet>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.tireDao().observeSets(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tireEvents: StateFlow<List<TireEvent>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.tireDao().observeEvents(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reminders: StateFlow<List<Reminder>> =
        db.reminderDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot message for the snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun say(msg: String) { _message.value = msg }
    fun clearMessage() { _message.value = null }

    // ---- Vehicles ------------------------------------------------------------------------------

    fun select(id: String) { selectedId.value = id }

    fun addVehicle(make: String, model: String, year: Int, odometer: Int, colorHex: String) =
        viewModelScope.launch {
            val v = Vehicle(newId("CAR"), make, model, year, odometer, colorHex)
            db.vehicleDao().upsert(v)
            selectedId.value = v.vehicleId
        }

    fun updateVehicle(v: Vehicle) = viewModelScope.launch { db.vehicleDao().upsert(v) }

    fun deleteVehicle(v: Vehicle) = viewModelScope.launch {
        db.reminderDao().getForVehicle(v.vehicleId).forEach { scheduler.cancel(it) }
        db.reminderDao().deleteForVehicle(v.vehicleId)
        withContext(Dispatchers.IO) { photoFile(v.vehicleId).delete() }
        db.vehicleDao().delete(v) // schedules and expenses go with it (cascade)
        selectedId.value = null
        _photoVersion.value++
    }

    fun setOdometer(value: Int) = viewModelScope.launch {
        activeVehicle.value?.let { db.vehicleDao().setOdometer(it.vehicleId, value) }
    }

    // ---- Maintenance ---------------------------------------------------------------------------

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

    // ---- Expenses ------------------------------------------------------------------------------

    fun addExpense(date: String, odometer: Int, category: String, cost: Double, litres: Double) =
        viewModelScope.launch {
            val v = activeVehicle.value ?: return@launch
            db.expenseDao().upsert(ExpenseLog(newId("LOG"), v.vehicleId, date, odometer, category, cost, litres))
            if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
        }

    /** A quick car-wash entry. It is stored as an expense so it counts toward your totals. */
    fun addWash(date: String, cost: Double) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        db.expenseDao().upsert(ExpenseLog(newId("LOG"), v.vehicleId, date, v.currentOdometer, Categories.CAR_WASH, cost, 0.0))
    }

    fun deleteExpense(log: ExpenseLog) = viewModelScope.launch { db.expenseDao().delete(log) }

    // ---- Tires ---------------------------------------------------------------------------------

    fun addTireSet(name: String, season: String, brand: String, size: String, kmBefore: Int, mountNow: Boolean) =
        viewModelScope.launch {
            val v = activeVehicle.value ?: return@launch
            val set = TireSet(newId("SET"), v.vehicleId, name, season, brand, size, false, 0, kmBefore)
            db.tireDao().upsertSet(set)
            if (mountNow) mountInternal(v, set, v.currentOdometer)
        }

    fun updateTireSet(set: TireSet) = viewModelScope.launch { db.tireDao().upsertSet(set) }

    fun deleteTireSet(set: TireSet) = viewModelScope.launch { db.tireDao().deleteSet(set) }

    /** Puts [set] on the car at [odometer]; whatever was on before is taken off and its km banked. */
    fun mountTireSet(set: TireSet, odometer: Int) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        mountInternal(v, set, odometer)
    }

    private suspend fun mountInternal(v: Vehicle, set: TireSet, odometer: Int) {
        db.withTransaction {
            db.tireDao().unmountAll(v.vehicleId, odometer)
            db.tireDao().upsertSet(set.copy(mounted = true, mountedAtOdometer = odometer))
            db.tireDao().upsertEvent(
                TireEvent(newId("TIR"), v.vehicleId, set.setId, TireEventType.SWAP, LocalDate.now().toString(), odometer, null, "Mounted ${set.name}"),
            )
            if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
        }
    }

    fun addTireEvent(type: String, date: String, odometer: Int, wheel: String?, note: String) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        val onCar = tireSets.value.firstOrNull { it.mounted }?.setId
        db.tireDao().upsertEvent(TireEvent(newId("TIR"), v.vehicleId, onCar, type, date, odometer, wheel, note))
        if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
    }

    fun deleteTireEvent(e: TireEvent) = viewModelScope.launch { db.tireDao().deleteEvent(e) }

    // ---- Reminders -----------------------------------------------------------------------------

    fun addReminder(title: String, note: String, triggerAt: Long) = viewModelScope.launch {
        val r = Reminder(newId("REM"), activeVehicle.value?.vehicleId, title, note, triggerAt)
        db.reminderDao().upsert(r)
        scheduler.schedule(r)
    }

    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        scheduler.cancel(r)
        db.reminderDao().delete(r)
    }

    // ---- Backup --------------------------------------------------------------------------------

    /** Writes the backup JSON to the cache dir and returns the file, ready to be shared. */
    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "carlo-backup-${LocalDate.now()}.json").also { it.writeText(Backup.toJson(db)) }
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

    // ---- Vehicle photos ------------------------------------------------------------------------

    private val _photoVersion = MutableStateFlow(0)
    val photoVersion: StateFlow<Int> = _photoVersion

    fun photoFile(vehicleId: String) =
        File(File(getApplication<Application>().filesDir, "photos"), "$vehicleId.img")

    /** Copies any picture into private storage, fixing its rotation and capping it at ~2000 px. */
    fun importPhoto(uri: Uri) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        _message.value = runCatching {
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "that isn't an image" }
                var sample = 1
                while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2

                var bitmap = resolver.openInputStream(uri)!!.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("couldn't read the image")

                val degrees = runCatching {
                    resolver.openInputStream(uri)!!.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                }.getOrDefault(0f)
                if (degrees != 0f) {
                    bitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true,
                    )
                }

                val target = photoFile(v.vehicleId).also { it.parentFile?.mkdirs() }
                val tmp = File(target.parentFile, "${v.vehicleId}.tmp")
                try {
                    tmp.outputStream().use {
                        bitmap.compress(if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    target.delete()
                    check(tmp.renameTo(target)) { "couldn't save the photo" }
                } finally {
                    tmp.delete()
                }
            }
            _photoVersion.value++
            "Photo updated"
        }.getOrElse { "Couldn't use that picture: ${it.message}" }
    }

    fun removePhoto() = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        withContext(Dispatchers.IO) { photoFile(v.vehicleId).delete() }
        _photoVersion.value++
    }

    // ---- Settings ------------------------------------------------------------------------------

    fun setDueSoonKm(km: Int) = settingsStore.setDueSoonKm(km)
    fun setReminderHour(hour: Int) = settingsStore.setReminderHour(hour)

    private fun newId(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(8)}"
}
