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
