package io.github.georg912.plugnap

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/**
 * "Exact-restore" test for the settings export/import feature: sets every
 * exportable field away from its default, exports, wipes SharedPreferences
 * back to a clean-install state, imports, and checks every field is back
 * exactly as it was. Device-bound state (ruleId/ruleActive) and the
 * one-night skipUntil flag are set too, but must NOT come back — that's the
 * whole point of excluding them (a restored ruleId would point at a zen
 * rule that doesn't exist on the new device).
 *
 * PrefsJsonTest.kt already covers the pure JSON conversion in isolation;
 * this test exercises real SharedPreferences, the same way an actual
 * device-to-device transfer would.
 */
@RunWith(AndroidJUnit4::class)
class PrefsExportImportRoundTripTest {

    private lateinit var context: Context

    @Before
    fun resetPrefs() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("zendock_device", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun everyExportedSettingSurvivesAWipeAndRestore() {
        val original = Prefs(context)
        original.enabled = true
        original.allDay = true
        original.windowStart = 5
        original.windowEnd = 6
        original.mode = ScheduleMode.PER_DAY
        original.weekendStart = 7
        original.weekendEnd = 8
        original.setDayStart(DayOfWeek.WEDNESDAY, 111)
        original.setDayEnd(DayOfWeek.WEDNESDAY, 222)
        original.plugAc = false
        original.plugUsb = false
        original.plugWireless = false
        original.alarmEndMode = AlarmEndMode.EXTEND
        original.maxExtendMinutes = 45
        original.unplugGraceSec = 90
        original.plugInDelaySec = 15
        original.hideNotification = true
        original.themeMode = AppCompatDelegate.MODE_NIGHT_YES
        original.grayscale = false
        original.suppressAmbient = false
        original.dimWallpaper = false
        original.nightMode = false
        // device-bound / one-night state: must NOT survive the round trip
        original.ruleId = "rule-on-old-phone"
        original.ruleActive = true
        original.skipUntil = 999_999_999L

        val json = original.exportJson()

        // simulate moving to a fresh install (new phone / reinstall)
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("zendock_device", Context.MODE_PRIVATE).edit().clear().commit()

        val restored = Prefs(context)
        restored.importJson(json)

        assertEquals(true, restored.enabled)
        assertEquals(true, restored.allDay)
        assertEquals(5, restored.windowStart)
        assertEquals(6, restored.windowEnd)
        assertEquals(ScheduleMode.PER_DAY, restored.mode)
        assertEquals(7, restored.weekendStart)
        assertEquals(8, restored.weekendEnd)
        assertEquals(111, restored.dayStart(DayOfWeek.WEDNESDAY))
        assertEquals(222, restored.dayEnd(DayOfWeek.WEDNESDAY))
        assertEquals(false, restored.plugAc)
        assertEquals(false, restored.plugUsb)
        assertEquals(false, restored.plugWireless)
        assertEquals(AlarmEndMode.EXTEND, restored.alarmEndMode)
        assertEquals(45, restored.maxExtendMinutes)
        assertEquals(90, restored.unplugGraceSec)
        assertEquals(15, restored.plugInDelaySec)
        assertEquals(true, restored.hideNotification)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, restored.themeMode)
        assertEquals(false, restored.grayscale)
        assertEquals(false, restored.suppressAmbient)
        assertEquals(false, restored.dimWallpaper)
        assertEquals(false, restored.nightMode)

        // deliberately excluded: back to defaults, not the pre-export values
        assertNull("ruleId must never come back from an import", restored.ruleId)
        assertEquals(false, restored.ruleActive)
        assertEquals(0L, restored.skipUntil)
    }

    @Test
    fun importingOverwritesAnUnrelatedExistingProfile() {
        // the receiving device already has its own settings — import must
        // overwrite them, not merge or leave them untouched
        val fresh = Prefs(context)
        fresh.allDay = true
        fresh.windowStart = 999
        fresh.mode = ScheduleMode.WEEKEND
        val json = fresh.exportJson()

        context.getSharedPreferences("zendock", Context.MODE_PRIVATE).edit().clear().commit()
        val stale = Prefs(context)
        stale.allDay = false
        stale.windowStart = 1
        stale.mode = ScheduleMode.SIMPLE

        stale.importJson(json)

        assertEquals(true, stale.allDay)
        assertEquals(999, stale.windowStart)
        assertEquals(ScheduleMode.WEEKEND, stale.mode)
    }
}
