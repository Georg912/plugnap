package io.github.georg912.plugnap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Empfängt Fensterstart/-ende und die "Heute aussetzen"-Aktion aus der
 * Benachrichtigung; plant danach jeweils die nächsten Alarme.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        when (intent.action) {
            AlarmScheduler.ACTION_WINDOW_START -> {
                if (prefs.enabled && !prefs.allDay) {
                    try {
                        BedtimeService.start(context)
                    } catch (e: Exception) {
                        // z. B. ForegroundServiceStartNotAllowedException bei
                        // ungenauem Alarm ohne Exact-Alarm-Berechtigung
                        Log.e("ZenDock", "Service-Start am Fensterbeginn fehlgeschlagen", e)
                    }
                }
            }
            AlarmScheduler.ACTION_WINDOW_END -> {
                // Deaktivieren geht auch ohne laufenden Service direkt hier.
                ZenRuleManager.setActive(context, false)
                context.stopService(Intent(context, BedtimeService::class.java))
            }
            AlarmScheduler.ACTION_SKIP_TONIGHT -> {
                prefs.skipUntil = Schedule.skipUntilMillis(prefs)
                ZenRuleManager.setActive(context, false)
                // Laufenden Service anstoßen, damit die Notification den
                // Aussetzen-Status anzeigt (Service ist bereits Foreground).
                try {
                    BedtimeService.start(context)
                } catch (_: Exception) {
                }
            }
        }
        AlarmScheduler.reschedule(context)
    }
}
