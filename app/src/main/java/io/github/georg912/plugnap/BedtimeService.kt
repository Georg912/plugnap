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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.util.Locale

/**
 * Lauscht während des Bedtime-Fensters auf ACTION_POWER_CONNECTED/DISCONNECTED.
 *
 * Warum ein Foreground-Service? Seit Android 8 können diese Broadcasts nicht
 * mehr im Manifest registriert werden — es braucht einen laufenden Prozess.
 * Der Service läuft nur innerhalb des konfigurierten Fensters (bzw. dauerhaft
 * im „ganztägig"-Modus) und ist ansonsten gestoppt.
 */
class BedtimeService : Service() {

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())

    // Karenz beim Abstecken: kurzes Umstecken beendet den Modus nicht.
    private val delayedOff = Runnable {
        ZenRuleManager.setActive(this, false)
        updateNotification()
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> evaluate()
                Intent.ACTION_POWER_DISCONNECTED ->
                    if (Prefs(context).ruleActive) {
                        handler.postDelayed(delayedOff, UNPLUG_GRACE_MS)
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
        ContextCompat.registerReceiver(
            this, powerReceiver, filter, ContextCompat.RECEIVER_EXPORTED
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
            // Außerhalb des Fensters gestartet (Race beim Boot o. Ä.) → beenden
            ZenRuleManager.setActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        evaluate()
        updateNotification()
        return START_STICKY
    }

    /** Soll der Modus gerade an sein? (Ladeart, Aussetzen, Ladezustand) */
    private fun evaluate() {
        val prefs = Prefs(this)
        val skipped = System.currentTimeMillis() < prefs.skipUntil
        val shouldBeOn = !skipped && currentPlugAllowed(prefs)
        if (shouldBeOn) handler.removeCallbacks(delayedOff)
        ZenRuleManager.setActive(this, shouldBeOn)
    }

    /** Lädt das Gerät gerade über eine erlaubte Ladeart? */
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
        handler.removeCallbacks(delayedOff)
        if (receiverRegistered) unregisterReceiver(powerReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
        private const val NOTIFICATION_ID = 1
        private const val UNPLUG_GRACE_MS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BedtimeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BedtimeService::class.java))
        }
    }
}
