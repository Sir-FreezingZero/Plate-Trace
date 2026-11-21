package com.example.platescanner.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.platescanner.PlateViewModel
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.data.SightingEntity
import com.example.platescanner.scan.PlateTextFilter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DotColor = Color(0xFF6B6F76)

private enum class SortMode(val label: String) { Recent("Recent"), MostSeen("Most seen") }

private sealed interface Entry {
    data class Day(val label: String, val isToday: Boolean) : Entry
    data class Item(val plate: PlateEntity) : Entry
}

/**
 * Full-screen plates page, drawn over the camera. The camera keeps running underneath, so
 * recording continues while this page is open.
 */
@Composable
fun PlatesPage(
    visible: Boolean,
    plates: List<PlateEntity>,
    recording: Boolean,
    recordingSince: Long,
    viewModel: PlateViewModel,
    onClose: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    FullPage(visible = visible) {
        AnimatedContent(
            targetState = selectedId,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "platesPage",
        ) { id ->
            if (id == null) {
                PlateTimeline(
                    plates = plates,
                    recording = recording,
                    recordingSince = recordingSince,
                    onSelect = { selectedId = it.id },
                    onBack = onClose,
                )
            } else {
                PlateDetail(
                    plateId = id,
                    recording = recording,
                    recordingSince = recordingSince,
                    viewModel = viewModel,
                    onBack = { selectedId = null },
                )
            }
        }
    }

    BackHandler(enabled = visible && selectedId != null) { selectedId = null }
    LaunchedEffect(visible) { if (!visible) selectedId = null }
}

// ---------------------------------------------------------------------------------------------
// Timeline (list of plates)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlateTimeline(
    plates: List<PlateEntity>,
    recording: Boolean,
    recordingSince: Long,
    onSelect: (PlateEntity) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SortMode.Recent) }

    val needle = PlateTextFilter.normalize(query)
    val filtered = if (needle.isEmpty()) plates else plates.filter { needle in it.plateNumber }
    val entries: List<Entry> = when (sort) {
        SortMode.Recent -> buildList {
            var lastDay: String? = null
            for (p in filtered) { // already newest first
                val (label, today) = dayLabel(p.lastSeen)
                if (label != lastDay) {
                    lastDay = label
                    add(Entry.Day(label, today))
                }
                add(Entry.Item(p))
            }
        }
        SortMode.MostSeen -> filtered
            .sortedWith(compareByDescending<PlateEntity> { it.sightingCount }.thenByDescending { it.lastSeen })
            .map { Entry.Item(it) }
    }
    val newestId = plates.firstOrNull()?.id

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Plates",
            subtitle = "${plates.size} plates · ${plates.sumOf { it.sightingCount }} sightings",
            recording = recording,
            recordingSince = recordingSince,
            onBack = onBack,
        ) {
            IconButton(onClick = {
                searching = !searching
                if (!searching) query = ""
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }

        if (searching) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (plates.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SortMode.values().forEach { mode ->
                    SmallChip(mode.label, selected = sort == mode) { sort = mode }
                }
            }
        }

        when {
            plates.isEmpty() -> EmptyState(
                "No plates yet",
                "Start recording and point the camera at a vehicle, or scan a photo.",
            )
            entries.isEmpty() -> EmptyState("No matches", "No saved plate contains \"$needle\".")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    entries,
                    key = { _, e -> if (e is Entry.Item) e.plate.id else "d_" + (e as Entry.Day).label },
                ) { i, e ->
                    val above = i > 0
                    val below = i < entries.lastIndex
                    when (e) {
                        is Entry.Day -> DayRow(e, above, below)
                        is Entry.Item -> PlateRow(
                            plate = e.plate,
                            highlight = e.plate.id == newestId,
                            lineAbove = above,
                            lineBelow = below,
                            onClick = { onSelect(e.plate) },
                        )
                    }
                }
            }
        }
    }
}

private enum class Dot { Small, Highlight, Ring, RingAccent }

/** Vertical timeline line with a dot centred on the row. */
@Composable
private fun TimelineGutter(dot: Dot, lineAbove: Boolean, lineBelow: Boolean) {
    Canvas(Modifier.width(28.dp).fillMaxHeight()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val stroke = 1.dp.toPx()
        if (lineAbove) drawLine(LineColor, Offset(cx, 0f), Offset(cx, cy), stroke)
        if (lineBelow) drawLine(LineColor, Offset(cx, cy), Offset(cx, size.height), stroke)
        val c = Offset(cx, cy)
        when (dot) {
            Dot.Small -> drawCircle(DotColor, 4.dp.toPx(), c)
            Dot.Highlight -> drawCircle(Accent, 5.dp.toPx(), c)
            Dot.Ring, Dot.RingAccent -> {
                drawCircle(PageColor, 6.dp.toPx(), c)
                drawCircle(
                    if (dot == Dot.RingAccent) Accent else DotColor,
                    5.dp.toPx(), c, style = Stroke(2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: Entry.Day, lineAbove: Boolean, lineBelow: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineGutter(if (day.isToday) Dot.RingAccent else Dot.Ring, lineAbove, lineBelow)
        Text(
            day.label.uppercase(),
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun PlateRow(
    plate: PlateEntity,
    highlight: Boolean,
    lineAbove: Boolean,
    lineBelow: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineGutter(if (highlight) Dot.Highlight else Dot.Small, lineAbove, lineBelow)
        AsyncImage(
            model = File(plate.latestVehicleImagePath ?: plate.latestImagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(start = 6.dp, top = 5.dp, bottom = 5.dp)
                .width(72.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ThumbBackground),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plate.plateNumber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
            )
            Text(
                "${relative(plate.lastSeen)} · ${plate.sightingCount}×",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FieldColor)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Plate number", color = Muted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Clear search",
                tint = Muted,
                modifier = Modifier.size(18.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Muted,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else FieldColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------------------------
// Detail (one plate)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlateDetail(
    plateId: Long,
    recording: Boolean,
    recordingSince: Long,
    viewModel: PlateViewModel,
    onBack: () -> Unit,
) {
    val plateFlow = remember(plateId) { viewModel.plate(plateId) }
    val sightingsFlow = remember(plateId) { viewModel.sightings(plateId) }
    val plate by plateFlow.collectAsState(initial = null)
    val sightings by sightingsFlow.collectAsState(initial = emptyList())
    var loaded by remember(plateId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<SightingEntity?>(null) }

    // If the plate disappears (deleted), go back to the list.
    LaunchedEffect(plate) {
        if (plate != null) loaded = true else if (loaded) onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(Modifier.weight(1f)) {
                plate?.let { PlateBadge(it.plateNumber, fontSize = 18.sp) }
            }
            if (recording) RecordingIndicator(recordingSince)
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete plate", tint = Muted)
            }
        }

        val p = plate ?: return@Column
        val latest = sightings.firstOrNull()

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ThumbBackground)
                        .clickable(enabled = latest != null) { viewing = latest },
                ) {
                    AsyncImage(
                        model = File(p.latestVehicleImagePath ?: p.latestImagePath),
                        contentDescription = "Vehicle",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (p.latestVehicleImagePath != null) {
                        AsyncImage(
                            model = File(p.latestImagePath),
                            contentDescription = "Plate",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .width(96.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            item(key = "stats") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    Stat("SEEN", "${p.sightingCount}×", Modifier.weight(1f))
                    StatDivider()
                    Stat("FIRST", shortDateTime(p.firstSeen), Modifier.weight(1.4f))
                    StatDivider()
                    Stat("LAST", relative(p.lastSeen), Modifier.weight(1.4f))
                }
            }

            item(key = "historyHeader") {
                Text(
                    "HISTORY",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            itemsIndexed(sightings, key = { _, s -> s.id }) { i, s ->
                SightingRow(
                    s,
                    lineAbove = i > 0,
                    lineBelow = i < sightings.lastIndex,
                    highlight = i == 0,
                    onClick = { viewing = s },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plate?.plateNumber ?: "plate"}?") },
            text = { Text("This removes the plate and all ${sightings.size} saved sightings.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(plateId)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    viewing?.let { s -> SightingViewer(s, plate?.plateNumber.orEmpty(), onDismiss = { viewing = null }) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(LineColor))
}

@Composable
private fun SightingRow(
    s: SightingEntity,
    lineAbove: Boolean,
    lineBelow: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineGutter(if (highlight) Dot.Highlight else Dot.Small, lineAbove, lineBelow)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(shortDateTime(s.timestamp), fontSize = 13.sp, maxLines = 1)
            Text(relative(s.timestamp), color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        if (s.vehicleImagePath != null) {
            AsyncImage(
                model = File(s.vehicleImagePath),
                contentDescription = "Vehicle",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .width(66.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ThumbBackground),
            )
            Spacer(Modifier.width(6.dp))
        }
        AsyncImage(
            model = File(s.imagePath),
            contentDescription = "Plate",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(vertical = 5.dp)
                .width(66.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ThumbBackground),
        )
    }
}

/** Plate number drawn like a real plate: white, dark border, bold monospace. */
@Composable
private fun PlateBadge(number: String, fontSize: TextUnit) {
    Text(
        number,
        color = Color(0xFF111111),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        letterSpacing = 1.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF4F4F2))
            .border(2.dp, Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** Full-screen view of one sighting: vehicle on top, plate below. Tap to close. */
@Composable
private fun SightingViewer(sighting: SightingEntity, plateNumber: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (sighting.vehicleImagePath != null) {
                AsyncImage(
                    model = File(sighting.vehicleImagePath),
                    contentDescription = "Vehicle",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                )
            }
            AsyncImage(
                model = File(sighting.imagePath),
                contentDescription = "Plate",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(6.dp)),
            )
            PlateBadge(plateNumber, fontSize = 20.sp)
            Text(shortDateTime(sighting.timestamp), color = Muted, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Time formatting
// ---------------------------------------------------------------------------------------------

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()

private fun shortDateTime(time: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(Date(time))

/** "Today", "Yesterday", or e.g. "Mon, Nov 17"; second value is true for today. */
private fun dayLabel(time: Long): Pair<String, Boolean> {
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(then, now)) return "Today" to true
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(then, now)) return "Yesterday" to false
    val pattern = if (then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
        "EEE, MMM d"
    } else {
        "EEE, MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time)) to false
}
