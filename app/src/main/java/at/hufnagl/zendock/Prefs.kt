package at.hufnagl.zendock

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences

/** Einstellungen der App. Zeiten als Minuten seit Mitternacht (lokale Zeit). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("zendock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** true = Ladetrigger gilt rund um die Uhr, kein Zeitfenster */
    var allDay: Boolean
        get() = sp.getBoolean("all_day", false)
        set(v) = sp.edit().putBoolean("all_day", v).apply()

    var windowStart: Int
        get() = sp.getInt("window_start", 21 * 60)
        set(v) = sp.edit().putInt("window_start", v).apply()

    var windowEnd: Int
        get() = sp.getInt("window_end", 7 * 60)
        set(v) = sp.edit().putInt("window_end", v).apply()

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

    companion object {
        /**
         * Liegt [minuteOfDay] im Fenster [start, end)? Fenster darf über
         * Mitternacht gehen (z. B. 21:00–07:00).
         */
        fun inWindow(minuteOfDay: Int, start: Int, end: Int): Boolean {
            if (start == end) return false
            return if (start < end) {
                minuteOfDay in start until end
            } else {
                minuteOfDay >= start || minuteOfDay < end
            }
        }
    }
}
