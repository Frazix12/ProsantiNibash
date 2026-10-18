package com.irfan.prosantinibash.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportsTest {
    @Test fun csvQuotesFieldsAndNeutralizesSpreadsheetFormulas() {
        assertEquals("\"'  =SUM(A1:A2)\"", Exports.csvCell("  =SUM(A1:A2)"))
        assertEquals("\"Owner \"\"A\"\", B\nC\"", Exports.csvCell("Owner \"A\", B\nC"))
        assertEquals("\"12500\"", Exports.csvCell("12500"))
    }
}
