package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regressionstest für den Dropdown-Bug aus v1.4.0: Nach einer Activity-
 * Neuerstellung (Theme-Wechsel) filterte der Standard-Adapter die Liste auf
 * einen Eintrag zusammen, und die Positions-basierte Zuordnung setzte dann
 * den falschen Wert (Position 0 der gefilterten Liste = erster Array-Wert).
 * Die Zuordnung muss deshalb IMMER über den Label-Text laufen.
 */
class DropdownMappingTest {

    private val labels = arrayOf("Follow system", "Light", "Dark")
    private val values = intArrayOf(-1, 1, 2)  // AppCompatDelegate.MODE_NIGHT_*

    @Test
    fun `Label wird unabhaengig von der Position aufgeloest`() {
        assertEquals(2, labelToValue(labels, values, "Dark"))
        assertEquals(1, labelToValue(labels, values, "Light"))
        assertEquals(-1, labelToValue(labels, values, "Follow system"))
    }

    @Test
    fun `unbekanntes Label faellt auf ersten Wert zurueck statt zu crashen`() =
        assertEquals(-1, labelToValue(labels, values, "gibt es nicht"))
}
