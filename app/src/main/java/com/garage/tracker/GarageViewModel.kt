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

    val brakeEntries: StateFlow<List<BrakeEntry>> = activeVehicle
        .flatMapLatest { v -> v?.let { db.brakeDao().observe(it.vehicleId) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val insurancePlan: StateFlow<RecurringExpense?> = activeVehicle
        .flatMapLatest { v -> v?.let { db.recurringDao().observe(it.vehicleId, Categories.INSURANCE) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val reminders: StateFlow<List<Reminder>> =
        db.reminderDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot message for the snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { viewModelScope.launch { runRecurring() } }

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

    fun updateExpense(log: ExpenseLog) = viewModelScope.launch { db.expenseDao().upsert(log) }

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

    /** Swaps to [target] (e.g. summer to winter): the old set comes off, and the swap is logged with its cost. */
    fun swapTires(target: TireSet, date: String, odometer: Int, cost: Double, note: String) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        mountInternal(v, target, odometer, date, cost, note)
    }

    private suspend fun mountInternal(
        v: Vehicle, set: TireSet, odometer: Int,
        date: String = LocalDate.now().toString(), cost: Double = 0.0, note: String = "",
    ) {
        db.withTransaction {
            db.tireDao().unmountAll(v.vehicleId, odometer)
            db.tireDao().upsertSet(set.copy(mounted = true, mountedAtOdometer = odometer))
            val e = TireEvent(
                newId("TIR"), v.vehicleId, set.setId, TireEventType.SWAP, date, odometer, null,
                note.ifBlank { "Mounted ${set.name}" }, cost,
            )
            db.tireDao().upsertEvent(e)
            mirrorCost(e.eventId, v.vehicleId, date, odometer, cost)
            if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
        }
    }

    /** Adds or edits a tire log entry. A cost also shows up in Expenses under Service. */
    fun saveTireEvent(
        existing: TireEvent?, type: String, date: String, odometer: Int, wheel: String?, note: String,
        cost: Double, fl: Double?, fr: Double?, rl: Double?, rr: Double?,
    ) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        val onCar = tireSets.value.firstOrNull { it.mounted }?.setId
        val e = TireEvent(
            existing?.eventId ?: newId("TIR"), v.vehicleId, existing?.setId ?: onCar,
            type, date, odometer, wheel, note, cost, fl, fr, rl, rr,
        )
        db.withTransaction {
            db.tireDao().upsertEvent(e)
            mirrorCost(e.eventId, v.vehicleId, date, odometer, cost)
        }
        if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
    }

    fun deleteTireEvent(e: TireEvent) = viewModelScope.launch {
        db.withTransaction {
            db.tireDao().deleteEvent(e)
            db.expenseDao().deleteById("EXP_${e.eventId}")
        }
    }

    /** Keeps a matching Service expense in sync with a cost entered on a tire or brake entry. */
    private suspend fun mirrorCost(sourceId: String, vehicleId: String, date: String, odometer: Int, cost: Double) {
        val id = "EXP_$sourceId"
        if (cost > 0) db.expenseDao().upsert(ExpenseLog(id, vehicleId, date, odometer, Categories.SERVICE, cost, 0.0))
        else db.expenseDao().deleteById(id)
    }

    // ---- Brakes --------------------------------------------------------------------------------

    fun saveBrakeEntry(
        existing: BrakeEntry?, type: String, date: String, odometer: Int,
        frontMm: Double?, rearMm: Double?, cost: Double, note: String,
    ) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        val e = BrakeEntry(existing?.entryId ?: newId("BRK"), v.vehicleId, date, odometer, type, frontMm, rearMm, cost, note)
        db.withTransaction {
            db.brakeDao().upsert(e)
            mirrorCost(e.entryId, v.vehicleId, date, odometer, cost)
        }
        if (odometer > v.currentOdometer) db.vehicleDao().setOdometer(v.vehicleId, odometer)
    }

    fun deleteBrakeEntry(e: BrakeEntry) = viewModelScope.launch {
        db.withTransaction {
            db.brakeDao().delete(e)
            db.expenseDao().deleteById("EXP_${e.entryId}")
        }
    }

    // ---- Monthly insurance ---------------------------------------------------------------------

    /** Sets up (or changes) the monthly insurance payment, starting at [firstDate]. */
    fun saveInsurance(amount: Double, firstDate: LocalDate, active: Boolean) = viewModelScope.launch {
        val v = activeVehicle.value ?: return@launch
        db.recurringDao().upsert(
            RecurringExpense("REC_${v.vehicleId}_Insurance", v.vehicleId, Categories.INSURANCE, amount, firstDate.dayOfMonth, firstDate.toString(), active),
        )
        runRecurring()
    }

    fun removeInsurance() = viewModelScope.launch { insurancePlan.value?.let { db.recurringDao().delete(it) } }

    fun refreshRecurring() = viewModelScope.launch { runRecurring() }

    /** Adds every monthly payment that has come due since the app last looked. */
    private suspend fun runRecurring() {
        val today = LocalDate.now()
        val vehicles = db.vehicleDao().getAll().associateBy { it.vehicleId }
        db.recurringDao().allActive().forEach { plan ->
            val odometer = vehicles[plan.vehicleId]?.currentOdometer ?: return@forEach
            var due = runCatching { LocalDate.parse(plan.nextDue) }.getOrNull() ?: return@forEach
            var added = 0
            while (!due.isAfter(today) && added < 600) {
                // Fixed id per month: running this twice can never double-add a payment.
                db.expenseDao().upsert(ExpenseLog("${plan.recurringId}_$due", plan.vehicleId, due.toString(), odometer, plan.category, plan.amount, 0.0))
                val next = java.time.YearMonth.from(due).plusMonths(1)
                due = next.atDay(minOf(plan.dayOfMonth, next.lengthOfMonth()))
                added++
            }
            if (added > 0) db.recurringDao().upsert(plan.copy(nextDue = due.toString()))
        }
    }

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
