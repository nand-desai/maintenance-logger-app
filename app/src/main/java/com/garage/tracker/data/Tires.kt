package com.garage.tracker.data

object Seasons {
    const val SUMMER = "Summer"
    const val WINTER = "Winter"
    const val ALL_SEASON = "All-season"
    val all = listOf(SUMMER, WINTER, ALL_SEASON)
}

object TireEventType {
    const val ROTATION = "Rotation"
    const val BALANCING = "Balancing"
    const val ALIGNMENT = "Alignment"
    const val PUNCTURE = "Puncture"
    const val SWAP = "Swap"
    const val TREAD = "Tread"

    /** Things the user can log by hand (a swap is logged automatically when a set is mounted). */
    val loggable = listOf(ROTATION, BALANCING, ALIGNMENT, PUNCTURE, TREAD)
}

val wheelNames = linkedMapOf("FL" to "Front left", "FR" to "Front right", "RL" to "Rear left", "RR" to "Rear right")

/** Guideline distances in km, used only to colour the tire-care tiles. */
fun careIntervalKm(type: String): Int = when (type) {
    TireEventType.ROTATION -> 10_000
    TireEventType.BALANCING -> 20_000
    TireEventType.ALIGNMENT -> 20_000
    else -> 0
}

/** Total km driven on this set, including the current stint if it is on the car. */
fun TireSet.kmDriven(vehicleOdometer: Int): Int =
    kmBefore + if (mounted) (vehicleOdometer - mountedAtOdometer).coerceAtLeast(0) else 0

fun List<TireEvent>.latest(type: String): TireEvent? =
    filter { it.type == type }.maxWithOrNull(compareBy({ it.date }, { it.odometer }))

// ---- Tread depth -------------------------------------------------------------------------------

/** Replace around this depth (the legal minimum in most places is 1.6 mm). */
const val TREAD_REPLACE_MM = 3.0

fun TireEvent.treadFor(wheel: String): Double? = when (wheel) {
    "FL" -> treadFl; "FR" -> treadFr; "RL" -> treadRl; else -> treadRr
}

/** Shallowest wheel in one measurement. */
fun TireEvent.worstTread(): Double? = listOfNotNull(treadFl, treadFr, treadRl, treadRr).minOrNull()

private fun List<TireEvent>.chronological() = sortedWith(compareBy({ it.date }, { it.odometer }))

/** Most recent tread reading for each wheel of the given set. */
fun latestTread(events: List<TireEvent>, setId: String?): Map<String, Double> {
    val out = linkedMapOf<String, Double>()
    events.filter { it.type == TireEventType.TREAD && it.setId == setId }.chronological().forEach { e ->
        wheelNames.keys.forEach { w -> e.treadFor(w)?.let { out[w] = it } }
    }
    return out
}

data class WearEstimate(val mmPer1000Km: Double, val kmLeft: Int?)

/** Wear rate of the shallowest wheel between the first and last measurement of a set. */
fun treadWear(events: List<TireEvent>, setId: String?): WearEstimate? {
    val pts = events.filter { it.type == TireEventType.TREAD && it.setId == setId && it.worstTread() != null }.chronological()
    if (pts.size < 2) return null
    val first = pts.first(); val last = pts.last()
    val km = last.odometer - first.odometer
    val worn = first.worstTread()!! - last.worstTread()!!
    if (km <= 0 || worn <= 0) return null
    val perKm = worn / km
    val left = ((last.worstTread()!! - TREAD_REPLACE_MM) / perKm).toInt().coerceAtLeast(0)
    return WearEstimate(perKm * 1000, left)
}

// ---- Rotation happens at the seasonal swap -----------------------------------------------------

/** Newest rotation, counting a seasonal swap as a rotation. */
fun lastRotation(events: List<TireEvent>): TireEvent? =
    events.filter { it.type == TireEventType.ROTATION || it.type == TireEventType.SWAP }
        .maxWithOrNull(compareBy({ it.date }, { it.odometer }))

/** Winter tires from mid-October to mid-April. */
fun isWinterPeriod(date: java.time.LocalDate): Boolean {
    val md = date.monthValue * 100 + date.dayOfMonth
    return md >= 1015 || md < 415
}

/** True when the set on the car doesn't match the time of year. */
fun swapDue(mounted: TireSet?, today: java.time.LocalDate): Boolean = when (mounted?.season) {
    Seasons.SUMMER -> isWinterPeriod(today)
    Seasons.WINTER -> !isWinterPeriod(today)
    else -> false
}

// ---- Brake pads ---------------------------------------------------------------------------------

object BrakeType {
    const val MEASURED = "Measured"
    const val REPLACED = "Replaced"
}

/** New pads are roughly 10-12 mm; most shops advise replacing around 3 mm. */
const val BRAKE_REPLACE_MM = 3.0
const val BRAKE_NEW_MM = 12.0

private fun List<BrakeEntry>.chrono() = sortedWith(compareBy({ it.date }, { it.odometer }))
private fun BrakeEntry.mm(front: Boolean) = if (front) frontMm else rearMm

/** Latest thickness reading for an axle, with the entry it came from. */
fun latestBrake(entries: List<BrakeEntry>, front: Boolean): BrakeEntry? =
    entries.chrono().lastOrNull { it.mm(front) != null }

/** Estimated km left until [BRAKE_REPLACE_MM], from wear since the pads were last replaced. */
fun brakeKmLeft(entries: List<BrakeEntry>, front: Boolean): Int? {
    val sorted = entries.chrono()
    val lastNew = sorted.indexOfLast { it.type == BrakeType.REPLACED }
    val pts = sorted.drop(if (lastNew >= 0) lastNew else 0).filter { it.mm(front) != null }
    if (pts.size < 2) return null
    val first = pts.first(); val last = pts.last()
    val km = last.odometer - first.odometer
    val worn = first.mm(front)!! - last.mm(front)!!
    if (km <= 0 || worn <= 0) return null
    return ((last.mm(front)!! - BRAKE_REPLACE_MM) / (worn / km)).toInt().coerceAtLeast(0)
}
