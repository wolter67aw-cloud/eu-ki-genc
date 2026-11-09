package eu.kigenc.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import eu.kigenc.app.R

/** Ein wählbares App-Symbol für den Startbildschirm. */
data class AppIcon(val id: String, val label: String, val alias: String, @DrawableRes val preview: Int)

/**
 * Wechselt das Symbol auf dem Startbildschirm. Technisch ist jedes Symbol ein „activity-alias“ im Manifest;
 * genau einer davon ist aktiv. „.MainActivity“ ist das Standard-Symbol.
 */
object AppIcons {
    val all = listOf(
        AppIcon("standard", "KI-GenC", "eu.kigenc.app.MainActivity", R.drawable.logo_kigenc),
        AppIcon("genc", "GenC Hologramm", "eu.kigenc.app.IconGenC", R.drawable.symbol_genc),
        AppIcon("vielfalt", "Vielfalt", "eu.kigenc.app.IconVielfalt", R.drawable.symbol_vielfalt),
        AppIcon("vegan", "Vegan", "eu.kigenc.app.IconVegan", R.drawable.symbol_vegan),
        AppIcon("pride", "Pride", "eu.kigenc.app.IconPride", R.drawable.symbol_pride),
        AppIcon("zweisamkeit", "Zweisamkeit", "eu.kigenc.app.IconZweisamkeit", R.drawable.symbol_zweisamkeit),
        AppIcon("klassisch", "Klassisch", "eu.kigenc.app.IconKlassisch", R.drawable.symbol_klassisch),
    )

    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return all.firstOrNull { icon ->
            val state = pm.getComponentEnabledSetting(ComponentName(context.packageName, icon.alias))
            when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // Standardzustand laut Manifest: nur das Standard-Symbol ist aktiv
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.id == "standard"
                else -> false
            }
        } ?: all[0]
    }

    /** Aktiviert das gewählte Symbol und deaktiviert alle anderen. Der Startbildschirm aktualisiert sich nach einigen Sekunden. */
    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val target = all.firstOrNull { it.id == id } ?: return
        // Erst das neue aktivieren, dann die anderen ausschalten – so gibt es nie „kein Symbol“
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, target.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        all.filter { it.id != id }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, it.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }
}
