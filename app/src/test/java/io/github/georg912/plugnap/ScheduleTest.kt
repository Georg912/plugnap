package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Reine JVM-Tests für die Fensterlogik (kein Emulator nötig):
 * ./gradlew test
 *
 * Referenzwoche: Mo 2026-07-20 … So 2026-07-26 (Fr = 24., Sa = 25.).
 */
class ScheduleTest {

    /** Test-Double für die Einstellungen — bewusst ohne Android. */
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

    // --- Grundfenster 21:00–07:00 (über Mitternacht) ---

    @Test
    fun `abends im Fenster`() =
        assertTrue(Schedule.inWindow(at(20, 22, 0), Params()))

    @Test
    fun `frueh morgens im Fenster (Nacht vom Vortag)`() =
        assertTrue(Schedule.inWindow(at(21, 6, 30), Params()))

    @Test
    fun `mittags nicht im Fenster`() =
        assertFalse(Schedule.inWindow(at(20, 12, 0), Params()))

    @Test
    fun `Fensterstart inklusiv, Fensterende exklusiv`() {
        assertTrue(Schedule.inWindow(at(20, 21, 0), Params()))
        assertFalse(Schedule.inWindow(at(21, 7, 0), Params()))
    }

    @Test
    fun `allDay gilt immer`() =
        assertTrue(Schedule.inWindow(at(20, 12, 0), Params(allDay = true)))

    @Test
    fun `leeres Fenster (Start gleich Ende) gilt nie`() {
        val p = Params(windowStart = 21 * 60, windowEnd = 21 * 60)
        assertFalse(Schedule.inWindow(at(20, 21, 0), p))
        assertFalse(Schedule.inWindow(at(20, 23, 0), p))
    }

    @Test
    fun `Tagfenster ohne Mitternachtsueberlauf`() {
        val p = Params(windowStart = 9 * 60, windowEnd = 17 * 60)
        assertTrue(Schedule.inWindow(at(20, 12, 0), p))
        assertFalse(Schedule.inWindow(at(20, 20, 0), p))
    }

    // --- Wochenend-Zeiten (Fr/Sa 23:00–08:00) ---

    @Test
    fun `Freitag 22 Uhr liegt VOR dem Wochenend-Fensterstart`() {
        val p = Params(weekendEnabled = true)
        assertFalse(Schedule.inWindow(at(24, 22, 0), p))   // Fr 22:00
        assertTrue(Schedule.inWindow(at(23, 22, 0), p))    // Do 22:00 (Werktag)
    }

    @Test
    fun `Samstag frueh 7-30 gehoert zur Freitagnacht (Wochenendende 8 Uhr)`() {
        val p = Params(weekendEnabled = true)
        assertTrue(Schedule.inWindow(at(25, 7, 30), p))    // Sa 07:30
        assertFalse(Schedule.inWindow(at(21, 7, 30), p))   // Di 07:30 (Werktagsende 07:00)
    }

    @Test
    fun `Sonntagnacht gilt wieder das Werktagsfenster`() {
        val p = Params(weekendEnabled = true)
        assertTrue(Schedule.inWindow(at(26, 21, 30), p))   // So 21:30 (Nacht auf Mo)
    }

    // --- nextStart / nextEnd ---

    @Test
    fun `nextStart am Werktag`() =
        assertEquals(at(20, 21, 0), Schedule.nextStart(at(20, 18, 0), Params()))

    @Test
    fun `nextStart am Freitag beruecksichtigt Wochenend-Zeit`() =
        assertEquals(
            at(24, 23, 0),
            Schedule.nextStart(at(24, 22, 0), Params(weekendEnabled = true))
        )

    @Test
    fun `nextStart nach Fensterbeginn liefert morgen`() =
        assertEquals(at(21, 21, 0), Schedule.nextStart(at(20, 21, 30), Params()))

    @Test
    fun `nextEnd im laufenden Fenster liefert dessen Ende`() =
        assertEquals(at(21, 7, 0), Schedule.nextEnd(at(20, 23, 0), Params()))

    @Test
    fun `nextEnd in der Freitagnacht liefert Wochenendende`() =
        assertEquals(
            at(25, 8, 0),
            Schedule.nextEnd(at(24, 23, 30), Params(weekendEnabled = true))
        )

    @Test
    fun `currentWindow liefert null ausserhalb`() =
        assertEquals(null, Schedule.currentWindow(at(20, 12, 0), Params()))
}
