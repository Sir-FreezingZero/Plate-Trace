package com.example.platescanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.platescanner.PlateViewModel
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.scan.FormatMode
import com.example.platescanner.scan.PlateTextFilter
import kotlin.math.roundToInt

private val CooldownOptions = listOf(5 to "5 s", 15 to "15 s", 60 to "1 min", 300 to "5 min")

/** Preset formats: mask (L = letter, 0 = digit), example, where it's used. */
private data class FormatPreset(val mask: String, val example: String, val note: String)

private val FormatPresets = listOf(
    FormatPreset("LLL0000", "ABC 1234", "NY, PA, TX and many other states"),
    FormatPreset("0LLL000", "1ABC234", "California"),
    FormatPreset("L00LLL", "A12 BCD", "New Jersey"),
    FormatPreset("LLL000", "ABC 123", "3 letters, 3 numbers"),
    FormatPreset("000LLL", "123 ABC", "3 numbers, 3 letters"),
    FormatPreset("LL00000", "AB 12345", "2 letters, 5 numbers"),
)

private const val MIN_SCALE = 3f
private const val MAX_SCALE = 12f

/** Full-screen settings page; like the plates page, recording keeps running underneath. */
@Composable
fun SettingsPage(
    visible: Boolean,
    plates: List<PlateEntity>,
    recording: Boolean,
    recordingSince: Long,
    viewModel: PlateViewModel,
    onClose: () -> Unit,
) {
    val cooldown by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    val rules by viewModel.formatRules.collectAsStateWithLifecycle()
    val scale by viewModel.vehicleScale.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val sightings = plates.sumOf { it.sightingCount }

    FullPage(visible = visible) {
        PageHeader(
            title = "Settings",
            subtitle = null,
            recording = recording,
            recordingSince = recordingSince,
            onBack = onClose,
        )

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // --- Plate format -------------------------------------------------------------
            SectionLabel("Plate format")
            ChoiceRow(
                title = "US plates",
                body = "Must have both letters and numbers. Blocks phone numbers; " +
                    "may still catch some sign text.",
                selected = rules.mode == FormatMode.US,
                onClick = { viewModel.setFormatMode(FormatMode.US) },
            )
            ChoiceRow(
                title = "Only specific formats",
                body = "Strictest. Only the formats ticked below are saved, and look-alike " +
                    "characters (0/O, 8/B, 5/S) are corrected to fit.",
                selected = rules.mode == FormatMode.SPECIFIC,
                onClick = { viewModel.setFormatMode(FormatMode.SPECIFIC) },
            )
            if (rules.mode == FormatMode.SPECIFIC) {
                FormatList(
                    masks = rules.masks,
                    onToggle = viewModel::setMaskEnabled,
                )
            }
            ChoiceRow(
                title = "Any plate-like text",
                body = "Most permissive. Will also save phone numbers and sign text.",
                selected = rules.mode == FormatMode.ANY,
                onClick = { viewModel.setFormatMode(FormatMode.ANY) },
            )

            Divider()

            // --- Capture --------------------------------------------------------------
            SectionLabel("Capture")
            SettingBlock(
                title = "Save the same plate again after",
                body = "A plate is saved once while it stays in view. It's saved again only " +
                    "after it hasn't been seen for this long.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CooldownOptions.forEach { (seconds, label) ->
                        OptionChip(label, selected = cooldown == seconds) {
                            viewModel.setCooldown(seconds)
                        }
                    }
                }
            }

            SettingBlock(
                title = "Car shot framing",
                body = "How far the car photo zooms out around the plate. The plate crop " +
                    "itself always stays tight. Applies to new sightings.",
            ) {
                FramingControl(
                    scale = scale,
                    onScaleChange = viewModel::setVehicleScale,
                )
            }

            Divider()

            // --- Storage --------------------------------------------------------------
            SectionLabel("Storage")
            SettingBlock(
                title = "Saved data",
                body = "${plates.size} plates · $sightings sightings · stored on this phone only",
            )
            Text(
                "Delete all plates",
                color = if (plates.isNotEmpty()) RecordRed else Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = plates.isNotEmpty()) { confirmClear = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all plates?") },
            text = { Text("This removes all ${plates.size} plates and every saved image.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.deleteAll()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Plate format
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChoiceRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Muted),
        )
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(body, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Preset formats as checkboxes, plus any custom formats, plus a field to add one. */
@Composable
private fun FormatList(masks: List<String>, onToggle: (String, Boolean) -> Unit) {
    val presetMasks = FormatPresets.map { it.mask }.toSet()
    val custom = masks.filter { it !in presetMasks }

    Column(Modifier.padding(start = 44.dp, end = 20.dp, bottom = 8.dp)) {
        FormatPresets.forEach { preset ->
            CheckRow(
                label = preset.example,
                note = preset.note,
                checked = preset.mask in masks,
                onChange = { onToggle(preset.mask, it) },
            )
        }
        custom.forEach { mask ->
            CheckRow(
                label = mask,
                note = "Custom",
                checked = true,
                onChange = { onToggle(mask, it) },
            )
        }
        AddCustomFormat(onAdd = { onToggle(it, true) })
        if (masks.isEmpty()) {
            Text(
                "No formats ticked: nothing will be saved.",
                color = RecordRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Accent,
                uncheckedColor = Muted,
                checkmarkColor = Color.Black,
            ),
        )
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(note, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun AddCustomFormat(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cleaned = PlateTextFilter.cleanMask(text)

    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FieldColor)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) Text("Custom, e.g. LL0L000", color = Muted, fontSize = 13.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.uppercase().take(9) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = {
                    cleaned?.let(onAdd)
                    text = ""
                },
                enabled = cleaned != null,
            ) { Text("Add", color = if (cleaned != null) Accent else Muted) }
        }
        Text(
            "L = letter, 0 = number, * = either",
            color = if (text.isNotEmpty() && cleaned == null) RecordRed else Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Car shot framing
// ---------------------------------------------------------------------------------------------

@Composable
private fun FramingControl(scale: Float, onScaleChange: (Float) -> Unit) {
    // Local value while dragging; saved when the drag ends.
    var value by remember(scale) { mutableFloatStateOf(scale) }

    Column {
        FramingPreview(
            scale = value,
            modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tighter", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f× plate width".format(value),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text("Wider", color = Muted, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = { value = (it * 2).roundToInt() / 2f }, // 0.5 steps
            onValueChangeFinished = { onScaleChange(value) },
            valueRange = MIN_SCALE..MAX_SCALE,
            steps = ((MAX_SCALE - MIN_SCALE) * 2).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Accent,
                inactiveTrackColor = FieldColor,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/**
 * Sketch of what the car shot will contain at [scale]: the frame (same 5:4 shape as the saved
 * photo), a rough car outline, and the plate at the size and position used for cropping.
 * Approximate: assumes a typical car rear about 8× as wide as the plate's lettering.
 */
@Composable
private fun FramingPreview(scale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(5f / 4f)
            .clip(RoundedCornerShape(6.dp))
            .background(ThumbBackground)
            .border(1.dp, LineColor, RoundedCornerShape(6.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val textW = w / scale              // plate lettering width
            val plateW = textW * 1.35f         // whole plate
            val plateH = plateW / 2f
            val plateCx = w / 2f
            val plateCy = h * 0.62f            // same placement as cropVehicle()

            val carW = textW * 8f
            val carH = carW * 0.55f
            val carLeft = plateCx - carW / 2f
            val carTop = plateCy - carH * 0.62f

            clipRect {
                // Car body
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(carLeft, carTop),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carW * 0.06f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(carLeft, carTop),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carW * 0.06f),
                    style = Stroke(1.dp.toPx()),
                )
                // Rear window
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(carLeft + carW * 0.18f, carTop + carH * 0.08f),
                    size = Size(carW * 0.64f, carH * 0.30f),
                    cornerRadius = CornerRadius(carW * 0.03f),
                )
                // Plate
                drawRoundRect(
                    color = Color(0xFFF4F4F2),
                    topLeft = Offset(plateCx - plateW / 2f, plateCy - plateH / 2f),
                    size = Size(plateW, plateH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingBlock(
    title: String,
    body: String,
    control: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(body, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (control != null) {
            Spacer(Modifier.height(10.dp))
            control()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(top = 12.dp), color = LineColor)
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else FieldColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
