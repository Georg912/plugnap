package io.github.georg912.plugnap

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Die von der Fensterlogik benötigten Einstellungen — als Interface, damit
 * die Logik ohne Android-Abhängigkeiten (JVM-Unit-Tests!) prüfbar bleibt.
 * [Prefs] implementiert dieses Interface.
 */
interface ScheduleParams {
    val allDay: Boolean
    val windowStart: Int
    val windowEnd: Int
    val weekendEnabled: Boolean
    val weekendStart: Int
    val weekendEnd: Int
}

/**
 * Tagesabhängige Fensterberechnung: normales Nachtfenster, optional eigene
 * Wochenend-Zeiten (Nächte Fr→Sa und Sa→So), Mitternachtsüberlauf inklusive.
 * Ein "Fenster" gehört immer zu dem Tag, an dessen Abend es beginnt.
 */
object Schedule {

    data class Window(val start: LocalDateTime, val end: LocalDateTime)

    private fun isWeekendNight(date: LocalDate) =
        date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY

    private fun startMinute(date: LocalDate, p: ScheduleParams) =
        if (p.weekendEnabled && isWeekendNight(date)) p.weekendStart else p.windowStart

    private fun endMinute(date: LocalDate, p: ScheduleParams) =
        if (p.weekendEnabled && isWeekendNight(date)) p.weekendEnd else p.windowEnd

    /** Fenster der Nacht, die am Abend von [date] beginnt (leer, wenn Start == Ende). */
    fun windowFor(date: LocalDate, p: ScheduleParams): Window {
        val s = startMinute(date, p)
        val e = endMinute(date, p)
        val start = date.atTime(s / 60, s % 60)
        var end = date.atTime(e / 60, e % 60)
        if (e < s) end = end.plusDays(1)
        return Window(start, end)
    }

    /** Das Fenster, in dem [now] gerade liegt — oder null. */
    fun currentWindow(now: LocalDateTime, p: ScheduleParams): Window? =
        listOf(now.toLocalDate().minusDays(1), now.toLocalDate())
            .map { windowFor(it, p) }
            .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    fun inWindow(now: LocalDateTime, p: ScheduleParams): Boolean =
        p.allDay || currentWindow(now, p) != null

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

    fun toEpochMillis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Bis wann "Heute aussetzen" gelten soll. */
    fun skipUntilMillis(p: ScheduleParams): Long =
        if (p.allDay) System.currentTimeMillis() + 8 * 3600_000L
        else toEpochMillis(nextEnd(LocalDateTime.now(), p))
}
