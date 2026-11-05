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

    /** Things the user can log by hand (a swap is logged automatically when a set is mounted). */
    val loggable = listOf(ROTATION, BALANCING, ALIGNMENT, PUNCTURE)
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
