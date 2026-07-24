package io.github.georg912.plugnap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives window start/end and the "skip tonight" action from the
 * notification; reschedules the next alarms afterwards.
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
                        // e.g. ForegroundServiceStartNotAllowedException for an
                        // inexact alarm without the exact-alarm permission
                        Log.e("PlugNap", "Failed to start service at window start", e)
                    }
                }
            }
            AlarmScheduler.ACTION_WINDOW_END -> {
                // Deactivating works right here, even without a running service.
                ZenRuleManager.setActive(context, false)
                context.stopService(Intent(context, BedtimeService::class.java))
            }
            AlarmScheduler.ACTION_SKIP_TONIGHT -> {
                prefs.skipUntil = Schedule.skipUntilMillis(prefs)
                ZenRuleManager.setActive(context, false)
                // Nudge the running service so the notification shows the
                // skip state (the service is already foreground).
                try {
                    BedtimeService.start(context)
                } catch (_: Exception) {
                }
            }
        }
        AlarmScheduler.reschedule(context)
    }
}
