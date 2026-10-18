package com.irfan.prosantinibash.core.i18n

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** Display formatting never changes ISO dates, identifiers, or stored numeric values. */
object DisplayLocale {
    @Volatile var locale: Locale = Locale.ENGLISH
    fun digits(value: String): String = if (locale.language == "bn") value.map {
        if (it in '0'..'9') ('০'.code + it.code - '0'.code).toChar() else it
    }.joinToString("") else value

    fun asciiDigits(value: String): String = value.map {
        if (it in '০'..'৯') ('0'.code + it.code - '০'.code).toChar() else it
    }.joinToString("")

    fun formatter(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)
        .withDecimalStyle(DecimalStyle.STANDARD.withZeroDigit(if (locale.language == "bn") '০' else '0'))

    fun date(value: String): String = runCatching { LocalDate.parse(value).format(formatter("d MMM yyyy")) }
        .getOrDefault(value)
    fun month(value: YearMonth): String = value.format(formatter("MMMM yyyy"))
}
