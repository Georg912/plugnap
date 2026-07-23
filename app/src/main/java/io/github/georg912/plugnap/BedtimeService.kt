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

    // Verzögerte Aktivierung: kurzes Zwischenladen löst den Modus nicht aus.
    private val delayedOn = Runnable {
        evaluate()
        updateNotification()
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    handler.removeCallbacks(delayedOn)
                    val delayMs = Prefs(context).plugInDelaySec * 1000L
                    if (delayMs == 0L) evaluate()
                    else handler.postDelayed(delayedOn, delayMs)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    handler.removeCallbacks(delayedOn)
                    val prefs = Prefs(context)
                    if (prefs.ruleActive) {
                        val graceMs = prefs.unplugGraceSec * 1000L
                        if (graceMs == 0L) delayedOff.run()
                        else handler.postDelayed(delayedOff, graceMs)
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
        handler.removeCallbacks(delayedOn)
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
        // Kanal ohne Wichtigkeit: Der FGS läuft, aber Android zeigt keine
        // Benachrichtigung an — die App-eigene "ausblenden"-Option.
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
