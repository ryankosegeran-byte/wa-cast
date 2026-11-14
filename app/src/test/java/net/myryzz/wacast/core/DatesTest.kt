package net.myryzz.wacast.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {

    private val march15 = java.util.GregorianCalendar(2026, java.util.Calendar.MARCH, 15).time

    @Test
    fun unknownPresetReturnsEmpty() {
        assertEquals("", formatAutoDate("nope", march15, "en"))
        assertEquals("", formatAutoDate(null, march15, "en"))
    }

    @Test
    fun englishPresets() {
        assertEquals("March 15, 2026", formatAutoDate("dateMonthYear", march15, "en"))
        assertEquals("March", formatAutoDate("monthName", march15, "en"))
        assertEquals("2026", formatAutoDate("year", march15, "en"))
        assertEquals("03", formatAutoDate("monthNum", march15, "en"))
        assertEquals("03/2026", formatAutoDate("monthYear", march15, "en"))
        assertEquals("Sunday", formatAutoDate("dayName", march15, "en"))
        assertEquals("15/03/2026", formatAutoDate("numericDate", march15, "en"))
    }

    @Test
    fun indonesianPresetsUseLocalisedNames() {
        assertEquals("15 Maret 2026", formatAutoDate("dateMonthYear", march15, "id"))
        assertEquals("Maret", formatAutoDate("monthName", march15, "id"))
        assertEquals("Minggu", formatAutoDate("dayName", march15, "id"))
        // Numeric presets are language independent.
        assertEquals("15/03/2026", formatAutoDate("numericDate", march15, "id"))
    }

    @Test
    fun presetListOrderMatchesPicker() {
        assertEquals(
            listOf("dateMonthYear", "monthName", "year", "monthNum", "monthYear", "dayName", "numericDate"),
            AUTO_PRESETS,
        )
    }
}
