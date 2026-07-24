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
 * Manages our AutomaticZenRule including its ZenDeviceEffects.
 *
 * This is the core of the app: since Android 15, grayscale, AOD suppression,
 * wallpaper dimming and dark mode ("night mode") are bound to an app-owned
 * zen rule via ZenDeviceEffects. We create the rule and toggle it through
 * setAutomaticZenRuleState — plain Do Not Disturb access is all that takes.
 */
object ZenRuleManager {

    private const val TAG = "PlugNap"
    val CONDITION_URI: Uri = Uri.parse("condition://io.github.georg912.plugnap/charging-bedtime")

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    /**
     * Creates the rule or updates its effects to the current settings.
     * Returns the rule id, or null without DND access.
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

        // Sensible bedtime policy: alarms & media allowed, calls only from
        // starred contacts or repeat callers.
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
            Log.e(TAG, "Failed to create/update zen rule", e)
            null
        }
    }

    /** Activates or deactivates the rule (charger in/out). */
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
            Log.i(TAG, "Zen rule ${if (active) "activated" else "deactivated"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set zen rule state", e)
        }
    }

    /** Removes the rule entirely (when the app is disabled). */
    fun removeRule(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val prefs = Prefs(context)
        prefs.ruleId?.let { runCatching { nm.removeAutomaticZenRule(it) } }
        prefs.ruleId = null
        prefs.ruleActive = false
    }
}
