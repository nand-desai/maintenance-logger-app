package com.garage.tracker.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.garage.tracker.data.*
import com.garage.tracker.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How a tile or alert should be coloured. */
enum class Level { Good, Warn, Bad, Neutral }

@Composable
fun levelColors(level: Level): Pair<Color, Color> = when (level) {
    Level.Good -> MintBg to MintFg
    Level.Warn -> AmberBg to AmberFg
    Level.Bad -> CoralBg to CoralFg
    Level.Neutral -> NeutralBg to InkSoft
}

data class TileInfo(
    val id: String,
    val title: String,
    val status: String,
    val level: Level,
    val icon: ImageVector,
    val accent: Color,
)

/** Something that needs attention; [target] is the section that fixes it. */
data class Alert(val text: String, val level: Level, val target: String)

private fun km(n: Int) = "${"%,d".format(n)} km"

private fun Health.level() = when (this) {
    Health.Healthy -> Level.Good
    Health.DueSoon -> Level.Warn
    Health.Overdue -> Level.Bad
}

private fun padLevel(mm: Double) = when {
    mm >= 6.0 -> Level.Good
    mm >= BRAKE_REPLACE_MM -> Level.Warn
    else -> Level.Bad
}

private fun treadLevel(mm: Double) = when {
    mm >= 4.0 -> Level.Good
    mm >= TREAD_REPLACE_MM -> Level.Warn
    else -> Level.Bad
}

fun washSummary(washes: List<ExpenseLog>): String {
    val last = washes.firstOrNull() ?: return "No washes logged yet"
    val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(last.date), LocalDate.now()) }.getOrNull()
        ?: return "Last washed ${last.date}"
    return when {
        days <= 0L -> "Washed today"
        days == 1L -> "Washed yesterday"
        else -> "Washed $days days ago"
    }
}

private fun inDays(millis: Long): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
    return when {
        days <= 0L -> "today"
        days == 1L -> "tomorrow"
        else -> "in $days days"
    }
}

/** Everything that currently needs attention, most urgent first. */
fun buildAlerts(
    vehicle: Vehicle, schedules: List<MaintenanceSchedule>, dueSoonKm: Int,
    tireSets: List<TireSet>, tireEvents: List<TireEvent>, brakes: List<BrakeEntry>,
): List<Alert> {
    val odo = vehicle.currentOdometer
    val out = mutableListOf<Alert>()
    schedules.forEach { s ->
        val left = s.kmRemaining(odo)
        when (s.health(odo, dueSoonKm)) {
            Health.Overdue -> out += Alert("${s.serviceName} is overdue by ${km(-left)}", Level.Bad, "maintenance")
            Health.DueSoon -> out += Alert("${s.serviceName} is due in ${km(left)}", Level.Warn, "maintenance")
            Health.Healthy -> {}
        }
    }
    val mounted = tireSets.firstOrNull { it.mounted }
    if (swapDue(mounted, LocalDate.now())) {
        out += Alert(if (mounted?.season == Seasons.SUMMER) "Time to swap to winter tires" else "Time to swap to summer tires", Level.Warn, "tires")
    }
    latestTread(tireEvents, mounted?.setId).values.minOrNull()?.let { mm ->
        when (treadLevel(mm)) {
            Level.Bad -> out += Alert("Tire tread is down to ${"%.1f".format(mm)} mm", Level.Bad, "tires")
            Level.Warn -> out += Alert("Tire tread is getting low (${"%.1f".format(mm)} mm)", Level.Warn, "tires")
            else -> {}
        }
    }
    listOf("Front" to true, "Rear" to false).forEach { (name, front) ->
        val e = latestBrake(brakes, front)
        val mm = e?.let { if (front) it.frontMm else it.rearMm }
        if (mm != null) when (padLevel(mm)) {
            Level.Bad -> out += Alert("$name brake pads are at ${"%.1f".format(mm)} mm", Level.Bad, "brakes")
            Level.Warn -> out += Alert("$name brake pads are getting low (${"%.1f".format(mm)} mm)", Level.Warn, "brakes")
            else -> {}
        }
    }
    return out.sortedBy { if (it.level == Level.Bad) 0 else 1 }
}

/** The six section tiles with a live one-line status each. */
fun buildTiles(
    vehicle: Vehicle, schedules: List<MaintenanceSchedule>, logs: List<ExpenseLog>, dueSoonKm: Int,
    tireSets: List<TireSet>, tireEvents: List<TireEvent>, brakes: List<BrakeEntry>, reminders: List<Reminder>,
    notes: List<CarNote>,
): List<TileInfo> {
    val odo = vehicle.currentOdometer

    // Maintenance
    val next = schedules.minByOrNull { it.kmRemaining(odo) }
    val (maintStatus, maintLevel) = when {
        next == null -> "Add your first service" to Level.Neutral
        next.health(odo, dueSoonKm) == Health.Overdue -> "${next.serviceName} overdue" to Level.Bad
        next.health(odo, dueSoonKm) == Health.DueSoon -> "${next.serviceName} in ${km(next.kmRemaining(odo))}" to Level.Warn
        else -> "All on track · next in ${km(next.kmRemaining(odo))}" to Level.Good
    }

    // Expenses this month
    val month = YearMonth.now().toString()
    val spent = logs.filter { it.date.startsWith(month) }.sumOf { it.cost }

    // Tires
    val mounted = tireSets.firstOrNull { it.mounted }
    val tread = latestTread(tireEvents, mounted?.setId).values.minOrNull()
    val (tireStatus, tireLevel) = when {
        mounted == null -> (if (tireSets.isEmpty()) "Add your tire sets" else "No set mounted") to Level.Neutral
        swapDue(mounted, LocalDate.now()) -> "${mounted.season} on · time to swap?" to Level.Warn
        tread != null -> "${mounted.season} · ${"%.1f".format(tread)} mm" to treadLevel(tread)
        else -> "${mounted.season} set on the car" to Level.Neutral
    }

    // Brakes
    val front = latestBrake(brakes, true)?.frontMm
    val rear = latestBrake(brakes, false)?.rearMm
    val brakeText = listOfNotNull(front?.let { "Front ${"%.1f".format(it)}" }, rear?.let { "Rear ${"%.1f".format(it)}" })
    val brakeLevel = listOfNotNull(front, rear).minOrNull()?.let(::padLevel) ?: Level.Neutral
    val brakeStatus = if (brakeText.isEmpty()) "Not measured yet" else brakeText.joinToString(" · ") + " mm"

    // Washes
    val washes = logs.filter { it.category == Categories.CAR_WASH }.sortedByDescending { it.date }

    // Reminders
    val now = System.currentTimeMillis()
    val upcoming = reminders.filter { it.triggerAt > now }.minByOrNull { it.triggerAt }

    val openNotes = notes.count { !it.done }

    return listOf(
        TileInfo("maintenance", "Maintenance", maintStatus, maintLevel, Icons.Default.Build, BrandBlue),
        TileInfo("expenses", "Expenses", if (spent > 0) "${money(spent, 0)} this month" else "Nothing yet this month", Level.Neutral, Icons.Default.DonutLarge, Color(0xFF8B5CF6)),
        TileInfo("tires", "Tires", tireStatus, tireLevel, Icons.Default.TireRepair, Color(0xFF1FA58A)),
        TileInfo("brakes", "Brakes", brakeStatus, brakeLevel, Icons.Default.Album, Color(0xFFE8793B)),
        TileInfo("wash", "Car wash", washSummary(washes), Level.Neutral, Icons.Default.LocalCarWash, Color(0xFF2B8CD6)),
        TileInfo("notes", "Notes", if (openNotes == 0) "Nothing to mention" else "$openNotes to tell your mechanic", Level.Neutral, Icons.Default.EditNote, Color(0xFFCA9A1B)),
        TileInfo("reminders", "Reminders", upcoming?.let { "${it.title} ${inDays(it.triggerAt)}" } ?: "Nothing scheduled", Level.Neutral, Icons.Default.Notifications, Color(0xFFD6457F)),
    )
}
