package com.garage.tracker.data


enum class Health { Healthy, DueSoon, Overdue }

const val DUE_SOON_MILES = 1000

/** Miles left until the service is due; negative when overdue. */
fun MaintenanceSchedule.milesRemaining(currentOdometer: Int): Int =
    lastDoneOdometer + intervalMiles - currentOdometer

fun MaintenanceSchedule.health(currentOdometer: Int): Health {
    val left = milesRemaining(currentOdometer)
    return when {
        left < 0 -> Health.Overdue
        left <= DUE_SOON_MILES -> Health.DueSoon
        else -> Health.Healthy
    }
}

/** Fraction of the interval already used, clamped to 0..1. */
fun MaintenanceSchedule.progress(currentOdometer: Int): Float =
    if (intervalMiles <= 0) 1f
    else ((currentOdometer - lastDoneOdometer).toFloat() / intervalMiles).coerceIn(0f, 1f)

fun overallHealth(schedules: List<MaintenanceSchedule>, odometer: Int): Health = when {
    schedules.any { it.health(odometer) == Health.Overdue } -> Health.Overdue
    schedules.any { it.health(odometer) == Health.DueSoon } -> Health.DueSoon
    else -> Health.Healthy
}

data class ExpenseSummary(
    val lifetimeCost: Double,
    val costPerMile: Double?,
    val mpg: Double?,
    val byCategory: Map<String, Double>,
)

/**
 * MPG is computed from the fuel logs: miles driven between the first and last fill-up divided by
 * the gallons added after the first fill-up (the first tank has no known starting point).
 */
fun summarize(logs: List<ExpenseLog>, startOdometer: Int?, currentOdometer: Int): ExpenseSummary {
    val total = logs.sumOf { it.cost }
    val miles = (currentOdometer - (startOdometer ?: logs.minOfOrNull { it.odometer } ?: currentOdometer))
    val fills = logs.filter { it.category == Categories.FUEL && it.fuelGallons > 0 }.sortedBy { it.odometer }
    val mpg = if (fills.size >= 2) {
        val dist = fills.last().odometer - fills.first().odometer
        val gallons = fills.drop(1).sumOf { it.fuelGallons }
        if (dist > 0 && gallons > 0) dist / gallons else null
    } else null
    return ExpenseSummary(
        lifetimeCost = total,
        costPerMile = if (miles > 0 && total > 0) total / miles else null,
        mpg = mpg,
        byCategory = logs.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.cost } },
    )
}

/** MPG for a single new fuel entry, given the previous fuel log for the same vehicle. */
fun entryMpg(previousFuelOdometer: Int?, odometer: Int, gallons: Double): Double? =
    if (previousFuelOdometer != null && gallons > 0 && odometer > previousFuelOdometer)
        (odometer - previousFuelOdometer) / gallons else null
