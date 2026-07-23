package io.github.georg912.plugnap

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/** Einstellungen der App. Zeiten als Minuten seit Mitternacht (lokale Zeit). */
class Prefs(context: Context) : ScheduleParams {
    private val sp: SharedPreferences =
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** true = Ladetrigger gilt rund um die Uhr, kein Zeitfenster */
    override var allDay: Boolean
        get() = sp.getBoolean("all_day", false)
        set(v) = sp.edit().putBoolean("all_day", v).apply()

    override var windowStart: Int
        get() = sp.getInt("window_start", 21 * 60)
        set(v) = sp.edit().putInt("window_start", v).apply()

    override var windowEnd: Int
        get() = sp.getInt("window_end", 7 * 60)
        set(v) = sp.edit().putInt("window_end", v).apply()

    /** Eigene Zeiten für die Nächte Fr→Sa und Sa→So */
    override var weekendEnabled: Boolean
        get() = sp.getBoolean("weekend_enabled", false)
        set(v) = sp.edit().putBoolean("weekend_enabled", v).apply()

    override var weekendStart: Int
        get() = sp.getInt("weekend_start", 23 * 60)
        set(v) = sp.edit().putInt("weekend_start", v).apply()

    override var weekendEnd: Int
        get() = sp.getInt("weekend_end", 8 * 60)
        set(v) = sp.edit().putInt("weekend_end", v).apply()

    /** Welche Ladearten den Modus auslösen dürfen */
    var plugAc: Boolean
        get() = sp.getBoolean("plug_ac", true)
        set(v) = sp.edit().putBoolean("plug_ac", v).apply()

    var plugUsb: Boolean
        get() = sp.getBoolean("plug_usb", true)
        set(v) = sp.edit().putBoolean("plug_usb", v).apply()

    var plugWireless: Boolean
        get() = sp.getBoolean("plug_wireless", true)
        set(v) = sp.edit().putBoolean("plug_wireless", v).apply()

    /** Modus schon beim nächsten Wecker beenden statt erst am Fensterende */
    var endAtAlarm: Boolean
        get() = sp.getBoolean("end_at_alarm", false)
        set(v) = sp.edit().putBoolean("end_at_alarm", v).apply()

    /** "Heute aussetzen": bis zu diesem Zeitpunkt (Epoch-Millis) nicht aktivieren */
    var skipUntil: Long
        get() = sp.getLong("skip_until", 0L)
        set(v) = sp.edit().putLong("skip_until", v).apply()

    /** Karenz in Sekunden zwischen Abstecken und Deaktivieren (0 = sofort) */
    var unplugGraceSec: Int
        get() = sp.getInt("unplug_grace_sec", 30)
        set(v) = sp.edit().putInt("unplug_grace_sec", v).apply()

    /** Verzögerung in Sekunden zwischen Anstecken und Aktivieren (0 = sofort) */
    var plugInDelaySec: Int
        get() = sp.getInt("plugin_delay_sec", 0)
        set(v) = sp.edit().putInt("plugin_delay_sec", v).apply()

    /** Status-Benachrichtigung des Wächters ausblenden (Kanal ohne Wichtigkeit) */
    var hideNotification: Boolean
        get() = sp.getBoolean("hide_notification", false)
        set(v) = sp.edit().putBoolean("hide_notification", v).apply()

    /** App-Design: AppCompatDelegate.MODE_NIGHT_* */
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

    /** Vom System vergebene Regel-ID unserer AutomaticZenRule */
    var ruleId: String?
        get() = sp.getString("rule_id", null)
        set(v) = sp.edit().putString("rule_id", v).apply()

    /** Merker, ob die Regel gerade von uns aktiviert wurde (für Statusanzeige) */
    var ruleActive: Boolean
        get() = sp.getBoolean("rule_active", false)
        set(v) = sp.edit().putBoolean("rule_active", v).apply()
}
