package com.example.platescanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.platescanner.CaptureMode
import com.example.platescanner.PlateViewModel
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.scan.FormatMode
import com.example.platescanner.scan.PlateTextFilter
import kotlin.math.roundToInt

/**
 * Preset formats: one or more masks (L = letter, 0 = digit), example(s), where it's used.
 * A preset with several masks is one switch that turns them all on or off.
 */
private data class FormatPreset(val masks: List<String>, val example: String, val note: String) {
    constructor(mask: String, example: String, note: String) : this(listOf(mask), example, note)
}

private val FormatPresets = listOf(
    FormatPreset("LLL0000", "ABC 1234", "NY, PA, TX and many others"),
    FormatPreset("0LLL000", "1ABC234", "California"),
    FormatPreset(listOf("L00LLL", "LLL00L"), "A12 BCD · ABC 12D", "New Jersey (current and older)"),
    FormatPreset("LLL000", "ABC 123", "3 letters, 3 numbers"),
    FormatPreset("000LLL", "123 ABC", "3 numbers, 3 letters"),
    FormatPreset("LL00000", "AB 12345", "2 letters, 5 numbers"),
)

private val RowPadding = Modifier.padding(horizontal = 14.dp)

/** Full-screen settings page; recording keeps running underneath. */
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
    val showLive by viewModel.showLive.collectAsStateWithLifecycle()
    val agree by viewModel.agreeReads.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val photoInterval by viewModel.photoIntervalMs.collectAsStateWithLifecycle()
    val detectThreshold by viewModel.detectThreshold.collectAsStateWithLifecycle()
    val readThreshold by viewModel.readThreshold.collectAsStateWithLifecycle()
    val autoDelete by viewModel.autoDelete.collectAsStateWithLifecycle()
    val saveLocation by viewModel.saveLocation.collectAsStateWithLifecycle()
    val autoDeleteHours by viewModel.autoDeleteHours.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val sightings = plates.sumOf { it.sightingCount }
    val specific = rules.mode == FormatMode.SPECIFIC
    val photos = captureMode == CaptureMode.PHOTO

    FullPage(visible = visible) {
        PageHeader(
            title = "Settings",
            subtitle = null,
            recording = recording,
            recordingSince = recordingSince,
            onBack = onClose,
        )
        // Full-width line under the header.
        HorizontalDivider(Modifier.fillMaxWidth().padding(top = 4.dp), color = LineColor)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {

            Section("Capture") {
                ToggleRow(
                    title = "Use photos instead of video",
                    description = "Full-resolution photos: sharper and reads further, but fewer per second.",
                    checked = photos,
                    onChange = { viewModel.setCaptureMode(if (it) CaptureMode.PHOTO else CaptureMode.VIDEO) },
                )
                ToggleRow(
                    title = "Save location",
                    description = "Coordinates with each sighting; road, city, state when online. " +
                        if (saveLocation) "Now: ${remember(visible) { viewModel.locationTracker.statusText() }}" else "",
                    checked = saveLocation,
                    onChange = viewModel::setSaveLocation,
                )
                SliderRow(
                    title = "Photo interval",
                    description = "Time between photos in photo mode.",
                    value = photoInterval / 1000f,
                    range = 0.5f..3f,
                    step = 0.5f,
                    unit = "s",
                    decimals = 1,
                    enabled = photos,
                    onChange = { viewModel.setPhotoInterval((it * 1000).roundToInt().toLong()) },
                )
            }

            Section("Reading") {
                SliderRow(
                    title = "Plate detection confidence",
                    description = "Lower finds more plates, including distant ones and false boxes.",
                    value = detectThreshold.toFloat(),
                    range = 5f..95f,
                    step = 5f,
                    unit = "%",
                    decimals = 0,
                    onChange = { viewModel.setDetectThreshold(it.roundToInt()) },
                )
                SliderRow(
                    title = "Reading confidence",
                    description = "Every character must be read at least this surely.",
                    value = readThreshold.toFloat(),
                    range = 5f..95f,
                    step = 5f,
                    unit = "%",
                    decimals = 0,
                    onChange = { viewModel.setReadThreshold(it.roundToInt()) },
                )
                SliderRow(
                    title = "Matching readings to save",
                    description = "Identical reads needed. 2 is faster, 3+ is more accurate.",
                    value = agree.toFloat(),
                    range = 2f..5f,
                    step = 1f,
                    unit = "",
                    decimals = 0,
                    onChange = { viewModel.setAgreeReads(it.roundToInt()) },
                )
                SliderRow(
                    title = "Save same plate again after",
                    description = "Time out of view before it can be saved again.",
                    value = cooldown.toFloat(),
                    range = 5f..300f,
                    step = 5f,
                    unit = "s",
                    decimals = 0,
                    onChange = { viewModel.setCooldown(it.roundToInt()) },
                )
            }

            Section("Plate format") {
                ChoiceRow(
                    title = "Accept",
                    description = when (rules.mode) {
                        FormatMode.US -> "Letters and numbers both required. Blocks phone numbers."
                        FormatMode.SPECIFIC -> "Only the formats switched on below. Fixes look-alikes (0/O, 8/B)."
                        FormatMode.ANY -> "Any plate-like text, including phone numbers and signs."
                    },
                    options = listOf("US plates", "Specific", "Any"),
                    selected = when (rules.mode) {
                        FormatMode.US -> 0
                        FormatMode.SPECIFIC -> 1
                        FormatMode.ANY -> 2
                    },
                    onSelect = {
                        viewModel.setFormatMode(
                            when (it) { 0 -> FormatMode.US; 1 -> FormatMode.SPECIFIC; else -> FormatMode.ANY }
                        )
                    },
                )
                SubLabel(if (specific) "Formats" else "Formats (used with Specific)", enabled = specific)
                FormatPresets.forEach { preset ->
                    ToggleRow(
                        title = preset.example,
                        description = preset.note,
                        monospaceTitle = true,
                        checked = preset.masks.all { it in rules.masks },
                        enabled = specific,
                        onChange = { on -> preset.masks.forEach { viewModel.setMaskEnabled(it, on) } },
                    )
                }
                val presetMasks = FormatPresets.flatMap { it.masks }.toSet()
                rules.masks.filter { it !in presetMasks }.forEach { mask ->
                    ToggleRow(
                        title = mask,
                        description = "Custom",
                        monospaceTitle = true,
                        checked = true,
                        enabled = specific,
                        onChange = { viewModel.setMaskEnabled(mask, it) },
                    )
                }
                AddFormatRow(enabled = specific, onAdd = { viewModel.setMaskEnabled(it, true) })
                if (specific && rules.masks.isEmpty()) {
                    Text(
                        "No formats on: nothing will be saved.",
                        color = RecordRed,
                        fontSize = 11.sp,
                        modifier = RowPadding.padding(bottom = 4.dp),
                    )
                }
            }

            Section("Saved images") {
                SliderRow(
                    title = "Car shot width",
                    description = "How far the car photo zooms out. The plate crop is unaffected.",
                    value = scale,
                    range = 2f..9f,
                    step = 0.5f,
                    unit = "×",
                    decimals = 1,
                    trailing = { v -> FramingPreview(v) },
                    onChange = viewModel::setVehicleScale,
                )
            }

            Section("Display") {
                ToggleRow(
                    title = "Show live readings",
                    description = "Boxes and readings over the camera while recording.",
                    checked = showLive,
                    onChange = viewModel::setShowLive,
                )
            }

            Section("Storage") {
                ToggleRow(
                    title = "Auto-delete old plates",
                    description = "Plates with a bell or star are always kept.",
                    checked = autoDelete,
                    onChange = viewModel::setAutoDelete,
                )
                DurationSliderRow(
                    title = "Delete after",
                    description = "Time since last seen. Type e.g. 5h or 10d.",
                    hours = autoDeleteHours,
                    enabled = autoDelete,
                    onChange = viewModel::setAutoDeleteHours,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = plates.isNotEmpty()) { confirmClear = true }
                        .then(RowPadding)
                        .padding(vertical = 10.dp)
                        .alpha(if (plates.isNotEmpty()) 1f else 0.38f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowText(
                        title = "Delete all plates",
                        description = "${plates.size} plates · $sightings sightings · on this phone only",
                        titleColor = RecordRed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AppVersion()
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
// Building blocks
// ---------------------------------------------------------------------------------------------

/** Section label, then its rows in a rounded panel with fine lines between them. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title.uppercase(),
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PanelBg),
        ) {
            DividedColumn(content)
        }
    }
}

/** Stacks its children and draws a fine line between each pair (inset from the sides). */
@Composable
private fun DividedColumn(content: @Composable () -> Unit) {
    val lineYs = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val inset = 14.dp.toPx()
                for (y in lineYs) {
                    drawLine(RowLine, Offset(inset, y.toFloat()), Offset(size.width - inset, y.toFloat()), 1f)
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            lineYs.clear()
            var y = 0
            placeables.forEachIndexed { i, p ->
                if (i > 0 && p.height > 0) lineYs.add(y)
                p.place(0, y)
                y += p.height
            }
        }
    }
}

/** Small label grouping the rows under it within a section. */
@Composable
private fun SubLabel(text: String, enabled: Boolean = true) {
    Text(
        text,
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = RowPadding
            .padding(top = 6.dp, bottom = 2.dp)
            .alpha(if (enabled) 1f else 0.6f),
    )
}

/** App name and version, centred at the bottom of the page. */
@Composable
private fun AppVersion() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    Text(
        "Plate Scanner $version",
        color = Muted.copy(alpha = 0.5f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun RowText(
    title: String,
    description: String?,
    monospaceTitle: Boolean = false,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
) {
    Column(modifier) {
        Text(
            title,
            color = titleColor,
            fontSize = 14.sp,
            fontWeight = if (monospaceTitle) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (monospaceTitle) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
        )
        if (description != null) {
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean = true,
    monospaceTitle: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .then(RowPadding)
            .padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, description, monospaceTitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Accent,
                disabledCheckedThumbColor = Color.Black,
                disabledCheckedTrackColor = Accent,
            ),
        )
    }
}

/** Slider with an editable number box on its right. */
@Composable
private fun SliderRow(
    title: String,
    description: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    decimals: Int,
    enabled: Boolean = true,
    trailing: (@Composable (Float) -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    fun snap(v: Float): Float {
        val snapped = ((v - range.start) / step).roundToInt() * step + range.start
        return snapped.coerceIn(range.start, range.endInclusive)
    }
    var current by remember(value) { mutableFloatStateOf(value) }

    Column(
        Modifier
            .fillMaxWidth()
            .then(RowPadding)
            .padding(top = 8.dp, bottom = 2.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, description, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing(current)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = current,
                onValueChange = { current = snap(it) },
                onValueChangeFinished = { onChange(current) },
                valueRange = range,
                steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Accent,
                    inactiveTrackColor = FieldColor,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.White,
                    disabledActiveTrackColor = Accent,
                    disabledInactiveTrackColor = FieldColor,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).height(32.dp),
            )
            Spacer(Modifier.width(10.dp))
            NumberBox(
                value = current,
                decimals = decimals,
                unit = unit,
                enabled = enabled,
                onCommit = {
                    val v = snap(it)
                    current = v
                    onChange(v)
                },
            )
        }
    }
}

/** Slider stops, in hours: hours first, then days, then weeks/months. */
private val DurationStops = listOf(
    1, 2, 3, 4, 6, 8, 12, 18,
    24, 36, 48, 72, 96, 120,
    168, 336, 504, 672,
    720, 1080, 1440, 2160,
)

/** "6 h", "36 h", "2 d", "30 d". Whole days show as days; everything else as hours. */
private fun formatDuration(hours: Int): String =
    if (hours >= 24 && hours % 24 == 0) "${hours / 24} d" else "$hours h"

/** "5h", "5 h", "10d", "1.5d"; a bare number uses [defaultUnitDays] to pick the unit. */
private fun parseDuration(text: String, defaultUnitDays: Boolean): Int? {
    val t = text.trim().lowercase().replace(',', '.')
    val m = Regex("^([0-9]*\\.?[0-9]+)\\s*([hd]?)").find(t) ?: return null
    val n = m.groupValues[1].toFloatOrNull() ?: return null
    val days = when (m.groupValues[2]) { "d" -> true; "h" -> false; else -> defaultUnitDays }
    return (if (days) n * 24 else n).roundToInt().takeIf { it >= 1 }
}

/** Slider over [DurationStops] with an editable "6 h" / "2 d" box on the right. */
@Composable
private fun DurationSliderRow(
    title: String,
    description: String,
    hours: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    fun nearestIndex(h: Int) = DurationStops.indices.minByOrNull { kotlin.math.abs(DurationStops[it] - h) } ?: 0
    var index by remember(hours) { mutableFloatStateOf(nearestIndex(hours).toFloat()) }
    var shown by remember(hours) { mutableStateOf(hours) }
    var text by remember(shown) { mutableStateOf(formatDuration(shown)) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun commitText() {
        val parsed = parseDuration(text, defaultUnitDays = shown >= 24 && shown % 24 == 0)
        if (parsed != null) {
            shown = parsed.coerceIn(1, DurationStops.last())
            index = nearestIndex(shown).toFloat()
            onChange(shown)
        }
        text = formatDuration(shown)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(RowPadding)
            .padding(top = 6.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        RowText(title, description)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = index,
                onValueChange = {
                    index = it.roundToInt().toFloat()
                    shown = DurationStops[index.toInt()]
                    text = formatDuration(shown)
                },
                onValueChangeFinished = { onChange(shown) },
                valueRange = 0f..(DurationStops.size - 1).toFloat(),
                steps = DurationStops.size - 2,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Accent,
                    inactiveTrackColor = FieldColor,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.White,
                    disabledActiveTrackColor = Accent,
                    disabledInactiveTrackColor = FieldColor,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).height(32.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(68.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(FieldColor)
                    .border(1.dp, if (focused) Accent else LineColor, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                    ),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (focused && !it.isFocused) commitText()
                            focused = it.isFocused
                        },
                )
            }
        }
    }
}

/** Small editable number field; commits on Done or when it loses focus. */
@Composable
private fun NumberBox(value: Float, decimals: Int, unit: String, enabled: Boolean, onCommit: (Float) -> Unit) {
    fun format(v: Float) = if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(v)
    var text by remember(value) { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun commit() {
        val parsed = text.replace(',', '.').toFloatOrNull()
        if (parsed != null) onCommit(parsed) else text = format(value)
    }

    Row(
        Modifier
            .width(68.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(FieldColor)
            .border(1.dp, if (focused) Accent else LineColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    if (focused && !it.isFocused) commit()
                    focused = it.isFocused
                },
        )
        if (unit.isNotEmpty()) {
            Text(unit, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp))
        }
    }
}

/** Title with a compact segmented choice on the right, description below. */
@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().then(RowPadding).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(6.dp)),
            ) {
                options.forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (i == selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (i == selected) Color.White else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Text(description, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun AddFormatRow(enabled: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val cleaned = PlateTextFilter.cleanMask(text)

    Row(
        Modifier
            .fillMaxWidth()
            .then(RowPadding)
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(FieldColor)
                .border(1.dp, LineColor, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    "Custom format, e.g. LL0L000 (L letter, 0 number, * either)",
                    color = Muted, fontSize = 11.sp, maxLines = 1,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it.uppercase().take(9) },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Add",
            color = if (enabled && cleaned != null) Accent else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(enabled = enabled && cleaned != null) {
                    cleaned?.let(onAdd)
                    text = ""
                }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

/**
 * Tiny sketch of the car shot at [scale]: the 5:4 frame, a rough car outline and the plate,
 * centred, at the size used for cropping. Approximate (assumes a car rear ≈ 6× plate width).
 */
@Composable
private fun FramingPreview(scale: Float) {
    Box(
        Modifier
            .size(width = 50.dp, height = 40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ThumbBackground)
            .border(1.dp, LineColor, RoundedCornerShape(4.dp)),
    ) {
        Canvas(Modifier.size(width = 50.dp, height = 40.dp)) {
            val w = size.width
            val h = size.height
            val plateW = w / scale
            val plateH = plateW / 2f
            val cx = w / 2f
            val cy = h / 2f
            val carW = plateW * 6f
            val carH = carW * 0.55f
            clipRect {
                drawRoundRect(
                    Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(cx - carW / 2f, cy - carH * 0.62f),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carW * 0.06f),
                )
                drawRect(
                    Color(0xFFF4F4F2),
                    topLeft = Offset(cx - plateW / 2f, cy - plateH / 2f),
                    size = Size(plateW, plateH),
                )
            }
        }
    }
}
