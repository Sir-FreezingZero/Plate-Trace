package com.example.platescanner.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardColor = Color.White.copy(alpha = 0.06f)
private val Hairline = Color.White.copy(alpha = 0.10f)

private enum class SortMode(val label: String) { Recent("Recent"), MostSeen("Most seen") }

/** Right-hand slide-out panel: plate list, and a detail view for one plate. */
@Composable
fun PlatesPanel(
    visible: Boolean,
    plates: List<PlateEntity>,
    viewModel: PlateViewModel,
    onDismiss: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    SidePanel(visible = visible, fromStart = false, onDismiss = onDismiss) {
        AnimatedContent(
            targetState = selectedId,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "panel",
        ) { id ->
            if (id == null) {
                PlateList(
                    plates = plates,
                    onSelect = { selectedId = it.id },
                    onClose = onDismiss,
                )
            } else {
                PlateDetail(
                    plateId = id,
                    viewModel = viewModel,
                    onBack = { selectedId = null },
                )
            }
        }
    }

    BackHandler(enabled = visible && selectedId != null) { selectedId = null }

    // Always reopen on the list.
    LaunchedEffect(visible) { if (!visible) selectedId = null }
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlateList(
    plates: List<PlateEntity>,
    onSelect: (PlateEntity) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortMode.Recent) }

    val needle = PlateTextFilter.normalize(query)
    val filtered = if (needle.isEmpty()) plates else plates.filter { needle in it.plateNumber }
    val shown = when (sort) {
        SortMode.Recent -> filtered // already newest first
        SortMode.MostSeen -> filtered.sortedWith(
            compareByDescending<PlateEntity> { it.sightingCount }.thenByDescending { it.lastSeen }
        )
    }
    val totalSightings = plates.sumOf { it.sightingCount }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Plates", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (plates.isEmpty()) "Nothing saved yet"
                    else "${plates.size} plates · $totalSightings sightings",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        if (plates.isNotEmpty()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortMode.values().forEach { mode ->
                    SmallChip(mode.label, selected = sort == mode) { sort = mode }
                }
            }
        }

        when {
            plates.isEmpty() -> EmptyState(
                "No plates yet",
                "Tap the record button and point the camera at a vehicle, or scan a photo.",
            )
            shown.isEmpty() -> EmptyState("No matches", "No saved plate contains \"$needle\".")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (sort == SortMode.Recent) {
                    // Group under day headings: Today, Yesterday, Mon, Nov 17 …
                    var lastDay: String? = null
                    for (plate in shown) {
                        val day = dayLabel(plate.lastSeen)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "h_$day") { DayHeader(day) }
                        }
                        item(key = plate.id) { PlateCard(plate, onClick = { onSelect(plate) }) }
                    }
                } else {
                    items(shown, key = { it.id }) { plate ->
                        PlateCard(plate, onClick = { onSelect(plate) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search plates", color = Muted, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Clear search",
                tint = Muted,
                modifier = Modifier.size(20.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label.uppercase(),
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** One plate: car photo with the plate crop inset, plate number, and when it was seen. */
@Composable
private fun PlateCard(plate: PlateEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).height(84.dp)) {
            AsyncImage(
                model = File(plate.latestVehicleImagePath ?: plate.latestImagePath),
                contentDescription = "Vehicle",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThumbBackground),
            )
            if (plate.latestVehicleImagePath != null) {
                AsyncImage(
                    model = File(plate.latestImagePath),
                    contentDescription = "Plate",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .width(56.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                        .background(ThumbBackground),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            PlateBadge(plate.plateNumber, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(relative(plate.lastSeen), color = Color.White, fontSize = 13.sp, maxLines = 1)
            Text(
                if (plate.sightingCount == 1) "Seen once" else "Seen ${plate.sightingCount} times",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
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
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFF4F4F2))
            .border(2.dp, Color(0xFF2B2B2B), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlateDetail(plateId: Long, viewModel: PlateViewModel, onBack: () -> Unit) {
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
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete plate")
            }
        }

        val p = plate ?: return@Column
        val latest = sightings.firstOrNull()

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero: latest vehicle shot with the plate crop inset.
            item(key = "hero") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
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
                                .padding(10.dp)
                                .width(110.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }

            item(key = "title") {
                Column(horizontalAlignment = Alignment.Start) {
                    PlateBadge(p.plateNumber, fontSize = 24.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Seen", "${p.sightingCount}×", Modifier.weight(1f))
                        StatTile("First", shortDate(p.firstSeen), Modifier.weight(1f))
                        StatTile("Last", relative(p.lastSeen), Modifier.weight(1f))
                    }
                }
            }

            item(key = "historyHeader") {
                Text(
                    "HISTORY",
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }

            items(sightings, key = { it.id }) { s ->
                SightingCard(s, onClick = { viewing = s })
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
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** One sighting in the history: time, car shot and plate crop side by side. */
@Composable
private fun SightingCard(s: SightingEntity, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(
            absolute(s.timestamp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.vehicleImagePath != null) {
                AsyncImage(
                    model = File(s.vehicleImagePath),
                    contentDescription = "Vehicle",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1.3f)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThumbBackground),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    // Same height as the 4:3 vehicle image beside it (which is 1.3x as wide).
                    .aspectRatio(if (s.vehicleImagePath != null) 1f / (1.3f * 3f / 4f) else 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThumbBackground),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(s.imagePath),
                    contentDescription = "Plate",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
            AsyncImage(
                model = File(sighting.imagePath),
                contentDescription = "Plate",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(8.dp)),
            )
            PlateBadge(plateNumber, fontSize = 20.sp)
            Text(absolute(sighting.timestamp), color = Muted, fontSize = 14.sp)
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

private fun absolute(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

private fun shortDate(time: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))

private fun dayLabel(time: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(then, now)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(then, now)) return "Yesterday"
    val pattern = if (then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
        "EEE, MMM d"
    } else {
        "EEE, MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}
