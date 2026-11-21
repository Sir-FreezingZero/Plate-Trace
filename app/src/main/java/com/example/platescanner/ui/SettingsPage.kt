package com.example.platescanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.platescanner.PlateViewModel
import com.example.platescanner.data.PlateEntity

private val CooldownOptions = listOf(5 to "5 s", 15 to "15 s", 60 to "1 min", 300 to "5 min")

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

            Divider()
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
