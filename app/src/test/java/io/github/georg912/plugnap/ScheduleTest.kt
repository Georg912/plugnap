package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Plain JVM tests for the window logic (no emulator needed):
 * ./gradlew test
 *
 * Reference week: Mon 2026-07-20 … Sun 2026-07-26 (Fri = 24th, Sat = 25th).
 */
class ScheduleTest {

    /** Test double for the settings — deliberately Android-free. */
    private class Params(
        override val allDay: Boolean = false,
        override val windowStart: Int = 21 * 60,
        override val windowEnd: Int = 7 * 60,
        override val weekendEnabled: Boolean = false,
        override val weekendStart: Int = 23 * 60,
        override val weekendEnd: Int = 8 * 60,
    ) : ScheduleParams

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, day, hour, minute)

    // --- Base window 21:00–07:00 (crossing midnight) ---

    @Test
    fun `evening is inside the window`() =
        assertTrue(Schedule.inWindow(at(20, 22, 0), Params()))

    @Test
    fun `early morning is inside the window (previous day's night)`() =
        assertTrue(Schedule.inWindow(at(21, 6, 30), Params()))

    @Test
    fun `noon is outside the window`() =
        assertFalse(Schedule.inWindow(at(20, 12, 0), Params()))

    @Test
    fun `window start is inclusive, window end exclusive`() {
        assertTrue(Schedule.inWindow(at(20, 21, 0), Params()))
        assertFalse(Schedule.inWindow(at(21, 7, 0), Params()))
    }

    @Test
    fun `allDay always applies`() =
        assertTrue(Schedule.inWindow(at(20, 12, 0), Params(allDay = true)))

    @Test
    fun `empty window (start equals end) never applies`() {
        val p = Params(windowStart = 21 * 60, windowEnd = 21 * 60)
        assertFalse(Schedule.inWindow(at(20, 21, 0), p))
        assertFalse(Schedule.inWindow(at(20, 23, 0), p))
    }

    @Test
    fun `daytime window without midnight rollover`() {
        val p = Params(windowStart = 9 * 60, windowEnd = 17 * 60)
        assertTrue(Schedule.inWindow(at(20, 12, 0), p))
        assertFalse(Schedule.inWindow(at(20, 20, 0), p))
    }

    // --- Weekend times (Fri/Sat 23:00–08:00) ---

    @Test
    fun `Friday 22h is BEFORE the weekend window start`() {
        val p = Params(weekendEnabled = true)
        assertFalse(Schedule.inWindow(at(24, 22, 0), p))   // Fri 22:00
        assertTrue(Schedule.inWindow(at(23, 22, 0), p))    // Thu 22:00 (weekday)
    }

    @Test
    fun `Saturday morning 7-30 belongs to Friday night (weekend end 8h)`() {
        val p = Params(weekendEnabled = true)
        assertTrue(Schedule.inWindow(at(25, 7, 30), p))    // Sat 07:30
        assertFalse(Schedule.inWindow(at(21, 7, 30), p))   // Tue 07:30 (weekday end 07:00)
    }

    @Test
    fun `Sunday night uses the weekday window again`() {
        val p = Params(weekendEnabled = true)
        assertTrue(Schedule.inWindow(at(26, 21, 30), p))   // Sun 21:30 (night into Mon)
    }

    // --- nextStart / nextEnd ---

    @Test
    fun `nextStart on a weekday`() =
        assertEquals(at(20, 21, 0), Schedule.nextStart(at(20, 18, 0), Params()))

    @Test
    fun `nextStart on Friday respects the weekend time`() =
        assertEquals(
            at(24, 23, 0),
            Schedule.nextStart(at(24, 22, 0), Params(weekendEnabled = true))
        )

    @Test
    fun `nextStart after the window began returns tomorrow`() =
        assertEquals(at(21, 21, 0), Schedule.nextStart(at(20, 21, 30), Params()))

    @Test
    fun `nextEnd inside a running window returns its end`() =
        assertEquals(at(21, 7, 0), Schedule.nextEnd(at(20, 23, 0), Params()))

    @Test
    fun `nextEnd in the Friday night returns the weekend end`() =
        assertEquals(
            at(25, 8, 0),
            Schedule.nextEnd(at(24, 23, 30), Params(weekendEnabled = true))
        )

    @Test
    fun `currentWindow returns null outside`() =
        assertEquals(null, Schedule.currentWindow(at(20, 12, 0), Params()))
}
