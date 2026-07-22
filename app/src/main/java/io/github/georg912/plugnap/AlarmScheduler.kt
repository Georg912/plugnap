package io.github.georg912.plugnap

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plant die täglichen Alarme für Fensterstart und Fensterende.
 *
 * Exakte Alarme sind hier nicht nur Kosmetik: Nur der Empfang eines exakten
 * Alarms erlaubt es, den Foreground-Service aus dem Hintergrund zu starten
 * (FGS-Background-Start-Exemption seit Android 12).
 */
object AlarmScheduler {

    const val ACTION_WINDOW_START = "io.github.georg912.plugnap.WINDOW_START"
    const val ACTION_WINDOW_END = "io.github.georg912.plugnap.WINDOW_END"
    const val ACTION_SKIP_TONIGHT = "io.github.georg912.plugnap.SKIP_TONIGHT"

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Plant beide Alarme neu (idempotent). Bei allDay/deaktiviert: alles absagen. */
    fun reschedule(context: Context) {
        val prefs = Prefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val startPi = pendingIntent(context, ACTION_WINDOW_START)
        val endPi = pendingIntent(context, ACTION_WINDOW_END)

        if (!prefs.enabled || prefs.allDay) {
            am.cancel(startPi)
            am.cancel(endPi)
            return
        }

        val now = LocalDateTime.now()
        set(am, Schedule.toEpochMillis(Schedule.nextStart(now, prefs)), startPi)
        set(am, Schedule.toEpochMillis(effectiveEnd(now, prefs, am)), endPi)
    }

    /**
     * Reguläres Fensterende — oder früher, wenn "Beim Wecker beenden" aktiv ist
     * und der nächste Wecker noch innerhalb des Fensters klingelt.
     */
    private fun effectiveEnd(now: LocalDateTime, prefs: Prefs, am: AlarmManager): LocalDateTime {
        val end = Schedule.nextEnd(now, prefs)
        if (!prefs.endAtAlarm) return end
        val trigger = am.nextAlarmClock?.triggerTime ?: return end
        val alarmDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(trigger), ZoneId.systemDefault())
        val current = Schedule.currentWindow(now, prefs)
        val insideWindow = current == null || alarmDt.isAfter(current.start)
        return if (alarmDt.isAfter(now) && alarmDt.isBefore(end) && insideWindow) alarmDt else end
    }

    private fun set(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // Fallback: ungenau (±10 min); der FGS-Start kann dann vom System
            // verweigert werden — die App bittet deshalb im UI um die Berechtigung.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
