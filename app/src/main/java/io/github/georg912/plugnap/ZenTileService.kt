package io.github.georg912.plugnap

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick-settings tile: toggles the bedtime mode manually — independent of
 * the charging trigger. A manually activated mode is still ended at the
 * window end (or on unplug while the service is running).
 */
class ZenTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!ZenRuleManager.hasDndAccess(this)) {
            // Without DND access: open the app, where it can be granted.
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
            return
        }
        ZenRuleManager.setActive(this, !Prefs(this).ruleActive)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (Prefs(this).ruleActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
