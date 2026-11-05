package com.garage.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Vehicle::class, MaintenanceSchedule::class, ExpenseLog::class, Reminder::class],
    version = 2,
    exportSchema = false,
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var instance: GarageDatabase? = null

        fun get(context: Context): GarageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, GarageDatabase::class.java, "garage.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
