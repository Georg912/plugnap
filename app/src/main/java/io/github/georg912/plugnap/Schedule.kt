package io.github.georg912.plugnap

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Which per-day schedule variant is active. An enum instead of two booleans:
 *  the invalid "weekend + per-day" combination is structurally impossible. */
enum class ScheduleMode { SIMPLE, WEEKEND, PER_DAY }

/** How the next alarm clock affects the window end. */
enum class AlarmEndMode {
    OFF,      // always the window end
    SHORTEN,  // only earlier (the pre-1.6 behavior)
    EXTEND,   // only later
    FOLLOW,   // always the alarm, both directions
}

/**
 * The settings the window logic needs — as an interface so the logic stays
 * testable without Android dependencies (plain JVM unit tests).
 * [Prefs] implements this interface.
 */
interface ScheduleParams {
    val allDay: Boolean
    val windowStart: Int
    val windowEnd: Int
    val mode: ScheduleMode
    val weekendStart: Int
    val weekendEnd: Int
    fun dayStart(d: DayOfWeek): Int
    fun dayEnd(d: DayOfWeek): Int
    val alarmEndMode: AlarmEndMode
    val maxExtendMinutes: Int
}

/**
 * Day-aware window math: regular night window, separate weekend times or
 * per-day times, midnight rollover included. A "window" always belongs to
 * the day on whose evening it starts.
 *
 * The next-alarm-clock time is runtime state, not a setting — it is passed
 * in as an optional parameter instead of living in [ScheduleParams].
 */
object Schedule {

    data class Window(val start: LocalDateTime, val end: LocalDateTime)

    private fun isWeekendNight(date: LocalDate) =
        date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY

    private fun startMinute(date: LocalDate, p: ScheduleParams) = when (p.mode) {
        ScheduleMode.SIMPLE -> p.windowStart
        ScheduleMode.WEEKEND -> if (isWeekendNight(date)) p.weekendStart else p.windowStart
        ScheduleMode.PER_DAY -> p.dayStart(date.dayOfWeek)
    }

    private fun endMinute(date: LocalDate, p: ScheduleParams) = when (p.mode) {
        ScheduleMode.SIMPLE -> p.windowEnd
        ScheduleMode.WEEKEND -> if (isWeekendNight(date)) p.weekendEnd else p.windowEnd
        ScheduleMode.PER_DAY -> p.dayEnd(date.dayOfWeek)
    }

    /** Window of the night starting on the evening of [date] (empty if start == end). */
    fun windowFor(date: LocalDate, p: ScheduleParams): Window {
        val s = startMinute(date, p)
        val e = endMinute(date, p)
        val start = date.atTime(s / 60, s % 60)
        var end = date.atTime(e / 60, e % 60)
        if (e < s) end = end.plusDays(1)
        return Window(start, end)
    }

    /**
     * The window end, possibly moved by the next alarm clock. Never extends
     * past [ScheduleParams.maxExtendMinutes] after the base end: an alarm far
     * in the future (set for tomorrow afternoon) must mean "ignore", not
     * "stay on until then".
     */
    fun effectiveEnd(window: Window, alarm: LocalDateTime?, p: ScheduleParams): LocalDateTime {
        if (alarm == null || p.alarmEndMode == AlarmEndMode.OFF) return window.end
        if (!alarm.isAfter(window.start)) return window.end          // alarm before the window
        val cap = window.end.plusMinutes(p.maxExtendMinutes.toLong())
        if (alarm.isAfter(cap)) return window.end                    // beyond the cap
        val earlier = alarm.isBefore(window.end)
        return when (p.alarmEndMode) {
            AlarmEndMode.SHORTEN -> if (earlier) alarm else window.end
            AlarmEndMode.EXTEND -> if (earlier) window.end else alarm
            AlarmEndMode.FOLLOW -> alarm
            AlarmEndMode.OFF -> window.end
        }
    }

    /**
     * The window [now] currently falls into — with its alarm-adjusted end —
     * or null. [alarm] is the next alarm-clock time, if any.
     */
    fun currentWindow(
        now: LocalDateTime,
        p: ScheduleParams,
        alarm: LocalDateTime? = null,
    ): Window? =
        listOf(now.toLocalDate().minusDays(1), now.toLocalDate())
            .map { windowFor(it, p) }
            .filter { it.end.isAfter(it.start) }
            .map { Window(it.start, effectiveEnd(it, alarm, p)) }
            .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    fun inWindow(now: LocalDateTime, p: ScheduleParams, alarm: LocalDateTime? = null): Boolean =
        p.allDay || currentWindow(now, p, alarm) != null

    fun nextStart(now: LocalDateTime, p: ScheduleParams): LocalDateTime {
        for (i in 0..7L) {
            val w = windowFor(now.toLocalDate().plusDays(i), p)
            if (w.start.isAfter(now) && w.end.isAfter(w.start)) return w.start
        }
        return now.plusDays(1)
    }

    fun nextEnd(now: LocalDateTime, p: ScheduleParams): LocalDateTime {
        for (i in -1..7L) {
            val w = windowFor(now.toLocalDate().plusDays(i), p)
            if (w.end.isAfter(now) && w.end.isAfter(w.start)) return w.end
        }
        return now.plusDays(1)
    }

    /**
     * Where the WINDOW_END alarm should fire: inside a (possibly extended)
     * window its effective end, otherwise the next base end. The base value
     * is refreshed at WINDOW_START and on every alarm-clock change anyway.
     */
    fun nextEffectiveEnd(now: LocalDateTime, p: ScheduleParams, alarm: LocalDateTime?): LocalDateTime =
        currentWindow(now, p, alarm)?.end ?: nextEnd(now, p)

    /** True if PER_DAY mode has every single day disabled (start == end). */
    fun allDaysOff(p: ScheduleParams): Boolean =
        p.mode == ScheduleMode.PER_DAY &&
            DayOfWeek.entries.all { p.dayStart(it) == p.dayEnd(it) }

    fun toEpochMillis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Until when "skip tonight" should last. */
    fun skipUntilMillis(p: ScheduleParams): Long =
        if (p.allDay) System.currentTimeMillis() + 8 * 3600_000L
        else toEpochMillis(nextEnd(LocalDateTime.now(), p))
}
