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
        TireSet::class, TireEvent::class, BrakeEntry::class, RecurringExpense::class, CarNote::class, MaintenanceRecord::class, Attachment::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tireDao(): TireDao
    abstract fun brakeDao(): BrakeDao
    abstract fun recurringDao(): RecurringDao
    abstract fun noteDao(): NoteDao
    abstract fun recordDao(): RecordDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile private var instance: GarageDatabase? = null

        fun get(context: Context): GarageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, GarageDatabase::class.java, "garage.db",
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).fallbackToDestructiveMigration().build().also { instance = it }
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

/** Tire cost + tread depth, brake pad log, and monthly recurring expenses. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tire_events` ADD COLUMN `cost` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tire_events` ADD COLUMN `treadFl` REAL")
        db.execSQL("ALTER TABLE `tire_events` ADD COLUMN `treadFr` REAL")
        db.execSQL("ALTER TABLE `tire_events` ADD COLUMN `treadRl` REAL")
        db.execSQL("ALTER TABLE `tire_events` ADD COLUMN `treadRr` REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `brake_entries` (`entryId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `odometer` INTEGER NOT NULL, `type` TEXT NOT NULL, `frontMm` REAL, `rearMm` REAL, " +
                "`cost` REAL NOT NULL DEFAULT 0, `note` TEXT NOT NULL, PRIMARY KEY(`entryId`), " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_brake_entries_vehicleId` ON `brake_entries` (`vehicleId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_expenses` (`recurringId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `amount` REAL NOT NULL, `dayOfMonth` INTEGER NOT NULL, `nextDue` TEXT NOT NULL, " +
                "`active` INTEGER NOT NULL, PRIMARY KEY(`recurringId`), " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_vehicleId` ON `recurring_expenses` (`vehicleId`)")
    }
}

/** Lets an "Other" expense say what it was for. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expense_logs` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
    }
}

/** Adds the notes / observations table. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `car_notes` (`noteId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`odometer` INTEGER NOT NULL, `tag` TEXT NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL, " +
                "PRIMARY KEY(`noteId`), FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_car_notes_vehicleId` ON `car_notes` (`vehicleId`)")
    }
}

/** Adds the history of completed services. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `maintenance_records` (`recordId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`scheduleId` TEXT NOT NULL, `serviceName` TEXT NOT NULL, `date` TEXT NOT NULL, `odometer` INTEGER NOT NULL, " +
                "`cost` REAL NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`recordId`), " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_records_vehicleId` ON `maintenance_records` (`vehicleId`)")
    }
}

/** Adds receipt / file attachments. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`attachmentId` TEXT NOT NULL, `vehicleId` TEXT NOT NULL, " +
                "`ownerType` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `mime` TEXT NOT NULL, " +
                "`size` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`attachmentId`), " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_vehicleId` ON `attachments` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_ownerId` ON `attachments` (`ownerId`)")
    }
}
