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

    data class Counts(
        val vehicles: Int, val schedules: Int, val expenses: Int, val reminders: Int,
        val tireSets: Int = 0, val tireEvents: Int = 0, val brakeEntries: Int = 0, val notes: Int = 0,
    ) {
        override fun toString() =
            "$vehicles vehicles, $schedules services, $expenses expenses, $reminders reminders, " +
                "$tireSets tire sets, $tireEvents tire events, $brakeEntries brake entries, $notes notes"
    }

    suspend fun toJson(db: GarageDatabase, settings: JSONObject? = null): String {
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
                .put("fuel_liters", it.fuelLiters).put("note", it.note)
        }))
        root.put("reminders", JSONArray(db.reminderDao().getAll().map {
            JSONObject().put("reminder_id", it.reminderId).put("vehicle_id", it.vehicleId ?: JSONObject.NULL)
                .put("title", it.title).put("note", it.note).put("trigger_at", it.triggerAt)
        }))
        root.put("tire_sets", JSONArray(db.tireDao().allSets().map {
            JSONObject().put("set_id", it.setId).put("vehicle_id", it.vehicleId).put("name", it.name)
                .put("season", it.season).put("brand", it.brand).put("size", it.size).put("mounted", it.mounted)
                .put("mounted_at_odometer", it.mountedAtOdometer).put("km_before", it.kmBefore)
        }))
        root.put("tire_events", JSONArray(db.tireDao().allEvents().map {
            JSONObject().put("event_id", it.eventId).put("vehicle_id", it.vehicleId)
                .put("set_id", it.setId ?: JSONObject.NULL).put("type", it.type).put("date", it.date)
                .put("odometer", it.odometer).put("wheel", it.wheel ?: JSONObject.NULL).put("note", it.note)
                .put("cost", it.cost).put("tread_fl", it.treadFl ?: JSONObject.NULL).put("tread_fr", it.treadFr ?: JSONObject.NULL)
                .put("tread_rl", it.treadRl ?: JSONObject.NULL).put("tread_rr", it.treadRr ?: JSONObject.NULL)
        }))
        root.put("brake_entries", JSONArray(db.brakeDao().all().map {
            JSONObject().put("entry_id", it.entryId).put("vehicle_id", it.vehicleId).put("date", it.date)
                .put("odometer", it.odometer).put("type", it.type).put("front_mm", it.frontMm ?: JSONObject.NULL)
                .put("rear_mm", it.rearMm ?: JSONObject.NULL).put("cost", it.cost).put("note", it.note)
        }))
        root.put("car_notes", JSONArray(db.noteDao().all().map {
            JSONObject().put("note_id", it.noteId).put("vehicle_id", it.vehicleId).put("date", it.date)
                .put("odometer", it.odometer).put("tag", it.tag).put("text", it.text).put("done", it.done)
        }))
        root.put("recurring_expenses", JSONArray(db.recurringDao().all().map {
            JSONObject().put("recurring_id", it.recurringId).put("vehicle_id", it.vehicleId).put("category", it.category)
                .put("amount", it.amount).put("day_of_month", it.dayOfMonth).put("next_due", it.nextDue).put("active", it.active)
        }))
        if (settings != null) root.put("settings", settings)
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
                it.optDouble("fuel_liters", 0.0), it.optString("note", ""))
        }
        val reminders = root.optJSONArray("reminders").objects().map {
            Reminder(it.getString("reminder_id"), if (it.isNull("vehicle_id")) null else it.getString("vehicle_id"),
                it.getString("title"), it.optString("note", ""), it.getLong("trigger_at"))
        }
        val tireSets = root.optJSONArray("tire_sets").objects().map {
            TireSet(it.getString("set_id"), it.getString("vehicle_id"), it.getString("name"), it.getString("season"),
                it.optString("brand", ""), it.optString("size", ""), it.optBoolean("mounted", false),
                it.optInt("mounted_at_odometer", 0), it.optInt("km_before", 0))
        }
        val tireEvents = root.optJSONArray("tire_events").objects().map {
            TireEvent(it.getString("event_id"), it.getString("vehicle_id"),
                if (it.isNull("set_id")) null else it.getString("set_id"), it.getString("type"), it.getString("date"),
                it.getInt("odometer"), if (it.isNull("wheel")) null else it.getString("wheel"), it.optString("note", ""),
                it.optDouble("cost", 0.0), it.optMm("tread_fl"), it.optMm("tread_fr"), it.optMm("tread_rl"), it.optMm("tread_rr"))
        }
        val brakes = root.optJSONArray("brake_entries").objects().map {
            BrakeEntry(it.getString("entry_id"), it.getString("vehicle_id"), it.getString("date"), it.getInt("odometer"),
                it.getString("type"), it.optMm("front_mm"), it.optMm("rear_mm"), it.optDouble("cost", 0.0), it.optString("note", ""))
        }
        val notes = root.optJSONArray("car_notes").objects().map {
            CarNote(it.getString("note_id"), it.getString("vehicle_id"), it.getString("date"), it.getInt("odometer"),
                it.optString("tag", NoteTags.GENERAL), it.getString("text"), it.optBoolean("done", false))
        }
        val recurring = root.optJSONArray("recurring_expenses").objects().map {
            RecurringExpense(it.getString("recurring_id"), it.getString("vehicle_id"), it.getString("category"),
                it.getDouble("amount"), it.getInt("day_of_month"), it.getString("next_due"), it.optBoolean("active", true))
        }
        db.withTransaction {
            vehicles.forEach { db.vehicleDao().upsert(it) }
            schedules.forEach { db.scheduleDao().upsert(it) }
            expenses.forEach { db.expenseDao().upsert(it) }
            reminders.forEach { db.reminderDao().upsert(it) }
            tireSets.forEach { db.tireDao().upsertSet(it) }
            tireEvents.forEach { db.tireDao().upsertEvent(it) }
            brakes.forEach { db.brakeDao().upsert(it) }
            recurring.forEach { db.recurringDao().upsert(it) }
            notes.forEach { db.noteDao().upsert(it) }
        }
        return Counts(vehicles.size, schedules.size, expenses.size, reminders.size, tireSets.size, tireEvents.size, brakes.size, notes.size)
    }

    private fun JSONObject.optMm(key: String): Double? = if (isNull(key) || !has(key)) null else getDouble(key)

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}
