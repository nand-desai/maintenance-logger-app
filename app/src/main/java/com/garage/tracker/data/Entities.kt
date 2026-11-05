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
    val intervalMiles: Int,
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
    val fuelGallons: Double = 0.0,
)

object Categories {
    const val FUEL = "Fuel"
    const val SERVICE = "Service"
    const val INSURANCE = "Insurance"
    const val UPGRADES = "Upgrades"
    val all = listOf(FUEL, SERVICE, INSURANCE, UPGRADES)
}
