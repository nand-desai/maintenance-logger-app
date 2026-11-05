package com.garage.tracker.ui.screens

import java.util.Calendar

private val thousands = Regex("""^\d{1,3}(,\d{3})+(\.\d+)?$""")
private val plain = Regex("""^-?(\d+\.?\d*|\.\d+)$""")
private val blanks = Regex("""[\s '’]""")

/**
 * Reads what people actually type: 94,000 · 94 000 · 1,200.50 · 62,4 · 6.5.
 * A comma followed by exactly three digits (1,200 / 1,234,567.50) is a thousands separator;
 * any other single comma is a decimal comma. Returns null when it isn't a number.
 */
internal fun String.toNumber(): Double? {
    var t = replace(blanks, "")
    if (t.isEmpty()) return null
    t = if (thousands.matches(t)) t.replace(",", "") else t.replace(',', '.')
    return if (plain.matches(t)) t.toDoubleOrNull() else null
}

/** A whole-number reading in a believable range, e.g. 94,000 km. */
internal fun String.toOdometer(): Int? = toNumber()?.takeIf { it % 1.0 == 0.0 && it in 0.0..3_000_000.0 }?.toInt()

/** A cost or amount of fuel: zero or more. */
internal fun String.toMoney(): Double? = toNumber()?.takeIf { it in 0.0..1_000_000.0 }

/** Tread or pad thickness in mm. */
internal fun String.toMm(): Double? = toNumber()?.takeIf { it in 0.0..30.0 }

internal fun String.toYear(): Int? =
    toNumber()?.takeIf { it % 1.0 == 0.0 && it in 1950.0..(Calendar.getInstance().get(Calendar.YEAR) + 1).toDouble() }?.toInt()
