package io.github.georg912.plugnap

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime

/**
 * Restores the alarms after boot, timezone change, app update or a newly
 * granted exact-alarm permission (alarms set while it was missing are
 * inexact), and starts the service if we are currently inside the bedtime
 * window. After a boot outside the window it also ends a leftover mode.
 * (BOOT_COMPLETED may start a specialUse FGS — the Android 15 restriction
 * only covers certain other FGS types.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only accept the expected system actions — the receiver is exported
        // (required for BOOT_COMPLETED) and could otherwise be triggered by
        // third-party apps via explicit intents.
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return
        val prefs = Prefs(context)
        if (!prefs.enabled) return
        AlarmScheduler.reschedule(context)
        if (Schedule.inWindow(LocalDateTime.now(), prefs, AlarmScheduler.nextAlarmClockTime(context))) {
            try {
                BedtimeService.start(context)
            } catch (e: Exception) {
                Log.e("PlugNap", "Failed to start service after boot", e)
            }
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            ZenRuleManager.isActive(context)
        ) {
            // The zen rule's state survives a reboot, but the WINDOW_END
            // alarm doesn't: a phone that was off across the window end
            // would otherwise keep the mode on all day. (Boot only — on the
            // other actions a manual tile activation must survive.)
            ZenRuleManager.setActive(context, false)
        }
    }
}
