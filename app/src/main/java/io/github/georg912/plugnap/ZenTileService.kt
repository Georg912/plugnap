package io.github.georg912.plugnap

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick-Settings-Kachel: schaltet den Schlafmodus manuell an/aus —
 * unabhängig vom Ladetrigger. Ein manuell aktivierter Modus wird trotzdem
 * am Fensterende (bzw. beim Abstecken, falls der Service läuft) beendet.
 */
class ZenTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!ZenRuleManager.hasDndAccess(this)) {
            // Ohne DND-Zugriff: App öffnen, dort lässt er sich erteilen.
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
