package io.github.georg912.plugnap

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy
import android.util.Log

/**
 * Verwaltet unsere AutomaticZenRule inklusive ZenDeviceEffects.
 *
 * Das ist der Kern der App: Seit Android 15 sind Graustufen, AOD-Unterdrückung,
 * Wallpaper-Dimmen und Dark Mode („Night Mode") über ZenDeviceEffects an eine
 * app-eigene Zen-Regel gebunden. Wir legen die Regel an und schalten sie per
 * setAutomaticZenRuleState — dafür genügt der „Bitte nicht stören"-Zugriff.
 */
object ZenRuleManager {

    private const val TAG = "ZenDock"
    val CONDITION_URI: Uri = Uri.parse("condition://io.github.georg912.plugnap/charging-bedtime")

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    /**
     * Legt die Regel an bzw. aktualisiert die Effekte nach den aktuellen
     * Einstellungen. Gibt die Regel-ID zurück, oder null ohne DND-Zugriff.
     */
    fun ensureRule(context: Context): String? {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return null
        val prefs = Prefs(context)

        val effects = ZenDeviceEffects.Builder()
            .setShouldDisplayGrayscale(prefs.grayscale)
            .setShouldSuppressAmbientDisplay(prefs.suppressAmbient)
            .setShouldDimWallpaper(prefs.dimWallpaper)
            .setShouldUseNightMode(prefs.nightMode)
            .build()

        // Sinnvolle Bedtime-Richtlinie: Wecker & Medien erlaubt, Anrufe nur von
        // markierten Kontakten bzw. wiederholten Anrufern.
        val policy = ZenPolicy.Builder()
            .allowAlarms(true)
            .allowMedia(true)
            .allowSystem(false)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
            .allowRepeatCallers(true)
            .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
            .allowEvents(false)
            .allowReminders(false)
            .build()

        val rule = AutomaticZenRule.Builder(
            context.getString(R.string.zen_rule_name), CONDITION_URI
        )
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setDeviceEffects(effects)
            .setZenPolicy(policy)
            .setInterruptionFilter(prefs.interruptionFilter)
            .setManualInvocationAllowed(true)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setTriggerDescription(context.getString(R.string.zen_trigger_description))
            .setIconResId(R.drawable.ic_bedtime)
            .build()

        val existingId = prefs.ruleId?.takeIf { runCatching { nm.getAutomaticZenRule(it) }.getOrNull() != null }
        return try {
            if (existingId != null) {
                nm.updateAutomaticZenRule(existingId, rule)
                existingId
            } else {
                nm.addAutomaticZenRule(rule).also { prefs.ruleId = it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zen-Regel konnte nicht angelegt/aktualisiert werden", e)
            null
        }
    }

    /** Aktiviert oder deaktiviert die Regel (Ladegerät an/ab). */
    fun setActive(context: Context, active: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val id = ensureRule(context) ?: return
        val summary = context.getString(
            if (active) R.string.condition_active else R.string.condition_inactive
        )
        val condition = Condition(
            CONDITION_URI,
            summary,
            if (active) Condition.STATE_TRUE else Condition.STATE_FALSE,
            Condition.SOURCE_CONTEXT
        )
        try {
            nm.setAutomaticZenRuleState(id, condition)
            Prefs(context).ruleActive = active
            Log.i(TAG, "Zen-Regel ${if (active) "aktiviert" else "deaktiviert"}")
        } catch (e: Exception) {
            Log.e(TAG, "Zen-Regel-Zustand konnte nicht gesetzt werden", e)
        }
    }

    /** Entfernt die Regel vollständig (beim Deaktivieren der App). */
    fun removeRule(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val prefs = Prefs(context)
        prefs.ruleId?.let { runCatching { nm.removeAutomaticZenRule(it) } }
        prefs.ruleId = null
        prefs.ruleActive = false
    }
}
