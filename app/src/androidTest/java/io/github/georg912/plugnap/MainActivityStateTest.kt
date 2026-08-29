package io.github.georg912.plugnap

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the exact bug class found by hand while testing
 * v1.7.0's settings import: Android restores each stateful widget's own
 * instance state AFTER onCreate() runs (Activity.handleStartActivity ->
 * CompoundButton.onRestoreInstanceState), re-firing its change listener
 * with the PRE-recreate value and silently reverting whatever onCreate
 * (or a real settings change) just set. Fixed via android:saveEnabled=
 * "false" on every stateful widget in activity_main.xml — these tests
 * exist so a future edit that adds a new switch/toggle without that
 * attribute fails CI instead of shipping the same bug again.
 *
 * No unit test can catch this class of bug: it only exists at the real
 * Activity-lifecycle level, which is exactly what these instrumented
 * tests exercise (via ActivityScenario, on a real/emulated device).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStateTest {

    // Pre-grants the runtime permission MainActivity requests on first
    // launch — without this, the system dialog sits modally over the app
    // and swallows every subsequent Espresso click.
    @get:Rule
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Before
    fun resetPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("zendock_device", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun switchSurvivesExplicitRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.switchAllDay)).perform(click())
            onView(withId(R.id.switchAllDay)).check(matches(isChecked()))

            scenario.recreate() // same code path settings import uses

            onView(withId(R.id.switchAllDay)).check(matches(isChecked()))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assert(Prefs(context).allDay) { "allDay reverted to false after recreate()" }
        }
    }

    @Test
    fun toggleGroupSelectionSurvivesExplicitRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btnModeWeekend)).perform(click())
            onView(withId(R.id.btnModeWeekend)).check(matches(isChecked()))

            scenario.recreate()

            onView(withId(R.id.btnModeWeekend)).check(matches(isChecked()))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assert(Prefs(context).mode == ScheduleMode.WEEKEND) {
                "schedule mode reverted away from WEEKEND after recreate()"
            }
        }
    }

    /**
     * The bug as it actually shipped (pre-v1.7, undetected for months): the
     * app theme dropdown itself triggers an IMPLICIT recreate() via
     * AppCompatDelegate.setDefaultNightMode() whenever the effective night
     * mode really changes — no settings-import feature needed to hit it.
     */
    @Test
    fun changingAppThemeDoesNotRevertOtherSwitches() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Force a known starting mode so the second change below is
            // guaranteed to actually flip night mode, regardless of the
            // test device/emulator's current system theme.
            selectTheme("Light")

            // grayscale defaults to true — clicking it turns it OFF, which
            // is just as valid a regression target: recreate() reverting it
            // back to the pre-click default is exactly the bug being guarded.
            onView(withId(R.id.switchGrayscale)).perform(scrollTo(), click())
            onView(withId(R.id.switchGrayscale)).check(matches(isNotChecked()))

            selectTheme("Dark") // Light -> Dark always differs -> real recreate()

            onView(withId(R.id.switchGrayscale)).check(matches(isNotChecked()))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assert(!Prefs(context).grayscale) {
                "grayscale reverted back to true after an app-theme-triggered recreate()"
            }
            assert(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun selectTheme(label: String) {
        onView(withId(R.id.dropdownTheme)).perform(scrollTo(), click())
        // The exposed-dropdown popup's show animation occasionally isn't
        // fully attached yet by the time Espresso looks for its root,
        // especially on a field that was just reopened after a previous
        // selection (NoMatchingRootException, seen once in local testing).
        // loopMainThreadForAtLeast is the Espresso-sanctioned way to wait a
        // fixed, short amount of time without breaking idle synchronization
        // the way a raw Thread.sleep would.
        onView(isRoot()).perform(waitFor(200))
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click())
    }

    private fun waitFor(millis: Long): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription() = "wait for ${millis}ms"
        override fun perform(uiController: UiController, view: View) {
            uiController.loopMainThreadForAtLeast(millis)
        }
    }
}
