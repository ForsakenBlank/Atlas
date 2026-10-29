package com.forsakenblank.atlas.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.AppIcon

// background, the route line and its dot, as in the icon files
private fun colours(icon: AppIcon): Triple<Color, Color, Color> = when (icon) {
    AppIcon.DARK -> Triple(Color(0xFF121318), Color(0xFFF2F1EC), Color(0xFF7C93FF))
    AppIcon.INK -> Triple(Color(0xFF2B44C8), Color(0xFFFFFFFF), Color(0xFFB9C7FF))
    AppIcon.LIGHT -> Triple(Color(0xFFF6F5F1), Color(0xFF1B1E2B), Color(0xFF3A5BD9))
    AppIcon.SAND -> Triple(Color(0xFFE9E1D3), Color(0xFF2A2520), Color(0xFFC2562F))
}

// the atlas route icon drawn straight onto a canvas, so the picker shows exactly what you get
@Composable
fun AtlasIconPreview(icon: AppIcon, modifier: Modifier = Modifier) {
    val (bg, line, dot) = colours(icon)
    Canvas(modifier) {
        val k = size.minDimension / 1024f
        drawRoundRect(bg, cornerRadius = CornerRadius(230f * k))
        val path = Path().apply {
            moveTo(300f * k, 728f * k)
            lineTo(512f * k, 300f * k)
            lineTo(724f * k, 728f * k)
        }
        drawPath(path, line, style = Stroke(width = 66f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(dot, radius = 48f * k, center = Offset(512f * k, 600f * k))
    }
}

@Composable
fun AppIconDialog(current: AppIcon, onDismiss: () -> Unit, onPick: (AppIcon) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your home screen or taskbar may take a moment to show the new one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon.entries.forEach { icon ->
                        val chosen = icon == current
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(icon) }
                                .padding(4.dp),
                        ) {
                            AtlasIconPreview(
                                icon,
                                Modifier
                                    .size(56.dp)
                                    .border(
                                        width = if (chosen) 3.dp else 0.dp,
                                        color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .padding(if (chosen) 4.dp else 0.dp),
                            )
                            Text(
                                icon.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
