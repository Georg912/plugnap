package io.github.georg912.plugnap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime

/**
 * Restores the alarms after boot, timezone change or app update, and starts
 * the service if we are currently inside the bedtime window.
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
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return
        val prefs = Prefs(context)
        if (!prefs.enabled) return
        AlarmScheduler.reschedule(context)
        if (Schedule.inWindow(LocalDateTime.now(), prefs)) {
            try {
                BedtimeService.start(context)
            } catch (e: Exception) {
                Log.e("PlugNap", "Failed to start service after boot", e)
            }
        }
    }
}
