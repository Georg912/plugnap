package io.github.georg912.plugnap

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the dropdown bug from v1.4.0: after an activity
 * re-creation (theme switch) the default adapter filtered the list down to a
 * single entry, and the position-based mapping then applied the wrong value
 * (position 0 of the filtered list = first array value). The mapping must
 * therefore ALWAYS resolve via the label text.
 */
class DropdownMappingTest {

    private val labels = arrayOf("Follow system", "Light", "Dark")
    private val values = intArrayOf(-1, 1, 2)  // AppCompatDelegate.MODE_NIGHT_*

    @Test
    fun `label resolves independently of its position`() {
        assertEquals(2, labelToValue(labels, values, "Dark"))
        assertEquals(1, labelToValue(labels, values, "Light"))
        assertEquals(-1, labelToValue(labels, values, "Follow system"))
    }

    @Test
    fun `unknown label falls back to the first value instead of crashing`() =
        assertEquals(-1, labelToValue(labels, values, "does not exist"))
}
