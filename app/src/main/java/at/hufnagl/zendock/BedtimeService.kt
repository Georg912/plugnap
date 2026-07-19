package at.hufnagl.zendock

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime

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

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> ZenRuleManager.setActive(context, true)
                Intent.ACTION_POWER_DISCONNECTED -> ZenRuleManager.setActive(context, false)
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
        // Aktuellen Ladezustand auswerten — wichtig beim Start des Fensters,
        // beim Boot und nach einem Neustart des Service durch das System.
        val charging = getSystemService(BatteryManager::class.java).isCharging
        val now = LocalTime.now().hour * 60 + LocalTime.now().minute
        val inWindow = prefs.allDay ||
            Prefs.inWindow(now, prefs.windowStart, prefs.windowEnd)
        if (inWindow) {
            ZenRuleManager.setActive(this, charging)
        } else if (!prefs.allDay) {
            // Außerhalb des Fensters gestartet (Race beim Boot o. Ä.) → beenden
            ZenRuleManager.setActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
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
        val text = when {
            prefs.ruleActive -> getString(R.string.notification_active)
            prefs.allDay -> getString(R.string.notification_waiting_allday)
            else -> getString(
                R.string.notification_waiting,
                String.format("%02d:%02d", prefs.windowEnd / 60, prefs.windowEnd % 60)
            )
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bedtime)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "bedtime_watch"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BedtimeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BedtimeService::class.java))
        }
    }
}
