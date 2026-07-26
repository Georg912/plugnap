package io.github.georg912.plugnap

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.util.Locale

/**
 * Listens for ACTION_POWER_CONNECTED/DISCONNECTED during the bedtime window.
 *
 * Why a foreground service? Since Android 8 these broadcasts can no longer be
 * registered in the manifest — a running process is required. The service only
 * runs inside the configured window (or permanently in "any time of day"
 * mode) and is stopped otherwise.
 */
class BedtimeService : Service() {

    private var receiverRegistered = false

    // The grace/delay timers are wakeup alarms, NOT Handler.postDelayed:
    // postDelayed counts uptimeMillis, which stops during CPU suspend — and
    // "cable pulled, screen off" is exactly the suspend scenario. The alarm
    // fires ACTION_REEVALUATE, which restarts this service and re-checks the
    // actual state; it also survives process death mid-timer.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    AlarmScheduler.cancelReevaluate(context)
                    val delayMs = Prefs(context).plugInDelaySec * 1000L
                    if (delayMs == 0L) evaluate()
                    else AlarmScheduler.scheduleReevaluate(context, delayMs)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    AlarmScheduler.cancelReevaluate(context)
                    val prefs = Prefs(context)
                    if (prefs.ruleActive) {
                        val graceMs = prefs.unplugGraceSec * 1000L
                        if (graceMs == 0L) {
                            ZenRuleManager.setActive(this@BedtimeService, false)
                        } else {
                            AlarmScheduler.scheduleReevaluate(context, graceMs)
                        }
                    }
                }
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ->
                    AlarmScheduler.reschedule(context)
            }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        // NOT_EXPORTED: these are protected system broadcasts, and system
        // senders reach non-exported receivers just fine — free hardening.
        ContextCompat.registerReceiver(
            this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        if (!prefs.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.allDay && Schedule.currentWindow(LocalDateTime.now(), prefs) == null) {
            // Started outside the window (boot race etc.) -> shut down
            ZenRuleManager.setActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        evaluate()
        updateNotification()
        return START_STICKY
    }

    /** Should the mode be on right now? (charger type, skip, charging state) */
    private fun evaluate() {
        val prefs = Prefs(this)
        val skipped = System.currentTimeMillis() < prefs.skipUntil
        val shouldBeOn = !skipped && currentPlugAllowed(prefs)
        ZenRuleManager.setActive(this, shouldBeOn)
    }

    /** Is the device currently charging via an allowed charger type? */
    private fun currentPlugAllowed(prefs: Prefs): Boolean {
        val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when {
            plugged == 0 -> false
            plugged and (BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_DOCK) != 0 ->
                prefs.plugAc
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> prefs.plugUsb
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> prefs.plugWireless
            else -> true
        }
    }

    override fun onDestroy() {
        // Deliberately NOT cancelling a pending re-evaluate alarm: if the
        // system kills us mid-timer, the alarm restarts the service and the
        // state check completes — that's the self-healing path.
        if (receiverRegistered) unregisterReceiver(powerReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )
        // Channel with no importance: the FGS keeps running, but Android shows
        // no notification — the app's own "hide" option.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HIDDEN,
                getString(R.string.notification_channel_hidden),
                NotificationManager.IMPORTANCE_NONE
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(): Notification {
        val prefs = Prefs(this)
        val fmt = { m: Int -> String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60) }
        val text = when {
            System.currentTimeMillis() < prefs.skipUntil ->
                getString(R.string.notification_skipped)
            prefs.ruleActive -> getString(R.string.notification_active)
            prefs.allDay -> getString(R.string.notification_waiting_allday)
            else -> getString(R.string.notification_waiting, fmt(prefs.windowEnd))
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_SKIP_TONIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = if (prefs.hideNotification) CHANNEL_HIDDEN else CHANNEL_ID
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_bedtime)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
        if (System.currentTimeMillis() >= prefs.skipUntil) {
            builder.addAction(0, getString(R.string.skip_tonight), skipIntent)
        }
        return builder.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "bedtime_watch"
        private const val CHANNEL_HIDDEN = "bedtime_watch_hidden"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BedtimeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BedtimeService::class.java))
        }
    }
}
