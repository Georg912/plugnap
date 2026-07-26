package io.github.georg912.plugnap

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the daily alarms for window start and window end.
 *
 * Exact alarms are not just cosmetics here: only receiving an exact alarm
 * permits starting the foreground service from the background
 * (FGS background-start exemption since Android 12).
 */
object AlarmScheduler {

    const val ACTION_WINDOW_START = "io.github.georg912.plugnap.WINDOW_START"
    const val ACTION_WINDOW_END = "io.github.georg912.plugnap.WINDOW_END"
    const val ACTION_SKIP_TONIGHT = "io.github.georg912.plugnap.SKIP_TONIGHT"
    const val ACTION_REEVALUATE = "io.github.georg912.plugnap.REEVALUATE"

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Reschedules both alarms (idempotent). Cancels everything for allDay/disabled. */
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
     * Regular window end — or earlier if "end at alarm" is enabled and the
     * next alarm clock rings while still inside the window.
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

    /**
     * Doze-safe short timer for the unplug grace period and the plug-in
     * activation delay. Handler.postDelayed counts uptimeMillis, which stops
     * during CPU suspend — and "cable pulled, screen off" is exactly the
     * suspend scenario, so a 30 s grace could stretch indefinitely. A wakeup
     * alarm fires on wall-clock time and also survives process death; the
     * receiver simply re-evaluates the current state.
     */
    fun scheduleReevaluate(context: Context, delayMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, ACTION_REEVALUATE)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelReevaluate(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, ACTION_REEVALUATE))
    }

    private fun set(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // Fallback: inexact (±10 min); the system may then refuse the FGS
            // start — which is why the UI asks for the permission.
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
