package com.forsakenblank.atlas.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.forsakenblank.atlas.data.IconStyle
import com.forsakenblank.atlas.ui.theme.LocalSettings

// every icon in Atlas is drawn through here, so Settings -> Appearance -> Icon style swaps the whole set.
// only the everyday outlined icons change; ones filled on purpose (a filled star for a favourite) stay as they are
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val style = LocalSettings.current.iconStyle
    val vector = remember(imageVector, style) { restyle(imageVector, style) }
    androidx.compose.material3.Icon(vector, contentDescription, modifier, tint)
}

private val styled = HashMap<String, ImageVector?>()

// Icons.Outlined.Home -> Icons.Rounded.Home and so on. the icon sets are kotlin extension properties, one
// class per icon, so the same name is looked up in the other set (and remembered)
fun restyle(icon: ImageVector, style: IconStyle): ImageVector {
    if (style == IconStyle.OUTLINED || !icon.name.startsWith("Outlined.")) return icon
    val name = icon.name.removePrefix("Outlined.")
    val key = "${style.pkg}.$name"
    val found = synchronized(styled) {
        styled.getOrPut(key) {
            runCatching {
                val holder: Any = when (style) {
                    IconStyle.ROUNDED -> Icons.Rounded
                    IconStyle.SHARP -> Icons.Sharp
                    IconStyle.FILLED -> Icons.Filled
                    IconStyle.TWO_TONE -> Icons.TwoTone
                    IconStyle.OUTLINED -> Icons.Outlined
                }
                val cls = Class.forName("androidx.compose.material.icons.${style.pkg}.${name}Kt")
                cls.getMethod("get$name", holder.javaClass).invoke(null, holder) as? ImageVector
            }.getOrNull()
        }
    }
    return found ?: icon
}
