package com.garage.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Vehicle::class, MaintenanceSchedule::class, ExpenseLog::class, Reminder::class,
        TireSet::class, TireEvent::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tireDao(): TireDao

    companion object {
        @Volatile private var instance: GarageDatabase? = null

        fun get(context: Context): GarageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, GarageDatabase::class.java, "garage.db",
            ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

/** Adds the tire tables without touching existing data. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tire_sets` (`setId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `season` TEXT NOT NULL, `brand` TEXT NOT NULL, `size` TEXT NOT NULL, " +
                "`mounted` INTEGER NOT NULL, `mountedAtOdometer` INTEGER NOT NULL, `kmBefore` INTEGER NOT NULL, " +
                "PRIMARY KEY(`setId`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tire_sets_vehicleId` ON `tire_sets` (`vehicleId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tire_events` (`eventId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`setId` TEXT, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `odometer` INTEGER NOT NULL, " +
                "`wheel` TEXT, `note` TEXT NOT NULL, PRIMARY KEY(`eventId`), " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tire_events_vehicleId` ON `tire_events` (`vehicleId`)")
    }
}
