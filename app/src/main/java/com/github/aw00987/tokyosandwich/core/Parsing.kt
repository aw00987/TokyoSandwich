package com.github.aw00987.tokyosandwich.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Value parsing shared by the providers. The services pad strings with half- and full-width spaces and return amounts
// as numeric strings, sometimes with thousands separators.

/** Removes the full/half-width padding spaces the services use for alignment. */
internal fun String.compact(): String = replace(Regex("[\\s　]"), "")

/** An integer amount such as `"1,234"`, or null. */
internal fun String.toAmount(): Int? = compact().replace(",", "").toIntOrNull()

/** A decimal such as `"1,234.56"`, or null. */
internal fun String.toDecimal(): Double? = compact().replace(",", "").toDoubleOrNull()

/** A `yyyyMM` month, or null. */
internal fun String.toYearMonth(): YearMonth? =
    trim().takeIf { it.length == 6 && it.all(Char::isDigit) }
        ?.let { runCatching { YearMonth.of(it.take(4).toInt(), it.drop(4).toInt()) }.getOrNull() }

/** A `yyyyMMdd` date, or null. */
internal fun String.toLocalDate(): LocalDate? =
    trim().takeIf { it.length == 8 && it.all(Char::isDigit) }
        ?.let {
            runCatching {
                LocalDate.of(it.take(4).toInt(), it.substring(4, 6).toInt(), it.drop(6).toInt())
            }.getOrNull()
        }

private val TOKYO_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

/** An ISO-8601 instant such as `"2026-10-14T15:00:00.000Z"` as its date in Tokyo (here 2026-10-15), or null. */
internal fun String.isoToTokyoDate(): LocalDate? =
    runCatching { Instant.parse(trim()).atZone(TOKYO_ZONE).toLocalDate() }.getOrNull()
