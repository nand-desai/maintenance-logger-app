package com.garage.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY make, model")
    fun observeAll(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles")
    suspend fun getAll(): List<Vehicle>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    /** Emits again whenever anything in the garage changes (used to trigger automatic backups). */
    @Query(
        "SELECT (SELECT COUNT(*) FROM vehicles) + (SELECT COUNT(*) FROM maintenance_schedules) + " +
            "(SELECT COUNT(*) FROM expense_logs) + (SELECT COUNT(*) FROM reminders) + (SELECT COUNT(*) FROM tire_sets) + " +
            "(SELECT COUNT(*) FROM tire_events) + (SELECT COUNT(*) FROM brake_entries) + (SELECT COUNT(*) FROM recurring_expenses) + " +
            "(SELECT COUNT(*) FROM car_notes)",
    )
    fun changeTick(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vehicle: Vehicle)

    @Query("UPDATE vehicles SET currentOdometer = :odometer WHERE vehicleId = :id")
    suspend fun setOdometer(id: String, odometer: Int)

    @Delete
    suspend fun delete(vehicle: Vehicle)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :vehicleId ORDER BY serviceName")
    fun observeFor(vehicleId: String): Flow<List<MaintenanceSchedule>>

    @Query("SELECT * FROM maintenance_schedules")
    suspend fun getAll(): List<MaintenanceSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: MaintenanceSchedule)

    @Delete
    suspend fun delete(schedule: MaintenanceSchedule)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_logs WHERE vehicleId = :vehicleId ORDER BY date DESC, odometer DESC")
    fun observeFor(vehicleId: String): Flow<List<ExpenseLog>>

    @Query("SELECT * FROM expense_logs")
    suspend fun getAll(): List<ExpenseLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ExpenseLog)

    @Query("DELETE FROM expense_logs WHERE logId = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(log: ExpenseLog)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAt")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId")
    suspend fun getForVehicle(vehicleId: String): List<Reminder>

    @Query("DELETE FROM reminders WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
interface TireDao {
    @Query("SELECT * FROM tire_sets WHERE vehicleId = :vehicleId ORDER BY name")
    fun observeSets(vehicleId: String): Flow<List<TireSet>>

    @Query("SELECT * FROM tire_events WHERE vehicleId = :vehicleId ORDER BY date DESC, odometer DESC")
    fun observeEvents(vehicleId: String): Flow<List<TireEvent>>

    @Query("SELECT * FROM tire_sets")
    suspend fun allSets(): List<TireSet>

    @Query("SELECT * FROM tire_events")
    suspend fun allEvents(): List<TireEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: TireSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: TireEvent)

    @Delete
    suspend fun deleteSet(set: TireSet)

    @Delete
    suspend fun deleteEvent(event: TireEvent)

    /** Takes whatever is on the car off, banking the km it just drove. */
    @Query("UPDATE tire_sets SET mounted = 0, kmBefore = kmBefore + MAX(0, :odometer - mountedAtOdometer) WHERE vehicleId = :vehicleId AND mounted = 1")
    suspend fun unmountAll(vehicleId: String, odometer: Int)
}

@Dao
interface BrakeDao {
    @Query("SELECT * FROM brake_entries WHERE vehicleId = :vehicleId ORDER BY date DESC, odometer DESC")
    fun observe(vehicleId: String): Flow<List<BrakeEntry>>

    @Query("SELECT * FROM brake_entries")
    suspend fun all(): List<BrakeEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BrakeEntry)

    @Delete
    suspend fun delete(entry: BrakeEntry)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_expenses WHERE vehicleId = :vehicleId AND category = :category LIMIT 1")
    fun observe(vehicleId: String, category: String): Flow<RecurringExpense?>

    @Query("SELECT * FROM recurring_expenses WHERE active = 1")
    suspend fun allActive(): List<RecurringExpense>

    @Query("SELECT * FROM recurring_expenses")
    suspend fun all(): List<RecurringExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: RecurringExpense)

    @Delete
    suspend fun delete(plan: RecurringExpense)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM car_notes WHERE vehicleId = :vehicleId ORDER BY date DESC, odometer DESC")
    fun observe(vehicleId: String): Flow<List<CarNote>>

    @Query("SELECT * FROM car_notes")
    suspend fun all(): List<CarNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: CarNote)

    @Delete
    suspend fun delete(note: CarNote)
}
