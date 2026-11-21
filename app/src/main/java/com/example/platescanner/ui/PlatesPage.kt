package com.example.platescanner.ui

import android.text.format.DateUtils
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import kotlin.math.roundToInt
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.example.platescanner.scan.PlateTracker
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import com.example.platescanner.R
import androidx.compose.ui.res.painterResource
import com.example.platescanner.data.WatchEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.platescanner.WatchAlerts
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.data.SightingEntity
import com.example.platescanner.scan.PlateTextFilter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DotColor = Color(0xFF6B6F76)

private enum class SortMode(val label: String) {
    Recent("Recent"), MostSeen("Most seen"), Watched("Watched")
}

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
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* alerts still vibrate and show in-app if denied */ }

    val watches by viewModel.watches.collectAsStateWithLifecycle()
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33 && !WatchAlerts.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val toggleStar: (PlateEntity) -> Unit = { plate -> viewModel.setStarred(plate.id, !plate.starred) }

    // Swipe-to-delete with undo: the plate is hidden at once and really deleted ~4 s later
    // unless Undo is tapped. A new swipe commits any previous pending delete first.
    var pendingDelete by remember { mutableStateOf<PlateEntity?>(null) }
    val swipeDelete: (PlateEntity) -> Unit = { plate ->
        pendingDelete?.let { viewModel.delete(it.id) }
        pendingDelete = plate
    }
    LaunchedEffect(pendingDelete) {
        val p = pendingDelete ?: return@LaunchedEffect
        kotlinx.coroutines.delay(4_000)
        viewModel.delete(p.id)
        pendingDelete = null
    }
    val shownPlates = pendingDelete?.let { gone -> plates.filter { it.id != gone.id } } ?: plates

    val toggleWatch: (PlateEntity) -> Unit = { plate ->
        val turningOn = !plate.watched
        if (turningOn && Build.VERSION.SDK_INT >= 33 && !WatchAlerts.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setWatched(plate.id, turningOn)
    }

    FullPage(visible = visible) {
      Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedId,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "platesPage",
        ) { id ->
            if (id == null) {
                PlateTimeline(
                    plates = shownPlates,
                    onSwipeDelete = swipeDelete,
                    onDeleteMany = { ids -> ids.forEach { viewModel.delete(it) } },
                    onMerge = { intoId, fromIds -> fromIds.forEach { viewModel.merge(it, intoId) } },
                    watches = watches,
                    onAddWatch = { text ->
                        val ok = viewModel.addWatch(text)
                        if (ok) askNotifications()
                        ok
                    },
                    onRemoveWatch = viewModel::removeWatch,
                    recording = recording,
                    recordingSince = recordingSince,
                    onSelect = { selectedId = it.id },
                    onToggleWatch = toggleWatch,
                    onToggleStar = toggleStar,
                    onBack = onClose,
                )
            } else {
                PlateDetail(
                    plateId = id,
                    recording = recording,
                    recordingSince = recordingSince,
                    viewModel = viewModel,
                    onToggleWatch = toggleWatch,
                    onToggleStar = toggleStar,
                    onOpenPlate = { selectedId = it },
                    onBack = { selectedId = null },
                )
            }
        }

        pendingDelete?.let { p ->
            UndoBar(
                text = "Deleted ${p.plateNumber}",
                onUndo = { pendingDelete = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
      }
    }

    BackHandler(enabled = visible && selectedId != null) { selectedId = null }
    LaunchedEffect(visible) {
        if (!visible) {
            selectedId = null
            // Leaving the page commits a pending delete.
            pendingDelete?.let { viewModel.delete(it.id) }
            pendingDelete = null
        }
    }
}

/** Replaces the page header while plates are selected. */
@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
        }
        Text("$count selected", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onMerge, enabled = count >= 2) {
            Text("Merge", color = if (count >= 2) Color.White else Muted)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = RecordRed)
        }
    }
}

/** Choose which selected plate to keep; the others' sightings and photos move into it. */
@Composable
private fun MergeDialog(plates: List<PlateEntity>, onMerge: (Long) -> Unit, onDismiss: () -> Unit) {
    var keep by remember(plates) {
        mutableStateOf(plates.maxByOrNull { it.sightingCount }?.id ?: plates.first().id)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${plates.size} plates") },
        text = {
            Column {
                Text(
                    "Keep which plate number? The others' sightings and photos move into it.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                plates.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { keep = p.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = keep == p.id, onClick = { keep = p.id })
                        Text(
                            p.plateNumber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${p.sightingCount}×", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMerge(keep) }) { Text("Merge") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Deleted ABC1234 · Undo" bar at the bottom of the plates page. */
@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF26292E))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text("Undo", color = Accent, fontWeight = FontWeight.SemiBold) }
    }
}

/** Swipe a row right-to-left to delete it; a red background with a trash icon shows behind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(RecordRed.copy(alpha = 0.85f))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        modifier = modifier,
    ) {
        Box(Modifier.background(PanelBg)) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline (list of plates)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlateTimeline(
    plates: List<PlateEntity>,
    onSwipeDelete: (PlateEntity) -> Unit,
    onDeleteMany: (List<Long>) -> Unit,
    onMerge: (intoId: Long, fromIds: List<Long>) -> Unit,
    watches: List<WatchEntity>,
    onAddWatch: (String) -> Boolean,
    onRemoveWatch: (String) -> Unit,
    recording: Boolean,
    recordingSince: Long,
    onSelect: (PlateEntity) -> Unit,
    onToggleWatch: (PlateEntity) -> Unit,
    onToggleStar: (PlateEntity) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SortMode.Recent) }

    val needle = PlateTextFilter.normalize(query)
    val filtered = if (needle.isEmpty()) plates else plates.filter { needle in it.plateNumber }
    val entries: List<Entry> = when (sort) {
        SortMode.Recent, SortMode.Watched -> buildList {
            var lastDay: String? = null
            val list = if (sort == SortMode.Watched) filtered.filter { it.watched } else filtered
            for (p in list) { // already newest first
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

    // Multi-select: long-press a plate to start; tap to add/remove; back or ✕ to stop.
    var selection by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selection.isNotEmpty()
    var confirmDeleteMany by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selection = emptySet() }
    // Drop ids of plates that no longer exist (deleted elsewhere).
    LaunchedEffect(plates) {
        val ids = plates.map { it.id }.toSet()
        if (selection.any { it !in ids }) selection = selection.filter { it in ids }.toSet()
    }
    val waiting = if (needle.isEmpty()) watches else watches.filter { needle in it.plateNumber }
    val showWaiting = sort == SortMode.Watched && waiting.isNotEmpty()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionHeader(
                count = selection.size,
                onClose = { selection = emptySet() },
                onMerge = { confirmMerge = true },
                onDelete = { confirmDeleteMany = true },
            )
        } else {
            PageHeader(
                title = "Plates",
                subtitle = "${plates.size} plates · ${plates.sumOf { it.sightingCount }} sightings",
                recording = recording,
                recordingSince = recordingSince,
                onBack = onBack,
            ) {
                IconButton(onClick = { adding = true }) {
                    Icon(painterResource(R.drawable.ic_eye), contentDescription = "Watch for a plate")
                }
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }

        val selectedPlates = plates.filter { it.id in selection }
        if (confirmDeleteMany) {
            AlertDialog(
                onDismissRequest = { confirmDeleteMany = false },
                title = { Text("Delete ${selectedPlates.size} plates?") },
                text = { Text("Their sightings and photos are deleted too. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteMany(selectedPlates.map { it.id })
                        confirmDeleteMany = false
                        selection = emptySet()
                    }) { Text("Delete", color = RecordRed) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteMany = false }) { Text("Cancel") }
                },
            )
        }
        if (confirmMerge && selectedPlates.size >= 2) {
            MergeDialog(
                plates = selectedPlates,
                onMerge = { intoId ->
                    onMerge(intoId, selectedPlates.map { it.id }.filter { it != intoId })
                    confirmMerge = false
                    selection = emptySet()
                },
                onDismiss = { confirmMerge = false },
            )
        }
        // Full-width line under the header.
        HorizontalDivider(Modifier.fillMaxWidth().padding(top = 4.dp), color = LineColor)

        if (adding) {
            AddWatchDialog(
                onAdd = { text ->
                    if (onAddWatch(text)) {
                        adding = false
                        sort = SortMode.Watched
                        true
                    } else {
                        false
                    }
                },
                onDismiss = { adding = false },
            )
        }

        if (searching) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (plates.isNotEmpty() || watches.isNotEmpty()) {
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
            plates.isEmpty() && !showWaiting -> EmptyState(
                "No plates yet",
                "Start recording and point the camera at a vehicle, or scan a photo. " +
                    "Tap the eye icon to get an alert for a plate you haven't seen yet.",
            )
            entries.isEmpty() && !showWaiting && sort == SortMode.Watched && needle.isEmpty() -> EmptyState(
                "No watched plates",
                "Tap the bell on a plate, or the eye icon to watch for a plate you haven't seen yet.",
            )
            entries.isEmpty() && !showWaiting ->
                EmptyState("No matches", "No saved plate contains \"$needle\".")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showWaiting) {
                    item(key = "waiting_header") { GroupLabel("Waiting for") }
                    itemsIndexed(waiting, key = { _, w -> "w_" + w.plateNumber }) { wi, w ->
                        Box(Modifier.panelRow(first = wi == 0, last = wi == waiting.lastIndex)) {
                            WaitingRow(w, onRemove = { onRemoveWatch(w.plateNumber) })
                        }
                    }
                    if (entries.isNotEmpty()) {
                        item(key = "seen_header") { GroupLabel("Seen") }
                    }
                }
                itemsIndexed(
                    entries,
                    key = { _, e -> if (e is Entry.Item) e.plate.id else "d_" + (e as Entry.Day).label },
                ) { i, e ->
                    val above = i > 0
                    val below = i < entries.lastIndex
                    when (e) {
                        is Entry.Day -> DayRow(e)
                        is Entry.Item -> {
                            val id = e.plate.id
                            val row: @Composable () -> Unit = {
                                PlateRow(
                                    plate = e.plate,
                                    highlight = id == newestId,
                                    lineAbove = above,
                                    lineBelow = below,
                                    selected = id in selection,
                                    onClick = {
                                        if (selecting) {
                                            selection = if (id in selection) selection - id else selection + id
                                        } else {
                                            onSelect(e.plate)
                                        }
                                    },
                                    onLongClick = { selection = selection + id },
                                    onToggleWatch = { onToggleWatch(e.plate) },
                                    onToggleStar = { onToggleStar(e.plate) },
                                )
                            }
                            val first = i == 0 || entries[i - 1] is Entry.Day
                            val last = i == entries.lastIndex || entries[i + 1] is Entry.Day
                            // No swipe-to-delete while selecting.
                            if (selecting) {
                                Box(Modifier.panelRow(first, last)) { row() }
                            } else {
                                SwipeToDelete(
                                    onDelete = { onSwipeDelete(e.plate) },
                                    modifier = Modifier.panelRow(first, last),
                                ) { row() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Dot { Small, Highlight, Watched, Ring, RingAccent }

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
            Dot.Watched -> drawCircle(WatchAmber, 5.dp.toPx(), c)
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
private fun DayRow(day: Entry.Day) {
    GroupLabel(day.label)
}

/** Small uppercase label above a panel ("TODAY", "WAITING FOR", "HISTORY"). */
@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
    )
}

/**
 * One row of a rounded panel: rounded top corners on the first row, bottom corners on the
 * last, and a fine line above every row but the first.
 */
private fun Modifier.panelRow(first: Boolean, last: Boolean): Modifier = this
    .clip(
        RoundedCornerShape(
            topStart = if (first) 10.dp else 0.dp,
            topEnd = if (first) 10.dp else 0.dp,
            bottomStart = if (last) 10.dp else 0.dp,
            bottomEnd = if (last) 10.dp else 0.dp,
        )
    )
    .background(PanelBg)
    .drawBehind {
        if (!first) {
            val inset = 12.dp.toPx()
            drawLine(RowLine, Offset(inset, 0f), Offset(size.width - inset, 0f), 1f)
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlateRow(
    plate: PlateEntity,
    highlight: Boolean,
    lineAbove: Boolean,
    lineBelow: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleWatch: () -> Unit,
    onToggleStar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (selected) Accent.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
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
        StarButton(starred = plate.starred, onClick = onToggleStar)
        WatchButton(watched = plate.watched, onClick = onToggleWatch)
        Spacer(Modifier.width(4.dp))
    }
}

/** A typed-in plate that hasn't been seen yet; tap the bell to stop watching for it. */
@Composable
private fun WaitingRow(watch: WatchEntity, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                watch.plateNumber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
            )
            Text(
                "Not seen yet · added ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(watch.createdAt))}",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        WatchButton(watched = true, onClick = onRemove)
    }
}

/** Type a plate number to be alerted when it's first seen. */
@Composable
private fun AddWatchDialog(onAdd: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch for a plate") },
        text = {
            Column {
                Text(
                    "You'll get an alert the first time this plate is seen.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FieldColor)
                        .border(1.dp, if (error) RecordRed else LineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("e.g. ABC1234", color = Muted, fontSize = 16.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it.uppercase().take(12)
                            error = false
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White, fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { error = !onAdd(text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (error) {
                    Text(
                        "Enter 2–10 letters or numbers.",
                        color = RecordRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = !onAdd(text) }) { Text("Watch") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Star: keeps the plate (never auto-deleted). Filled white when starred. */
@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            if (starred) rememberVectorPainter(Icons.Filled.Star) else painterResource(R.drawable.ic_star_outline),
            contentDescription = if (starred) "Unstar" else "Star (keep)",
            tint = if (starred) Color.White else Muted.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Bell: filled amber when the plate is on the watch list. */
@Composable
private fun WatchButton(watched: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            if (watched) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            contentDescription = if (watched) "Stop watching" else "Watch this plate",
            tint = if (watched) WatchAmber else Muted.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
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
    onToggleWatch: (PlateEntity) -> Unit,
    onToggleStar: (PlateEntity) -> Unit,
    onOpenPlate: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val plateFlow = remember(plateId) { viewModel.plate(plateId) }
    val sightingsFlow = remember(plateId) { viewModel.sightings(plateId) }
    val plate by plateFlow.collectAsState(initial = null)
    val sightings by sightingsFlow.collectAsState(initial = emptyList())
    var loaded by remember(plateId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewingIndex by remember { mutableStateOf<Int?>(null) }
    val allPlates by viewModel.plates.collectAsStateWithLifecycle()
    val similar = plate?.let { p ->
        allPlates.filter { it.id != p.id && PlateTracker.similar(it.plateNumber, p.plateNumber, loose = true) }
    } ?: emptyList()

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
            plate?.let { p -> StarButton(starred = p.starred, onClick = { onToggleStar(p) }) }
            plate?.let { p -> WatchButton(watched = p.watched, onClick = { onToggleWatch(p) }) }
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
                        .clickable(enabled = latest != null) { viewingIndex = 0 },
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
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelBg)
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

            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(
                            "SIMILAR PLATES · LIKELY MISREADS",
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            similar.forEach { other ->
                                Text(
                                    "${other.plateNumber} · ${other.sightingCount}×",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(FieldColor)
                                        .border(1.dp, LineColor, RoundedCornerShape(6.dp))
                                        .clickable { onOpenPlate(other.id) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            val last = sightings.firstOrNull { it.latitude != null }
            item(key = "location") {
                if (last == null) {
                    Column(Modifier.padding(bottom = 2.dp)) {
                        GroupLabel("Last location")
                        Text(
                            "Not recorded yet. New sightings save it while recording with location on.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PanelBg)
                                .padding(14.dp),
                        )
                    }
                } else {
                    Column(Modifier.padding(bottom = 2.dp)) {
                        GroupLabel("Last location")
                        // Selectable so the coordinates can be copied.
                        SelectionContainer {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanelBg)
                                    .padding(14.dp),
                            ) {
                                placeText(last)?.let { Text(it, fontSize = 14.sp, lineHeight = 18.sp) }
                                Text(
                                    coordsText(last) ?: "",
                                    color = Muted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "historyHeader") { GroupLabel("History") }

            itemsIndexed(sightings, key = { _, s -> s.id }) { i, s ->
                Box(Modifier.panelRow(first = i == 0, last = i == sightings.lastIndex)) {
                    SightingRow(
                        s,
                        lineAbove = i > 0,
                        lineBelow = i < sightings.lastIndex,
                        highlight = i == 0,
                        onClick = { viewingIndex = i },
                    )
                }
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

    viewingIndex?.let { start ->
        if (sightings.isNotEmpty()) {
            SightingViewer(
                sightings = sightings,
                startIndex = start.coerceIn(0, sightings.lastIndex),
                plateNumber = plate?.plateNumber.orEmpty(),
                onDismiss = { viewingIndex = null },
            )
        }
    }
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
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "${compactDateTime(s.timestamp)} · ${relative(s.timestamp)}",
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            compactPlace(s)?.let {
                Text(it, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            coordsText(s)?.let {
                Text(
                    it,
                    color = Muted.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        if (s.vehicleImagePath != null) {
            AsyncImage(
                model = File(s.vehicleImagePath),
                contentDescription = "Vehicle",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .width(64.dp)
                    .height(46.dp)
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
                .padding(vertical = 6.dp)
                .width(64.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ThumbBackground),
        )
        Spacer(Modifier.width(10.dp))
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

/**
 * Full-screen view of a plate's sightings: car shot, plate crop with the reading, then date,
 * time, place and coordinates as plain labelled values. Swipe left/right between sightings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SightingViewer(
    sightings: List<SightingEntity>,
    startIndex: Int,
    plateNumber: String,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { sightings.size })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = PageColor, contentColor = Color.White) {
            Column(Modifier.fillMaxSize()) {
                // Top bar: close · plate · position
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    PlateBadge(plateNumber, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    if (sightings.size > 1) {
                        Text(
                            "${pager.currentPage + 1} / ${sightings.size}",
                            color = Muted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = LineColor)

                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    SightingPage(sightings[page], plateNumber)
                }
            }
        }
    }
}

@Composable
private fun SightingPage(s: SightingEntity, plateNumber: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Car shot
        if (s.vehicleImagePath != null) {
            AsyncImage(
                model = File(s.vehicleImagePath),
                contentDescription = "Car",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ThumbBackground),
            )
            Spacer(Modifier.height(10.dp))
        }

        // Plate crop + what was read
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = File(s.imagePath),
                contentDescription = "Plate",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ThumbBackground),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label("Plate")
                Text(
                    plateNumber,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (s.rawText.isNotBlank() && s.rawText != plateNumber) {
                    Text(
                        "read as ${s.rawText}",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = LineColor)

        Row {
            LabeledValue("Date", fullDate(s.timestamp), Modifier.weight(1f))
            Spacer(Modifier.width(14.dp))
            LabeledValue("Time", fullTime(s.timestamp), Modifier.weight(1f), sub = relative(s.timestamp))
        }
        Spacer(Modifier.height(16.dp))
        val place = placeText(s)
        LabeledValue(
            "Location",
            place ?: if (s.latitude != null) "Address not looked up" else "Not recorded",
            muted = place == null,
        )
        coordsText(s)?.let {
            Spacer(Modifier.height(16.dp))
            SelectionContainer {
                LabeledValue("Coordinates", it, monospace = true, sub = "Press and hold to copy")
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    muted: Boolean = false,
    sub: String? = null,
) {
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = if (muted) Muted else Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
        if (sub != null) {
            Text(sub, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
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

/** "Route 9, Toms River, New Jersey", or null if no place was looked up. */
private fun placeText(s: SightingEntity): String? =
    listOfNotNull(s.road, s.city, s.state).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }

/** "40.12345, -74.12345 ±8 m", or null if no location was saved. */
private fun coordsText(s: SightingEntity): String? {
    val lat = s.latitude ?: return null
    val lng = s.longitude ?: return null
    val acc = s.accuracyM?.let { " ±${it.roundToInt()} m" } ?: ""
    return "%.5f, %.5f".format(Locale.US, lat, lng) + acc
}

/** "Nov 21 · 1:56 PM" */
private fun compactDateTime(time: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time)) + " · " +
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(time))

/** "Sat, Nov 21, 2026" */
private fun fullDate(time: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(time))

/** "1:56:23 PM" */
private fun fullTime(time: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(Date(time))

private val RoadAbbreviations = listOf(
    "Boulevard" to "Blvd", "Avenue" to "Ave", "Street" to "St", "Road" to "Rd", "Drive" to "Dr",
    "Highway" to "Hwy", "Parkway" to "Pkwy", "Lane" to "Ln", "Court" to "Ct", "Place" to "Pl",
    "Terrace" to "Ter", "Turnpike" to "Tpke", "Expressway" to "Expy", "Circle" to "Cir",
    "North" to "N", "South" to "S", "East" to "E", "West" to "W",
)

private val StateCodes = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
    "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA",
    "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA",
    "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
    "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
    "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
    "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
    "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
    "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
    "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT",
    "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI",
    "Wyoming" to "WY", "District of Columbia" to "DC",
)

/** Short place for list rows: "Arnold Blvd, Long Beach, NJ". */
private fun compactPlace(s: SightingEntity): String? {
    val road = s.road?.split(" ")?.joinToString(" ") { word ->
        RoadAbbreviations.firstOrNull { it.first.equals(word, ignoreCase = true) }?.second ?: word
    }
    val state = s.state?.let { StateCodes[it] ?: it }
    return listOfNotNull(road, s.city, state).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
}
