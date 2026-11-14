package com.github.aw00987.tokyosandwich.core

import kotlin.math.roundToInt

// Number formatting shared by the screens and by the providers' record notes.

/** A whole number as is, anything else with one decimal. */
fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

/** A yen amount rounded to whole yen, with thousands separators. */
fun formatAmount(value: Double): String = "%,d".format(value.roundToInt())

/** Averaged daily costs are fractions of a yen, so small amounts keep one decimal. */
fun formatYenPrecise(value: Double): String =
    if (value < 100) "¥" + "%.1f".format(value) else "¥" + formatAmount(value)
