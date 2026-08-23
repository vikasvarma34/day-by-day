package com.vikaspokala.daybyday.ui

import com.vikaspokala.daybyday.ui.components.formatTimeSelection
import com.vikaspokala.daybyday.ui.components.normalizeHour
import com.vikaspokala.daybyday.ui.components.normalizeMinute
import com.vikaspokala.daybyday.ui.components.parseAmPm
import com.vikaspokala.daybyday.ui.components.parseHour
import com.vikaspokala.daybyday.ui.components.parseMinute
import org.junit.Assert.assertEquals
import org.junit.Test

class ChooseTimeModalTest {

    @Test
    fun normalizeMinute_validValuesPreserved() {
        assertEquals(48, normalizeMinute("48"))
        assertEquals(46, normalizeMinute("46"))
        assertEquals(4, normalizeMinute("4"))
        assertEquals(0, normalizeMinute("0"))
        assertEquals(5, normalizeMinute("05"))
        assertEquals(59, normalizeMinute("59"))
    }

    @Test
    fun normalizeMinute_invalidValuesUseFallback() {
        assertEquals(30, normalizeMinute("", fallback = 30))
        assertEquals(30, normalizeMinute("abc", fallback = 30))
        assertEquals(30, normalizeMinute("60", fallback = 30))
        assertEquals(30, normalizeMinute("-1", fallback = 30))
    }

    @Test
    fun normalizeHour_validValuesPreserved() {
        assertEquals(1, normalizeHour("1"))
        assertEquals(9, normalizeHour("9"))
        assertEquals(10, normalizeHour("10"))
        assertEquals(12, normalizeHour("12"))
        assertEquals(9, normalizeHour("09"))
    }

    @Test
    fun normalizeHour_invalidValuesUseFallback() {
        assertEquals(10, normalizeHour("", fallback = 10))
        assertEquals(10, normalizeHour("0", fallback = 10))
        assertEquals(10, normalizeHour("13", fallback = 10))
        assertEquals(10, normalizeHour("abc", fallback = 10))
    }

    @Test
    fun formatTimeSelection_producesStandardDisplayString() {
        assertEquals("10:48 PM", formatTimeSelection(10, 48, "PM"))
        assertEquals("10:46 PM", formatTimeSelection(10, 46, "PM"))
        assertEquals("10:04 PM", formatTimeSelection(10, 4, "PM"))
        assertEquals("9:05 AM", formatTimeSelection(9, 5, "AM"))
        assertEquals("12:00 PM", formatTimeSelection(12, 0, "PM"))
    }

    @Test
    fun parseInitialTimeString_correctlyExtractsComponents() {
        assertEquals(10, parseHour("10:48 PM"))
        assertEquals(48, parseMinute("10:48 PM"))
        assertEquals("PM", parseAmPm("10:48 PM"))

        assertEquals(9, parseHour("9:05 AM"))
        assertEquals(5, parseMinute("9:05 AM"))
        assertEquals("AM", parseAmPm("9:05 AM"))

        // Fallbacks for null/blank
        assertEquals(10, parseHour(null))
        assertEquals(30, parseMinute(null))
        assertEquals("AM", parseAmPm(null))
    }
}
