package com.garage.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val vehicleId: String,
    val make: String,
    val model: String,
    val year: Int,
    val currentOdometer: Int,
    val colorHex: String = "#3B82F6",
)

@Entity(
    tableName = "maintenance_schedules",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class MaintenanceSchedule(
    @PrimaryKey val scheduleId: String,
    val vehicleId: String,
    val serviceName: String,
    val intervalKm: Int,
    val lastDoneOdometer: Int,
)

@Entity(
    tableName = "expense_logs",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class ExpenseLog(
    @PrimaryKey val logId: String,
    val vehicleId: String,
    val date: String,
    val odometer: Int,
    val category: String,
    val cost: Double,
    val fuelLiters: Double = 0.0,
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val reminderId: String,
    val vehicleId: String?,
    val title: String,
    val note: String,
    /** Epoch millis at which the notification should fire. */
    val triggerAt: Long,
)

@Entity(
    tableName = "tire_sets",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class TireSet(
    @PrimaryKey val setId: String,
    val vehicleId: String,
    val name: String,
    /** Summer, Winter or All-season. */
    val season: String,
    val brand: String,
    /** e.g. 225/45 R17 */
    val size: String,
    /** True for the one set currently on the car. */
    val mounted: Boolean,
    /** Car odometer when this set was last put on (only meaningful while mounted). */
    val mountedAtOdometer: Int,
    /** Km already driven on this set before the current mounting. */
    val kmBefore: Int,
)

@Entity(
    tableName = "tire_events",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class TireEvent(
    @PrimaryKey val eventId: String,
    val vehicleId: String,
    /** The set that was on the car when this happened. */
    val setId: String?,
    /** Rotation, Balancing, Alignment, Puncture or Swap. */
    val type: String,
    val date: String,
    val odometer: Int,
    /** FL, FR, RL or RR for punctures. */
    val wheel: String?,
    val note: String,
)

object Categories {
    const val FUEL = "Fuel"
    const val SERVICE = "Service"
    const val INSURANCE = "Insurance"
    const val UPGRADES = "Upgrades"
    val all = listOf(FUEL, SERVICE, INSURANCE, UPGRADES)
}
