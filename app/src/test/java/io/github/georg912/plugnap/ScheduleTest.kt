package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Plain JVM tests for the window logic (no emulator needed):
 * ./gradlew test
 *
 * Reference week: Mon 2026-07-20 … Sun 2026-07-26 (Fri = 24th, Sat = 25th).
 * Note: the Prefs-level migrations (weekend_enabled -> mode, end_at_alarm ->
 * alarmEndMode) live on SharedPreferences and are exercised on-device, not here.
 */
class ScheduleTest {

    /** Test double for the settings — deliberately Android-free. */
    private class Params(
        override val allDay: Boolean = false,
        override val windowStart: Int = 21 * 60,
        override val windowEnd: Int = 7 * 60,
        override val mode: ScheduleMode = ScheduleMode.SIMPLE,
        override val weekendStart: Int = 23 * 60,
        override val weekendEnd: Int = 8 * 60,
        override val alarmEndMode: AlarmEndMode = AlarmEndMode.OFF,
        override val maxExtendMinutes: Int = 120,
        val days: Map<DayOfWeek, Pair<Int, Int>> = emptyMap(),
    ) : ScheduleParams {
        override fun dayStart(d: DayOfWeek) = days[d]?.first ?: windowStart
        override fun dayEnd(d: DayOfWeek) = days[d]?.second ?: windowEnd
    }

    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, day, hour, minute)

    /** PER_DAY map with exactly one enabled day (21:00-07:00), all others off. */
    private fun onlyDay(active: DayOfWeek): Map<DayOfWeek, Pair<Int, Int>> =
        DayOfWeek.entries.associateWith {
            if (it == active) 21 * 60 to 7 * 60 else 0 to 0
        }

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

    // --- Weekend mode (Fri/Sat 23:00–08:00) ---

    @Test
    fun `Friday 22h is BEFORE the weekend window start`() {
        val p = Params(mode = ScheduleMode.WEEKEND)
        assertFalse(Schedule.inWindow(at(24, 22, 0), p))   // Fri 22:00
        assertTrue(Schedule.inWindow(at(23, 22, 0), p))    // Thu 22:00 (weekday)
    }

    @Test
    fun `Saturday morning 7-30 belongs to Friday night (weekend end 8h)`() {
        val p = Params(mode = ScheduleMode.WEEKEND)
        assertTrue(Schedule.inWindow(at(25, 7, 30), p))    // Sat 07:30
        assertFalse(Schedule.inWindow(at(21, 7, 30), p))   // Tue 07:30 (weekday end 07:00)
    }

    @Test
    fun `Sunday night uses the weekday window again`() {
        val p = Params(mode = ScheduleMode.WEEKEND)
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
            Schedule.nextStart(at(24, 22, 0), Params(mode = ScheduleMode.WEEKEND))
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
            Schedule.nextEnd(at(24, 23, 30), Params(mode = ScheduleMode.WEEKEND))
        )

    @Test
    fun `currentWindow returns null outside`() =
        assertEquals(null, Schedule.currentWindow(at(20, 12, 0), Params()))

    // --- PER_DAY mode ---

    @Test
    fun `PER_DAY only Wednesday active - Tuesday evening is off`() {
        val p = Params(mode = ScheduleMode.PER_DAY, days = onlyDay(DayOfWeek.WEDNESDAY))
        assertFalse(Schedule.inWindow(at(21, 23, 0), p))   // Tue 23:00
    }

    @Test
    fun `PER_DAY only Wednesday active - Thursday 2h belongs to Wednesday night`() {
        val p = Params(mode = ScheduleMode.PER_DAY, days = onlyDay(DayOfWeek.WEDNESDAY))
        assertTrue(Schedule.inWindow(at(23, 2, 0), p))     // Thu 02:00
    }

    @Test
    fun `PER_DAY Sunday window crosses into Monday`() {
        val p = Params(
            mode = ScheduleMode.PER_DAY,
            days = mapOf(DayOfWeek.SUNDAY to (21 * 60 to 6 * 60 + 30))
        )
        assertTrue(Schedule.inWindow(at(27, 5, 0), p))     // Mon 05:00 (Sun night)
    }

    @Test
    fun `PER_DAY all days off - nextStart falls through cleanly`() {
        val p = Params(
            mode = ScheduleMode.PER_DAY,
            days = DayOfWeek.entries.associateWith { 0 to 0 }
        )
        assertFalse(Schedule.inWindow(at(20, 22, 0), p))
        assertTrue(Schedule.allDaysOff(p))
        assertEquals(at(20, 18, 0).plusDays(1), Schedule.nextStart(at(20, 18, 0), p))
    }

    @Test
    fun `PER_DAY Saturday daytime window without rollover`() {
        val p = Params(
            mode = ScheduleMode.PER_DAY,
            days = DayOfWeek.entries.associateWith {
                if (it == DayOfWeek.SATURDAY) 10 * 60 to 12 * 60 else 0 to 0
            }
        )
        assertTrue(Schedule.inWindow(at(25, 11, 0), p))    // Sat 11:00
        assertFalse(Schedule.inWindow(at(25, 9, 0), p))    // Sat 09:00
        assertFalse(Schedule.inWindow(at(25, 13, 0), p))   // Sat 13:00 (no plusDays)
    }

    @Test
    fun `allDaysOff is false outside PER_DAY mode`() =
        assertFalse(Schedule.allDaysOff(Params(mode = ScheduleMode.SIMPLE)))

    // --- Alarm-coupled end (base window Mon 21:00 -> Tue 07:00) ---

    private val window = Schedule.Window(at(20, 21, 0), at(21, 7, 0))

    @Test
    fun `EXTEND - alarm 30min past end within cap - alarm wins`() =
        assertEquals(
            at(21, 7, 30),
            Schedule.effectiveEnd(window, at(21, 7, 30), Params(alarmEndMode = AlarmEndMode.EXTEND))
        )

    @Test
    fun `EXTEND - alarm 180min past end beyond cap 120 - window end wins`() =
        assertEquals(
            at(21, 7, 0),
            Schedule.effectiveEnd(window, at(21, 10, 0), Params(alarmEndMode = AlarmEndMode.EXTEND))
        )

    @Test
    fun `EXTEND - alarm before window end - window end wins`() =
        assertEquals(
            at(21, 7, 0),
            Schedule.effectiveEnd(window, at(21, 6, 0), Params(alarmEndMode = AlarmEndMode.EXTEND))
        )

    @Test
    fun `SHORTEN - alarm after window end - window end wins`() =
        assertEquals(
            at(21, 7, 0),
            Schedule.effectiveEnd(window, at(21, 8, 0), Params(alarmEndMode = AlarmEndMode.SHORTEN))
        )

    @Test
    fun `SHORTEN - alarm before window end - alarm wins`() =
        assertEquals(
            at(21, 6, 0),
            Schedule.effectiveEnd(window, at(21, 6, 0), Params(alarmEndMode = AlarmEndMode.SHORTEN))
        )

    @Test
    fun `FOLLOW - alarm before window end - alarm wins`() =
        assertEquals(
            at(21, 6, 0),
            Schedule.effectiveEnd(window, at(21, 6, 0), Params(alarmEndMode = AlarmEndMode.FOLLOW))
        )

    @Test
    fun `FOLLOW - alarm beyond cap - window end wins`() =
        assertEquals(
            at(21, 7, 0),
            Schedule.effectiveEnd(window, at(21, 9, 30), Params(alarmEndMode = AlarmEndMode.FOLLOW))
        )

    @Test
    fun `all modes - no alarm - window end`() {
        for (m in AlarmEndMode.entries) {
            assertEquals(
                at(21, 7, 0),
                Schedule.effectiveEnd(window, null, Params(alarmEndMode = m))
            )
        }
    }

    @Test
    fun `all modes - alarm before window start - window end`() {
        for (m in AlarmEndMode.entries) {
            assertEquals(
                at(21, 7, 0),
                Schedule.effectiveEnd(window, at(20, 20, 0), Params(alarmEndMode = m))
            )
        }
    }

    @Test
    fun `OFF ignores the alarm entirely`() =
        assertEquals(
            at(21, 7, 0),
            Schedule.effectiveEnd(window, at(21, 6, 0), Params(alarmEndMode = AlarmEndMode.OFF))
        )

    @Test
    fun `inWindow is true between base end and extended end`() {
        val p = Params(alarmEndMode = AlarmEndMode.EXTEND)
        val alarm = at(21, 8, 0)
        assertTrue(Schedule.inWindow(at(21, 7, 30), p, alarm))
        assertFalse(Schedule.inWindow(at(21, 7, 30), p))            // without alarm
        assertFalse(Schedule.inWindow(at(21, 8, 30), p, alarm))     // past the alarm
    }

    @Test
    fun `nextEffectiveEnd inside extended window returns the alarm time`() =
        assertEquals(
            at(21, 8, 0),
            Schedule.nextEffectiveEnd(
                at(21, 7, 30), Params(alarmEndMode = AlarmEndMode.EXTEND), at(21, 8, 0)
            )
        )
}
