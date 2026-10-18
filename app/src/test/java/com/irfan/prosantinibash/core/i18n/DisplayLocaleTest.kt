package com.irfan.prosantinibash.core.i18n

import com.irfan.prosantinibash.core.util.Money
import com.irfan.prosantinibash.feature.tenants.rentPayload
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DisplayLocaleTest {
    @After fun restoreEnglish() { DisplayLocale.locale = Locale.ENGLISH }

    @Test fun bengaliAmountsKeepExactStoredPaisa() {
        DisplayLocale.locale = Locale.forLanguageTag("bn-BD")
        assertEquals("৳১,২৩৪.৫৬", Money.format(123456))
        assertEquals(123456L, Money.parsePaisa("১২৩৪.৫৬"))
        assertEquals(1L, Money.parsePaisa("০.০১"))
        assertNull(Money.parsePaisa("১২.৩৪৫"))
        assertNull(Money.parsePaisa("১e৩"))
        assertEquals("2468", DisplayLocale.asciiDigits("২৪৬৮"))
        val payload = rentPayload(mapOf("amount_paisa" to "১২৩৪.৫৬", "payment_method" to "Cash"),
            setOf("amount_paisa"), emptySet())
        assertEquals("123456", payload.getValue("amount_paisa").jsonPrimitive.content)
        assertEquals("Cash", payload.getValue("payment_method").jsonPrimitive.content)
    }

    @Test fun datesUseBengaliDigitsWithoutChangingIsoValues() {
        DisplayLocale.locale = Locale.forLanguageTag("bn-BD")
        assertTrue(DisplayLocale.date("2026-10-09").contains("২০২৬"))
        assertTrue(DisplayLocale.month(YearMonth.of(2026, 10)).contains("২০২৬"))
        assertEquals("invalid", DisplayLocale.date("invalid"))
        DisplayLocale.locale = Locale.ENGLISH
        assertEquals("৳1,234.56", Money.format(123456))
        assertEquals("9 Oct 2026", DisplayLocale.date("2026-10-09"))
    }
}
