package com.garage.tracker.data

import androidx.room.ColumnInfo
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
    /** What an "Other" expense was for, e.g. "Remote starter". */
    @ColumnInfo(defaultValue = "''") val note: String = "",
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
    @ColumnInfo(defaultValue = "0") val cost: Double = 0.0,
    /** Tread depth in mm per wheel, for "Tread" measurements. */
    val treadFl: Double? = null,
    val treadFr: Double? = null,
    val treadRl: Double? = null,
    val treadRr: Double? = null,
)

@Entity(
    tableName = "brake_entries",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class BrakeEntry(
    @PrimaryKey val entryId: String,
    val vehicleId: String,
    val date: String,
    val odometer: Int,
    /** "Measured" or "Replaced". */
    val type: String,
    /** Pad thickness in mm, front and rear axle. */
    val frontMm: Double?,
    val rearMm: Double?,
    @ColumnInfo(defaultValue = "0") val cost: Double = 0.0,
    val note: String = "",
)

/** A fixed amount added to the expenses every month (e.g. insurance). */
@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class RecurringExpense(
    @PrimaryKey val recurringId: String,
    val vehicleId: String,
    val category: String,
    val amount: Double,
    val dayOfMonth: Int,
    /** ISO date of the next payment that has not been added yet. */
    val nextDue: String,
    val active: Boolean,
)

/** Something to mention to the mechanic: a noise, a vibration, a warning light... */
@Entity(
    tableName = "car_notes",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class CarNote(
    @PrimaryKey val noteId: String,
    val vehicleId: String,
    val date: String,
    val odometer: Int,
    val tag: String,
    val text: String,
    /** True once it has been mentioned to the mechanic. */
    val done: Boolean,
)

/** One completed service: when, at what odometer, what it cost and any notes. */
@Entity(
    tableName = "maintenance_records",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")],
)
data class MaintenanceRecord(
    @PrimaryKey val recordId: String,
    val vehicleId: String,
    val scheduleId: String,
    val serviceName: String,
    val date: String,
    val odometer: Int,
    val cost: Double,
    val note: String,
)

/** A receipt or other file attached to a record; the bytes live in files/attachments/<attachmentId>. */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(Vehicle::class, ["vehicleId"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("ownerId")],
)
data class Attachment(
    @PrimaryKey val attachmentId: String,
    val vehicleId: String,
    /** maintenance, tire, brake or expense */
    val ownerType: String,
    /** The record / event / entry / expense id it belongs to. */
    val ownerId: String,
    val fileName: String,
    val mime: String,
    val size: Long,
    val createdAt: Long,
)

object NoteTags {
    const val GENERAL = "General"
    val all = listOf("Noise", "Vibration", "Leak", "Smell", "Warning light", "Handling", GENERAL)
}

object Categories {
    const val FUEL = "Fuel"
    const val SERVICE = "Service"
    const val INSURANCE = "Insurance"
    const val UPGRADES = "Upgrades"
    const val CAR_WASH = "Car wash"
    const val OTHER = "Other"
    val all = listOf(FUEL, SERVICE, INSURANCE, UPGRADES, CAR_WASH, OTHER)
}
