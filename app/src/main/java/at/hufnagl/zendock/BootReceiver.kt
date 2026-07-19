package at.hufnagl.zendock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalTime

/**
 * Stellt nach Boot, Zeitzonenwechsel oder App-Update die Alarme wieder her und
 * startet den Service, falls wir uns gerade im Bedtime-Fenster befinden.
 * (BOOT_COMPLETED darf einen specialUse-FGS starten — die Android-15-
 * Einschränkung betrifft nur bestimmte andere FGS-Typen.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Nur die erwarteten System-Actions akzeptieren — der Receiver ist
        // exportiert (für BOOT_COMPLETED nötig) und sonst per explizitem
        // Intent von Dritt-Apps auslösbar.
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return
        val prefs = Prefs(context)
        if (!prefs.enabled) return
        AlarmScheduler.reschedule(context)
        val now = LocalTime.now().hour * 60 + LocalTime.now().minute
        val inWindow = prefs.allDay ||
            Prefs.inWindow(now, prefs.windowStart, prefs.windowEnd)
        if (inWindow) {
            try {
                BedtimeService.start(context)
            } catch (e: Exception) {
                Log.e("ZenDock", "Service-Start nach Boot fehlgeschlagen", e)
            }
        }
    }
}
