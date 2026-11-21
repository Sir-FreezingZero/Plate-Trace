package com.example.platescanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private val CooldownOptions = listOf(5 to "5 s", 15 to "15 s", 60 to "1 min", 300 to "5 min")

/** Left-hand settings panel. */
@Composable
fun SettingsPanel(
    visible: Boolean,
    plateCount: Int,
    viewModel: PlateViewModel,
    onDismiss: () -> Unit,
) {
    val cooldown by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    SidePanel(visible = visible, fromStart = true, onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Text(
            "Save the same plate again after",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        Text(
            "A plate that stays in view is saved once. It's saved again only after it has " +
                "been out of view this long.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CooldownOptions.forEach { (seconds, label) ->
                OptionChip(label, selected = cooldown == seconds) { viewModel.setCooldown(seconds) }
            }
        }

        HorizontalDivider(
            Modifier.padding(vertical = 16.dp),
            color = Color.White.copy(alpha = 0.1f),
        )

        TextButton(
            onClick = { confirmClear = true },
            enabled = plateCount > 0,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                "Delete all plates",
                color = if (plateCount > 0) RecordRed else Muted,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all plates?") },
            text = { Text("This removes all $plateCount plates and every saved image.") },
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
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
