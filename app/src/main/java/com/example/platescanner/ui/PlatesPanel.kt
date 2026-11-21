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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import java.util.Date


/** Right-hand slide-out panel shown over the camera. */
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

@Composable
private fun PlateList(
    plates: List<PlateEntity>,
    onSelect: (PlateEntity) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = PlateTextFilter.normalize(query)
    val shown = if (needle.isEmpty()) plates else plates.filter { needle in it.plateNumber }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plates", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("${plates.size}", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        if (plates.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (plates.isEmpty()) "No plates yet.\nPoint the camera at a vehicle."
                    else "No matches.",
                    color = Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { plate ->
                    PlateRow(plate, onClick = { onSelect(plate) })
                }
            }
        }
    }
}

@Composable
private fun PlateRow(plate: PlateEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(plate.latestImagePath),
            contentDescription = plate.plateNumber,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(110.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ThumbBackground),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plate.plateNumber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
            )
            Text(
                "${plate.sightingCount}× · ${relative(plate.lastSeen)}",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

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
            Text(
                plate?.plateNumber ?: "",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete plate")
            }
        }

        plate?.let { p ->
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoLine("Sightings", p.sightingCount.toString())
                InfoLine("First seen", absolute(p.firstSeen))
                InfoLine("Last seen", absolute(p.lastSeen))
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = Color.White.copy(alpha = 0.1f))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sightings, key = { it.id }) { s ->
                Column {
                    AsyncImage(
                        model = File(s.imagePath),
                        contentDescription = "Plate image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThumbBackground)
                            .clickable { viewing = s },
                    )
                    Text(
                        absolute(s.timestamp),
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${plate?.plateNumber ?: "plate"}?") },
            text = { Text("This removes the plate and all ${sightings.size} saved images.") },
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

    viewing?.let { s -> ImageViewer(s, onDismiss = { viewing = null }) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, fontSize = 14.sp)
    }
}

/** Full-screen view of a single saved crop; tap anywhere to close. */
@Composable
private fun ImageViewer(sighting: SightingEntity, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(sighting.imagePath),
                contentDescription = "Plate image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Text(
                absolute(sighting.timestamp),
                color = Muted,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            )
        }
    }
}

private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()

private fun absolute(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
