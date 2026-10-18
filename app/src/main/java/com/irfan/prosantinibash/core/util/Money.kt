package com.irfan.prosantinibash.core.util

import com.irfan.prosantinibash.core.i18n.DisplayLocale
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Exact paisa conversion; transaction forms separately require positive amounts. */
object Money {
    private val amountPattern = Regex("[0-9]+(?:\\.[0-9]{1,2})?")
    fun parsePaisa(input: String): Long? {
        val value = DisplayLocale.asciiDigits(input.trim())
        if (!amountPattern.matches(value)) return null
        return try { BigDecimal(value).movePointRight(2).longValueExact() }
        catch (_: ArithmeticException) { null }
        catch (_: NumberFormatException) { null }
    }
    fun format(paisa: Long): String {
        val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
        return "৳" + DisplayLocale.digits(formatter.format(BigDecimal.valueOf(paisa, 2)))
    }
}
