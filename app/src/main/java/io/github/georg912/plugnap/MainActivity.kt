package io.github.georg912.plugnap

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var previewRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Permissions ---
        findViewById<MaterialButton>(R.id.btnDndAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnExactAlarm).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        // --- Main switch ---
        val master = findViewById<MaterialSwitch>(R.id.switchEnabled)
        master.isChecked = prefs.enabled
        master.setOnCheckedChangeListener { _, checked ->
            if (checked && !ZenRuleManager.hasDndAccess(this)) {
                master.isChecked = false
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return@setOnCheckedChangeListener
            }
            prefs.enabled = checked
            applyConfiguration()
        }

        // --- Skip tonight ---
        findViewById<MaterialButton>(R.id.btnSkip).setOnClickListener {
            prefs.skipUntil =
                if (skipActive()) 0L else Schedule.skipUntilMillis(prefs)
            applyConfiguration()
        }

        // --- Night window ---
        val allDay = findViewById<MaterialSwitch>(R.id.switchAllDay)
        allDay.isChecked = prefs.allDay
        allDay.setOnCheckedChangeListener { _, checked ->
            prefs.allDay = checked
            applyConfiguration()
        }
        findViewById<Button>(R.id.btnWindowStart).setOnClickListener {
            pickTime(prefs.windowStart) { prefs.windowStart = it; applyConfiguration() }
        }
        findViewById<Button>(R.id.btnWindowEnd).setOnClickListener {
            pickTime(prefs.windowEnd) { prefs.windowEnd = it; applyConfiguration() }
        }

        // --- Schedule mode (simple / weekend / per day) ---
        val toggleMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)
        toggleMode.check(
            when (prefs.mode) {
                ScheduleMode.SIMPLE -> R.id.btnModeSimple
                ScheduleMode.WEEKEND -> R.id.btnModeWeekend
                ScheduleMode.PER_DAY -> R.id.btnModePerDay
            }
        )
        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.mode = when (checkedId) {
                R.id.btnModeWeekend -> ScheduleMode.WEEKEND
                R.id.btnModePerDay -> ScheduleMode.PER_DAY
                else -> ScheduleMode.SIMPLE
            }
            animateLayoutChange()
            applyConfiguration()
        }
        findViewById<Button>(R.id.btnWeekendStart).setOnClickListener {
            pickTime(prefs.weekendStart) { prefs.weekendStart = it; applyConfiguration() }
        }
        findViewById<Button>(R.id.btnWeekendEnd).setOnClickListener {
            pickTime(prefs.weekendEnd) { prefs.weekendEnd = it; applyConfiguration() }
        }
        buildPerDayRows()

        // --- End at alarm ---
        val endAtAlarm = findViewById<MaterialSwitch>(R.id.switchEndAtAlarm)
        val toggleAlarm = findViewById<MaterialButtonToggleGroup>(R.id.toggleAlarmMode)
        endAtAlarm.isChecked = prefs.alarmEndMode != AlarmEndMode.OFF
        toggleAlarm.check(
            when (prefs.alarmEndMode) {
                AlarmEndMode.EXTEND -> R.id.btnAlarmExtend
                AlarmEndMode.FOLLOW -> R.id.btnAlarmFollow
                else -> R.id.btnAlarmShorten
            }
        )
        endAtAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.alarmEndMode =
                if (!checked) AlarmEndMode.OFF else checkedAlarmMode(toggleAlarm)
            animateLayoutChange()
            applyConfiguration()
        }
        toggleAlarm.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (endAtAlarm.isChecked) {
                prefs.alarmEndMode = checkedAlarmMode(toggleAlarm)
                animateLayoutChange()
                applyConfiguration()
            }
        }
        bindDropdown(R.id.dropdownMaxExtend, R.array.max_extend_labels, MAX_EXTEND_VALUES,
            prefs.maxExtendMinutes) {
            prefs.maxExtendMinutes = it
            applyConfiguration()
        }

        // --- Charger types ---
        bindPlugType(R.id.cbAc, { prefs.plugAc }, { prefs.plugAc = it })
        bindPlugType(R.id.cbUsb, { prefs.plugUsb }, { prefs.plugUsb = it })
        bindPlugType(R.id.cbWireless, { prefs.plugWireless }, { prefs.plugWireless = it })

        // --- Effects ---
        bindEffect(R.id.switchGrayscale, prefs.grayscale) { prefs.grayscale = it }
        bindEffect(R.id.switchAmbient, prefs.suppressAmbient) { prefs.suppressAmbient = it }
        bindEffect(R.id.switchDimWallpaper, prefs.dimWallpaper) { prefs.dimWallpaper = it }
        bindEffect(R.id.switchNightMode, prefs.nightMode) { prefs.nightMode = it }

        // --- DND filter ---
        bindDropdown(R.id.dropdownFilter, R.array.filter_labels, FILTERS,
            prefs.interruptionFilter) {
            prefs.interruptionFilter = it
            applyConfiguration()
        }

        // --- Plug-in delay & unplug grace ---
        bindDropdown(R.id.dropdownPlugDelay, R.array.plug_delay_labels, PLUG_DELAY_VALUES,
            prefs.plugInDelaySec) { prefs.plugInDelaySec = it }
        bindDropdown(R.id.dropdownGrace, R.array.grace_labels, GRACE_VALUES,
            prefs.unplugGraceSec) { prefs.unplugGraceSec = it }

        // --- Hide notification ---
        val hideNotif = findViewById<MaterialSwitch>(R.id.switchHideNotification)
        hideNotif.isChecked = prefs.hideNotification
        hideNotif.setOnCheckedChangeListener { _, checked ->
            prefs.hideNotification = checked
            // Restart the service so the notification moves to the other
            // channel (visible <-> hidden).
            BedtimeService.stop(this)
            applyConfiguration()
            if (checked) {
                // Android bumps the importance of FGS channels back up on its
                // own — only a USER block of the channel is final. So lead
                // straight there.
                startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, "bedtime_watch_hidden")
                )
            }
        }

        // --- App theme ---
        bindDropdown(R.id.dropdownTheme, R.array.theme_labels, THEME_VALUES,
            prefs.themeMode) {
            prefs.themeMode = it
            AppCompatDelegate.setDefaultNightMode(it)
        }

        // --- Preview ---
        findViewById<MaterialButton>(R.id.btnPreview).setOnClickListener { runPreview() }
        findViewById<MaterialButton>(R.id.btnModeSettings).setOnClickListener {
            openModeSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        if (previewRunning) ZenRuleManager.setActive(this, false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun skipActive() = System.currentTimeMillis() < prefs.skipUntil

    /** After every settings change: adjust rule, alarms and service. */
    private fun applyConfiguration() {
        if (prefs.enabled && ZenRuleManager.hasDndAccess(this)) {
            ZenRuleManager.ensureRule(this)
            AlarmScheduler.reschedule(this)
            val alarm = AlarmScheduler.nextAlarmClockTime(this)
            if (Schedule.inWindow(LocalDateTime.now(), prefs, alarm)) {
                BedtimeService.start(this)
            } else {
                BedtimeService.stop(this)
            }
        } else {
            AlarmScheduler.reschedule(this)
            BedtimeService.stop(this)
            ZenRuleManager.setActive(this, false)
        }
        updateStatus()
        // The service activates the rule asynchronously — refresh shortly after.
        handler.postDelayed({ updateStatus() }, 1200)
    }

    private fun bindEffect(id: Int, initial: Boolean, save: (Boolean) -> Unit) {
        val sw = findViewById<MaterialSwitch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked ->
            save(checked)
            applyConfiguration()
        }
    }

    /**
     * Wires up a dropdown with a label array and matching values.
     *
     * Important: non-filtering adapter + label-based mapping. After an
     * activity re-creation (e.g. theme switch) Android restores the field
     * text and the default adapter filters the list down to the matching
     * entry — positions no longer line up then.
     */
    private fun bindDropdown(
        id: Int, labelsRes: Int, values: IntArray, current: Int, save: (Int) -> Unit
    ) {
        val view = findViewById<AutoCompleteTextView>(id)
        val labels = resources.getStringArray(labelsRes)
        view.setAdapter(NoFilterAdapter(this, labels))
        view.setText(labels[values.indexOf(current).coerceAtLeast(0)], false)
        view.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            save(labelToValue(labels, values, label))
        }
    }

    private fun bindPlugType(id: Int, get: () -> Boolean, save: (Boolean) -> Unit) {
        val cb = findViewById<CheckBox>(id)
        cb.isChecked = get()
        cb.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                // At least one charger type must stay enabled (prefs still
                // holds the old value here, hence -1 for the one deselected).
                val remaining = listOf(prefs.plugAc, prefs.plugUsb, prefs.plugWireless)
                    .count { it } - 1
                if (remaining < 1) {
                    cb.isChecked = true
                    Toast.makeText(this, R.string.charger_keep_one, Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
            }
            save(checked)
            applyConfiguration()
        }
    }

    private fun checkedAlarmMode(group: MaterialButtonToggleGroup): AlarmEndMode =
        when (group.checkedButtonId) {
            R.id.btnAlarmExtend -> AlarmEndMode.EXTEND
            R.id.btnAlarmFollow -> AlarmEndMode.FOLLOW
            else -> AlarmEndMode.SHORTEN
        }

    private fun animateLayoutChange() {
        TransitionManager.beginDelayedTransition(
            findViewById(R.id.settingsContainer),
            AutoTransition().setDuration(150)
        )
    }

    /** Shows exactly the block belonging to the current schedule/alarm mode. */
    private fun updateModeVisibility() {
        val mode = prefs.mode
        findViewById<View>(R.id.rowWindow).isVisible = mode != ScheduleMode.PER_DAY
        findViewById<View>(R.id.helpWindow).isVisible = mode != ScheduleMode.PER_DAY
        findViewById<View>(R.id.helpWeekend).isVisible = mode == ScheduleMode.WEEKEND
        findViewById<View>(R.id.rowWeekend).isVisible = mode == ScheduleMode.WEEKEND
        findViewById<View>(R.id.perDayHint).isVisible = mode == ScheduleMode.PER_DAY
        findViewById<View>(R.id.containerPerDay).isVisible = mode == ScheduleMode.PER_DAY
        val alarmMode = prefs.alarmEndMode
        findViewById<View>(R.id.toggleAlarmMode).isVisible = alarmMode != AlarmEndMode.OFF
        findViewById<View>(R.id.tilMaxExtend).isVisible =
            alarmMode == AlarmEndMode.EXTEND || alarmMode == AlarmEndMode.FOLLOW
    }

    /**
     * Seven rows: switch | day | start -> (next day) end. Each time belongs to
     * the night starting on that evening — the end button carries the next
     * day's abbreviation when the window crosses midnight, so users don't set
     * "Monday" thinking of the morning they wake up.
     */
    private fun buildPerDayRows() {
        val container = findViewById<LinearLayout>(R.id.containerPerDay)
        container.removeAllViews()
        val locale = Locale.getDefault()
        val fmt = { m: Int -> String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60) }
        for (day in DayOfWeek.entries) {
            val start = prefs.dayStart(day)
            val end = prefs.dayEnd(day)
            val dayEnabled = start != end
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                MaterialSwitch(this).apply {
                    isChecked = dayEnabled
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            var newEnd = prefs.windowEnd
                            if (newEnd == prefs.dayStart(day)) newEnd = (newEnd + 60) % (24 * 60)
                            prefs.setDayEnd(day, newEnd)
                        } else {
                            prefs.setDayEnd(day, prefs.dayStart(day))
                        }
                        animateLayoutChange()
                        buildPerDayRows()
                        applyConfiguration()
                    }
                }
            )
            row.addView(
                TextView(this).apply {
                    text = day.getDisplayName(TextStyle.SHORT, locale)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
            )
            if (dayEnabled) {
                row.addView(
                    MaterialButton(
                        this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = fmt(start)
                        setOnClickListener {
                            pickTime(prefs.dayStart(day)) {
                                prefs.setDayStart(day, it)
                                buildPerDayRows()
                                applyConfiguration()
                            }
                        }
                    }
                )
                row.addView(TextView(this).apply { text = " → " })
                val crossesMidnight = end < start
                val endPrefix =
                    if (crossesMidnight) day.plus(1).getDisplayName(TextStyle.SHORT, locale) + " "
                    else ""
                row.addView(
                    MaterialButton(
                        this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = endPrefix + fmt(end)
                        setOnClickListener {
                            pickTime(prefs.dayEnd(day)) {
                                prefs.setDayEnd(day, it)
                                buildPerDayRows()
                                applyConfiguration()
                            }
                        }
                    }
                )
            }
            row.setOnLongClickListener {
                val s = prefs.dayStart(day)
                val e = prefs.dayEnd(day)
                if (s != e) {
                    DayOfWeek.entries.forEach { d ->
                        if (prefs.dayStart(d) != prefs.dayEnd(d)) {
                            prefs.setDayStart(d, s)
                            prefs.setDayEnd(d, e)
                        }
                    }
                    Toast.makeText(this, R.string.copied_to_all, Toast.LENGTH_SHORT).show()
                    buildPerDayRows()
                    applyConfiguration()
                }
                true
            }
            container.addView(row)
        }
    }

    private fun pickTime(minuteOfDay: Int, onPicked: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minuteOfDay / 60)
            .setMinute(minuteOfDay % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour * 60 + picker.minute)
        }
        picker.show(supportFragmentManager, "time")
    }

    /** Activates the effects for a 15-second preview. */
    private fun runPreview() {
        if (!ZenRuleManager.hasDndAccess(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        previewRunning = true
        ZenRuleManager.setActive(this, true)
        handler.postDelayed({
            ZenRuleManager.setActive(this, false)
            previewRunning = false
            updateStatus()
        }, 15_000)
        updateStatus()
    }

    private fun openModeSettings() {
        val id = prefs.ruleId ?: ZenRuleManager.ensureRule(this) ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS)
                    .putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID, id)
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
        }
    }

    private fun updateStatus() {
        val dnd = ZenRuleManager.hasDndAccess(this)
        val exact = AlarmScheduler.canScheduleExact(this)
        findViewById<TextView>(R.id.textDndStatus).text = getString(
            if (dnd) R.string.permission_granted else R.string.permission_missing
        )
        findViewById<TextView>(R.id.textExactStatus).text = getString(
            if (exact) R.string.permission_granted else R.string.permission_missing
        )
        findViewById<MaterialButton>(R.id.btnDndAccess).isEnabled = !dnd
        findViewById<MaterialButton>(R.id.btnExactAlarm).isEnabled = !exact

        val fmt = { m: Int -> String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60) }
        findViewById<Button>(R.id.btnWindowStart).text = fmt(prefs.windowStart)
        findViewById<Button>(R.id.btnWindowEnd).text = fmt(prefs.windowEnd)
        findViewById<Button>(R.id.btnWeekendStart).text = fmt(prefs.weekendStart)
        findViewById<Button>(R.id.btnWeekendEnd).text = fmt(prefs.weekendEnd)
        updateModeVisibility()

        findViewById<MaterialButton>(R.id.btnSkip).text = getString(
            if (skipActive()) R.string.resume_skip else R.string.skip_tonight
        )

        val charging = getSystemService(BatteryManager::class.java).isCharging
        val inWindow = Schedule.inWindow(
            LocalDateTime.now(), prefs, AlarmScheduler.nextAlarmClockTime(this)
        )
        val status = findViewById<TextView>(R.id.textStatus)
        status.text = when {
            !prefs.enabled -> getString(R.string.status_disabled)
            !dnd -> getString(R.string.status_no_permission)
            !prefs.allDay && Schedule.allDaysOff(prefs) ->
                getString(R.string.status_no_window)
            skipActive() -> getString(
                R.string.status_skipped,
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(prefs.skipUntil), ZoneId.systemDefault()
                ).let { fmt(it.hour * 60 + it.minute) }
            )
            prefs.ruleActive -> getString(R.string.status_active)
            charging && inWindow -> getString(R.string.status_activating)
            charging -> getString(R.string.status_charging_outside_window)
            else -> getString(
                R.string.status_waiting,
                if (prefs.allDay) getString(R.string.status_anytime)
                else fmt(prefs.windowStart) + "–" + fmt(prefs.windowEnd)
            )
        }
    }

    companion object {
        private val FILTERS = intArrayOf(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_NONE
        )
        private val GRACE_VALUES = intArrayOf(0, 15, 30, 60, 120)
        private val PLUG_DELAY_VALUES = intArrayOf(0, 60, 120, 300, 600)
        private val MAX_EXTEND_VALUES = intArrayOf(30, 60, 120, 180, 240)
        private val THEME_VALUES = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
    }
}
