package at.hufnagl.zendock

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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var previewRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)

        // --- Berechtigungen ---
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

        // --- Hauptschalter ---
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

        // --- Zeitfenster ---
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

        // --- Effekte ---
        bindEffect(R.id.switchGrayscale, prefs.grayscale) { prefs.grayscale = it }
        bindEffect(R.id.switchAmbient, prefs.suppressAmbient) { prefs.suppressAmbient = it }
        bindEffect(R.id.switchDimWallpaper, prefs.dimWallpaper) { prefs.dimWallpaper = it }
        bindEffect(R.id.switchNightMode, prefs.nightMode) { prefs.nightMode = it }

        // --- DND-Filter ---
        val filterView = findViewById<AutoCompleteTextView>(R.id.dropdownFilter)
        val labels = resources.getStringArray(R.array.filter_labels)
        filterView.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        filterView.setText(labels[filterIndex(prefs.interruptionFilter)], false)
        filterView.setOnItemClickListener { _, _, position, _ ->
            prefs.interruptionFilter = FILTERS[position]
            applyConfiguration()
        }

        // --- Test ---
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

    /** Nach jeder Einstellungsänderung: Regel, Alarme und Service anpassen. */
    private fun applyConfiguration() {
        if (prefs.enabled && ZenRuleManager.hasDndAccess(this)) {
            ZenRuleManager.ensureRule(this)
            AlarmScheduler.reschedule(this)
            val now = LocalTime.now().hour * 60 + LocalTime.now().minute
            val inWindow = prefs.allDay ||
                Prefs.inWindow(now, prefs.windowStart, prefs.windowEnd)
            if (inWindow) BedtimeService.start(this) else BedtimeService.stop(this)
        } else {
            AlarmScheduler.reschedule(this)
            BedtimeService.stop(this)
            ZenRuleManager.setActive(this, false)
        }
        updateStatus()
        // Der Service aktiviert die Regel asynchron — Status kurz danach auffrischen.
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

    /** Aktiviert die Effekte 15 Sekunden lang zur Vorschau. */
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

        val fmt = { m: Int -> String.format("%02d:%02d", m / 60, m % 60) }
        findViewById<Button>(R.id.btnWindowStart).text = fmt(prefs.windowStart)
        findViewById<Button>(R.id.btnWindowEnd).text = fmt(prefs.windowEnd)

        val charging = getSystemService(BatteryManager::class.java).isCharging
        val now = LocalTime.now().hour * 60 + LocalTime.now().minute
        val inWindow = prefs.allDay ||
            Prefs.inWindow(now, prefs.windowStart, prefs.windowEnd)
        val status = findViewById<TextView>(R.id.textStatus)
        status.text = when {
            !prefs.enabled -> getString(R.string.status_disabled)
            !dnd -> getString(R.string.status_no_permission)
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

    private fun filterIndex(filter: Int) = FILTERS.indexOf(filter).coerceAtLeast(0)

    companion object {
        private val FILTERS = intArrayOf(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_NONE
        )
    }
}
