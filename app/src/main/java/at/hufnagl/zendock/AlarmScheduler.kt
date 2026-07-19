package at.hufnagl.zendock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Plant die täglichen Alarme für Fensterstart und Fensterende.
 *
 * Exakte Alarme sind hier nicht nur Kosmetik: Nur der Empfang eines exakten
 * Alarms erlaubt es, den Foreground-Service aus dem Hintergrund zu starten
 * (FGS-Background-Start-Exemption seit Android 12).
 */
object AlarmScheduler {

    const val ACTION_WINDOW_START = "at.hufnagl.zendock.WINDOW_START"
    const val ACTION_WINDOW_END = "at.hufnagl.zendock.WINDOW_END"

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

        set(am, nextOccurrence(prefs.windowStart), startPi)
        set(am, nextOccurrence(prefs.windowEnd), endPi)
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

    /** Nächster Zeitpunkt (heute oder morgen) für minuteOfDay, als Epoch-Millis. */
    private fun nextOccurrence(minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var candidate = LocalDateTime.of(LocalDate.now(zone), time)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }
}
