package com.garage.tracker.data


enum class Health { Healthy, DueSoon, Overdue }

const val DUE_SOON_KM = 1500

/** Kilometres left until the service is due; negative when overdue. */
fun MaintenanceSchedule.kmRemaining(currentOdometer: Int): Int =
    lastDoneOdometer + intervalKm - currentOdometer

fun MaintenanceSchedule.health(currentOdometer: Int, dueSoonKm: Int = DUE_SOON_KM): Health {
    val left = kmRemaining(currentOdometer)
    return when {
        left < 0 -> Health.Overdue
        left <= dueSoonKm -> Health.DueSoon
        else -> Health.Healthy
    }
}

/** Fraction of the interval already used, clamped to 0..1. */
fun MaintenanceSchedule.progress(currentOdometer: Int): Float =
    if (intervalKm <= 0) 1f
    else ((currentOdometer - lastDoneOdometer).toFloat() / intervalKm).coerceIn(0f, 1f)

fun overallHealth(schedules: List<MaintenanceSchedule>, odometer: Int, dueSoonKm: Int = DUE_SOON_KM): Health = when {
    schedules.any { it.health(odometer, dueSoonKm) == Health.Overdue } -> Health.Overdue
    schedules.any { it.health(odometer, dueSoonKm) == Health.DueSoon } -> Health.DueSoon
    else -> Health.Healthy
}

data class ExpenseSummary(
    val lifetimeCost: Double,
    val costPerKm: Double?,
    /** Fuel consumption in litres per 100 km. */
    val litresPer100Km: Double?,
    val byCategory: Map<String, Double>,
)

/**
 * Consumption (L/100 km) comes from the fuel logs: the litres added after the first fill-up divided
 * by the distance driven between the first and last fill-up (the first tank has no known start).
 */
fun summarize(logs: List<ExpenseLog>, startOdometer: Int?, currentOdometer: Int): ExpenseSummary {
    val total = logs.sumOf { it.cost }
    val km = (currentOdometer - (startOdometer ?: logs.minOfOrNull { it.odometer } ?: currentOdometer))
    val fills = logs.filter { it.category == Categories.FUEL && it.fuelLiters > 0 }.sortedBy { it.odometer }
    val consumption = if (fills.size >= 2) {
        val dist = fills.last().odometer - fills.first().odometer
        val litres = fills.drop(1).sumOf { it.fuelLiters }
        if (dist > 0 && litres > 0) litres / dist * 100 else null
    } else null
    return ExpenseSummary(
        lifetimeCost = total,
        costPerKm = if (km > 0 && total > 0) total / km else null,
        litresPer100Km = consumption,
        byCategory = logs.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.cost } },
    )
}

/** L/100 km for a single new fuel entry, given the previous fuel log for the same vehicle. */
fun entryLitresPer100Km(previousFuelOdometer: Int?, odometer: Int, litres: Double): Double? =
    if (previousFuelOdometer != null && litres > 0 && odometer > previousFuelOdometer)
        litres / (odometer - previousFuelOdometer) * 100 else null
