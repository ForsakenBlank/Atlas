package com.forsakenblank.atlas.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ui.theme.ItemColors
import com.forsakenblank.atlas.ui.theme.hex
import com.forsakenblank.atlas.ui.theme.onColor

private fun Int.toHsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(this, it) }

private fun hsvColor(h: Float, s: Float, v: Float): Color = Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

fun parseHex(text: String): Int? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) (0xFF000000 or value).toInt() else value.toInt()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    // hue and saturation live here so they survive greys and black, which have no hue of their own
    var hue by remember { mutableStateOf(color.toHsv()[0]) }
    var sat by remember { mutableStateOf(color.toHsv()[1]) }
    var value by remember { mutableStateOf(color.toHsv()[2]) }
    var hexText by remember { mutableStateOf(Color(color).hex()) }
    var mode by remember { mutableIntStateOf(0) }

    LaunchedEffect(color) {
        if (hsvColor(hue, sat, value).toArgb() != color) {
            val hsv = color.toHsv()
            if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
            if (hsv[2] > 0f) sat = hsv[1]
            value = hsv[2]
        }
        if (parseHex(hexText) != color) hexText = Color(color).hex()
    }

    fun emitHsv(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        value = v
        onChange(hsvColor(h, s, v).toArgb())
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", color = Color(color).onColor(), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text.take(9)
                    parseHex(text)?.let { parsed ->
                        val hsv = parsed.toHsv()
                        hue = hsv[0]
                        sat = hsv[1]
                        value = hsv[2]
                        onChange(parsed)
                    }
                },
                label = { Text("Hex") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Picker", "Sliders").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = mode == index,
                    onClick = { mode = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }

        if (mode == 0) {
            SaturationValuePanel(hue, sat, value) { s, v -> emitHsv(hue, s, v) }
            HueBar(hue) { h -> emitHsv(h, sat, value) }
        } else {
            val c = Color(color)
            ChannelSlider("Red", c.red, Color.Red) { onChange(c.copy(red = it).toArgb()) }
            ChannelSlider("Green", c.green, Color(0xFF2E7D32)) { onChange(c.copy(green = it).toArgb()) }
            ChannelSlider("Blue", c.blue, Color.Blue) { onChange(c.copy(blue = it).toArgb()) }
            ChannelSlider("Brightness", value, MaterialTheme.colorScheme.onSurface) { emitHsv(hue, sat, it) }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (ItemColors + listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())).forEach { swatch ->
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable {
                            val hsv = swatch.toHsv()
                            hue = hsv[0]
                            sat = hsv[1]
                            value = hsv[2]
                            onChange(swatch)
                        }
                )
            }
        }
    }
}

@Composable
private fun SaturationValuePanel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val hueColor = hsvColor(hue, 1f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun update(p: Offset) {
                    latest((p.x / size.width).coerceIn(0f, 1f), 1f - (p.y / size.height).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
    }
}

private val hueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) }

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .pointerInput(Unit) {
                fun update(p: Offset) = latest((p.x / size.width).coerceIn(0f, 1f) * 360f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(hueStops))
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun ChannelSlider(label: String, amount: Float, tint: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
        Slider(
            value = amount,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f),
        )
        Text((amount * 255).toInt().toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
    }
}

@Composable
fun CustomColorDialog(initial: Int, title: String = "Pick a colour", onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ColorPicker(
                color = picked,
                onChange = { picked = it },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
