package io.github.georg912.plugnap

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/** App settings. Times are minutes since midnight (local time). */
class Prefs(context: Context) : ScheduleParams {
    private val sp: SharedPreferences =
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE)

    // Device-bound state lives in its own file, which is excluded from
    // backups (see data_extraction_rules.xml): a restored ruleId would point
    // at a zen rule that doesn't exist on the new device.
    private val deviceSp: SharedPreferences =
        context.getSharedPreferences("zendock_device", Context.MODE_PRIVATE)

    init {
        // One-time migration: rule state used to live in the backed-up file.
        if (!deviceSp.contains("rule_id") && sp.contains("rule_id")) {
            deviceSp.edit()
                .putString("rule_id", sp.getString("rule_id", null))
                .putBoolean("rule_active", sp.getBoolean("rule_active", false))
                .apply()
            sp.edit().remove("rule_id").remove("rule_active").apply()
        }
    }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** true = the charging trigger applies around the clock, no window */
    override var allDay: Boolean
        get() = sp.getBoolean("all_day", false)
        set(v) = sp.edit().putBoolean("all_day", v).apply()

    override var windowStart: Int
        get() = sp.getInt("window_start", 21 * 60)
        set(v) = sp.edit().putInt("window_start", v).apply()

    override var windowEnd: Int
        get() = sp.getInt("window_end", 7 * 60)
        set(v) = sp.edit().putInt("window_end", v).apply()

    /** Separate times for Friday and Saturday nights */
    override var weekendEnabled: Boolean
        get() = sp.getBoolean("weekend_enabled", false)
        set(v) = sp.edit().putBoolean("weekend_enabled", v).apply()

    override var weekendStart: Int
        get() = sp.getInt("weekend_start", 23 * 60)
        set(v) = sp.edit().putInt("weekend_start", v).apply()

    override var weekendEnd: Int
        get() = sp.getInt("weekend_end", 8 * 60)
        set(v) = sp.edit().putInt("weekend_end", v).apply()

    /** Which charger types may trigger the mode */
    var plugAc: Boolean
        get() = sp.getBoolean("plug_ac", true)
        set(v) = sp.edit().putBoolean("plug_ac", v).apply()

    var plugUsb: Boolean
        get() = sp.getBoolean("plug_usb", true)
        set(v) = sp.edit().putBoolean("plug_usb", v).apply()

    var plugWireless: Boolean
        get() = sp.getBoolean("plug_wireless", true)
        set(v) = sp.edit().putBoolean("plug_wireless", v).apply()

    /** End the mode at the next alarm clock instead of the window end */
    var endAtAlarm: Boolean
        get() = sp.getBoolean("end_at_alarm", false)
        set(v) = sp.edit().putBoolean("end_at_alarm", v).apply()

    /** "Skip tonight": don't activate until this instant (epoch millis) */
    var skipUntil: Long
        get() = sp.getLong("skip_until", 0L)
        set(v) = sp.edit().putLong("skip_until", v).apply()

    /** Grace period in seconds between unplugging and deactivation (0 = immediately) */
    var unplugGraceSec: Int
        get() = sp.getInt("unplug_grace_sec", 30)
        set(v) = sp.edit().putInt("unplug_grace_sec", v).apply()

    /** Delay in seconds between plugging in and activation (0 = immediately) */
    var plugInDelaySec: Int
        get() = sp.getInt("plugin_delay_sec", 0)
        set(v) = sp.edit().putInt("plugin_delay_sec", v).apply()

    /** Hide the watcher's status notification (importance-less channel) */
    var hideNotification: Boolean
        get() = sp.getBoolean("hide_notification", false)
        set(v) = sp.edit().putBoolean("hide_notification", v).apply()

    /** App theme: AppCompatDelegate.MODE_NIGHT_* */
    var themeMode: Int
        get() = sp.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var grayscale: Boolean
        get() = sp.getBoolean("fx_grayscale", true)
        set(v) = sp.edit().putBoolean("fx_grayscale", v).apply()

    var suppressAmbient: Boolean
        get() = sp.getBoolean("fx_ambient", true)
        set(v) = sp.edit().putBoolean("fx_ambient", v).apply()

    var dimWallpaper: Boolean
        get() = sp.getBoolean("fx_dim_wallpaper", true)
        set(v) = sp.edit().putBoolean("fx_dim_wallpaper", v).apply()

    var nightMode: Boolean
        get() = sp.getBoolean("fx_night_mode", true)
        set(v) = sp.edit().putBoolean("fx_night_mode", v).apply()

    /** NotificationManager.INTERRUPTION_FILTER_* */
    var interruptionFilter: Int
        get() = sp.getInt("filter", NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        set(v) = sp.edit().putInt("filter", v).apply()

    /** System-assigned id of our AutomaticZenRule (device-bound, not backed up) */
    var ruleId: String?
        get() = deviceSp.getString("rule_id", null)
        set(v) = deviceSp.edit().putString("rule_id", v).apply()

    /** Whether we currently have the rule activated (for status display) */
    var ruleActive: Boolean
        get() = deviceSp.getBoolean("rule_active", false)
        set(v) = deviceSp.edit().putBoolean("rule_active", v).apply()
}
