package io.github.rin.passkey.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Line icons on a 24dp grid with a 1.8 stroke and round caps and joins, so they read as one set.
/** The app's line icons. They are drawn in one color and tinted by [top.yukonga.miuix.kmp.basic.Icon]. */
object AppIcons {
    /** A person with a key: the passkey provider. */
    val Passkey: ImageVector by lazy { lineIcon("Passkey", listOf("M13 7.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0z", "M3.5 20c0-3.3 2.7-6 6-6c1.2 0 2.3.3 3.2.9", "M20.5 12.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z", "M18 15v6M18 18.5h2")) }
    /** A filled-in password field: the autofill service. */
    val Autofill: ImageVector by lazy { lineIcon("Autofill", listOf("M5 6.5h14A2.5 2.5 0 0 1 21.5 9v6a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 15V9A2.5 2.5 0 0 1 5 6.5z", "M16 9.5v5"), fill = listOf("M8.25 12a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 1 1 2.5 0z", "M12.25 12a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 1 1 2.5 0z")) }
    /** A crossed-out field: no autofill app. */
    val AutofillOff: ImageVector by lazy { lineIcon("AutofillOff", listOf("M5 6.5h14A2.5 2.5 0 0 1 21.5 9v6a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 15V9A2.5 2.5 0 0 1 5 6.5z", "M3.5 3.5l17 17")) }
    /** Connected and authorized. */
    val ShieldCheck: ImageVector by lazy { lineIcon("ShieldCheck", listOf("M12 3l7.5 3v5.5c0 4.5-3.1 8.4-7.5 9.5c-4.4-1.1-7.5-5-7.5-9.5V6z", "M8.8 12l2.3 2.3l4.3-4.6")) }
    /** Shizuku needs to be connected. */
    val Plug: ImageVector by lazy { lineIcon("Plug", listOf("M9 3v4M15 3v4", "M6.5 7h11v3.5a5.5 5.5 0 0 1-11 0z", "M12 16v5")) }
    /** Several providers stay enabled. */
    val Layers: ImageVector by lazy { lineIcon("Layers", listOf("M12 3.5l8.5 4.25L12 12L3.5 7.75z", "M3.5 12L12 16.25L20.5 12", "M3.5 16.25L12 20.5L20.5 16.25")) }
    /** Autofill follows the passkey provider. */
    val Sync: ImageVector by lazy { lineIcon("Sync", listOf("M4.6 10.7A7.5 7.5 0 0 1 17.75 7.18", "M17.75 3.3v3.88h-3.88", "M19.4 13.3A7.5 7.5 0 0 1 6.25 16.82", "M6.25 20.7v-3.88h3.88")) }
    /** Turn each provider on or off. */
    val Toggles: ImageVector by lazy { lineIcon("Toggles", listOf("M7.5 4.5h9a3.5 3.5 0 0 1 0 7h-9a3.5 3.5 0 0 1 0-7z", "M7.5 12.5h9a3.5 3.5 0 0 1 0 7h-9a3.5 3.5 0 0 1 0-7z"), fill = listOf("M18.1 8a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z", "M9.1 16a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z")) }
    /** Remove unavailable entries. */
    val Trash: ImageVector by lazy { lineIcon("Trash", listOf("M4.5 6.5h15", "M9.5 6.5V5a1.5 1.5 0 0 1 1.5-1.5h2A1.5 1.5 0 0 1 14.5 5v1.5", "M6.5 6.5l.8 12.1a2 2 0 0 0 2 1.9h5.4a2 2 0 0 0 2-1.9l.8-12.1", "M10.2 10.5v5.5M13.8 10.5v5.5")) }
    /** Restore the saved configuration. */
    val Restore: ImageVector by lazy { lineIcon("Restore", listOf("M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 8.9", "M4.5 4.8v4.1h4.1", "M12 8.2V12l2.6 1.6")) }
    /** Command output from dumpsys. */
    val Terminal: ImageVector by lazy { lineIcon("Terminal", listOf("M5 4.5h14A2.5 2.5 0 0 1 21.5 7v10a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 17V7A2.5 2.5 0 0 1 5 4.5z", "M7 9.5l2.5 2.5L7 14.5", "M12 14.5h4.5")) }
    /** Configuration values. */
    val ListDetails: ImageVector by lazy { lineIcon("ListDetails", listOf("M9 7h11", "M9 12h11", "M9 17h7"), fill = listOf("M6.25 7a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 1 1 2.5 0z", "M6.25 12a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 1 1 2.5 0z", "M6.25 17a1.25 1.25 0 1 1-2.5 0a1.25 1.25 0 1 1 2.5 0z")) }
    /** System settings. */
    val Phone: ImageVector by lazy { lineIcon("Phone", listOf("M8 2.5h8a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2z", "M10.5 18.5h3")) }
    /** A password manager. */
    val Lock: ImageVector by lazy { lineIcon("Lock", listOf("M8 10.5V8a4 4 0 0 1 8 0v2.5", "M7 10.5h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M12 14.5v2")) }
    /** A hardware security key. */
    val SecurityKey: ImageVector by lazy { lineIcon("SecurityKey", listOf("M9.5 8.5V3.5h5v5", "M8.5 8.5h7A1.5 1.5 0 0 1 17 10v8a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3v-8a1.5 1.5 0 0 1 1.5-1.5z", "M13.8 14.5a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z")) }
    /** Help and information. */
    val Info: ImageVector by lazy { lineIcon("Info", listOf("M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", "M12 11v5"), fill = listOf("M13.15 7.9a1.15 1.15 0 1 1-2.3 0a1.15 1.15 0 1 1 2.3 0z")) }
    /** Something needs attention. */
    val Alert: ImageVector by lazy { lineIcon("Alert", listOf("M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", "M12 7.5v5.2"), fill = listOf("M13.15 16.1a1.15 1.15 0 1 1-2.3 0a1.15 1.15 0 1 1 2.3 0z")) }
    /** Read the configuration again. */
    val Refresh: ImageVector by lazy { lineIcon("Refresh", listOf("M19.5 12a7.5 7.5 0 1 1-2.2-5.3l2.2 2.2", "M19.5 4.6v4.3h-4.3")) }
    /** More pages. */
    val More: ImageVector by lazy { lineIcon("More", listOf(), fill = listOf("M7.1 12a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z", "M13.6 12a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z", "M20.1 12a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z")) }
    /** Go back. */
    val Back: ImageVector by lazy { lineIcon("Back", listOf("M15 5l-7 7l7 7")) }
    /** Opens a detail. */
    val Chevron: ImageVector by lazy { lineIcon("Chevron", listOf("M9.5 6l6 6l-6 6")) }
    /** Opens another app. */
    val External: ImageVector by lazy { lineIcon("External", listOf("M8 16L16 8", "M9.5 8H16v6.5")) }
    /** The selected item. */
    val Check: ImageVector by lazy { lineIcon("Check", listOf("M5 12.5l4.5 4.5L19 7.5")) }
    /** Search. */
    val Search: ImageVector by lazy { lineIcon("Search", listOf("M17 10.5a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0z", "M15.5 15.5L20 20")) }
    /** Close. */
    val Close: ImageVector by lazy { lineIcon("Close", listOf("M6.5 6.5l11 11M17.5 6.5l-11 11")) }
    /** Copy. */
    val Copy: ImageVector by lazy { lineIcon("Copy", listOf("M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2")) }
}

private fun lineIcon(name: String, stroke: List<String>, fill: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        stroke.forEach {
            addPath(
                addPathNodes(it), stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fill.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()
