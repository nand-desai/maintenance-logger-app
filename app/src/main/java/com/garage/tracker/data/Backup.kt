package com.garage.tracker.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** JSON export/import of the whole garage. Column names follow the schema in the README. */
object Backup {
    private const val APP = "carlo"
    private val ACCEPTED = setOf(APP, "wheelhouse", "pitstop")
    private const val FORMAT_VERSION = 2 // 2 = metric (km, litres)

    data class Counts(val vehicles: Int, val schedules: Int, val expenses: Int, val reminders: Int) {
        override fun toString() =
            "$vehicles vehicles, $schedules services, $expenses expenses, $reminders reminders"
    }

    suspend fun toJson(db: GarageDatabase): String {
        val root = JSONObject()
            .put("app", APP)
            .put("version", FORMAT_VERSION)
            .put("exported_at", Instant.now().toString())
        root.put("vehicles", JSONArray(db.vehicleDao().getAll().map {
            JSONObject().put("vehicle_id", it.vehicleId).put("make", it.make).put("model", it.model)
                .put("year", it.year).put("current_odometer", it.currentOdometer).put("color_hex", it.colorHex)
        }))
        root.put("maintenance_schedules", JSONArray(db.scheduleDao().getAll().map {
            JSONObject().put("schedule_id", it.scheduleId).put("vehicle_id", it.vehicleId)
                .put("service_name", it.serviceName).put("interval_km", it.intervalKm)
                .put("last_done_odometer", it.lastDoneOdometer)
        }))
        root.put("expense_logs", JSONArray(db.expenseDao().getAll().map {
            JSONObject().put("log_id", it.logId).put("vehicle_id", it.vehicleId).put("date", it.date)
                .put("odometer", it.odometer).put("category", it.category).put("cost", it.cost)
                .put("fuel_liters", it.fuelLiters)
        }))
        root.put("reminders", JSONArray(db.reminderDao().getAll().map {
            JSONObject().put("reminder_id", it.reminderId).put("vehicle_id", it.vehicleId ?: JSONObject.NULL)
                .put("title", it.title).put("note", it.note).put("trigger_at", it.triggerAt)
        }))
        return root.toString(2)
    }

    /**
     * Merges a backup into the database: rows with the same ID are overwritten, everything else is
     * kept. Runs in one transaction, so a malformed file changes nothing.
     */
    suspend fun restore(db: GarageDatabase, text: String): Counts {
        val root = JSONObject(text)
        require(root.optString("app") in ACCEPTED) { "This isn't a Carlo backup file" }
        require(root.optInt("version", 1) >= 2) { "This backup uses miles/gallons and can't be imported" }
        val vehicles = root.optJSONArray("vehicles").objects().map {
            Vehicle(it.getString("vehicle_id"), it.getString("make"), it.getString("model"),
                it.getInt("year"), it.getInt("current_odometer"), it.optString("color_hex", "#3B82F6"))
        }
        val schedules = root.optJSONArray("maintenance_schedules").objects().map {
            MaintenanceSchedule(it.getString("schedule_id"), it.getString("vehicle_id"),
                it.getString("service_name"), it.getInt("interval_km"), it.getInt("last_done_odometer"))
        }
        val expenses = root.optJSONArray("expense_logs").objects().map {
            ExpenseLog(it.getString("log_id"), it.getString("vehicle_id"), it.getString("date"),
                it.getInt("odometer"), it.getString("category"), it.getDouble("cost"),
                it.optDouble("fuel_liters", 0.0))
        }
        val reminders = root.optJSONArray("reminders").objects().map {
            Reminder(it.getString("reminder_id"), if (it.isNull("vehicle_id")) null else it.getString("vehicle_id"),
                it.getString("title"), it.optString("note", ""), it.getLong("trigger_at"))
        }
        db.withTransaction {
            vehicles.forEach { db.vehicleDao().upsert(it) }
            schedules.forEach { db.scheduleDao().upsert(it) }
            expenses.forEach { db.expenseDao().upsert(it) }
            reminders.forEach { db.reminderDao().upsert(it) }
        }
        return Counts(vehicles.size, schedules.size, expenses.size, reminders.size)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}
