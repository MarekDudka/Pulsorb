// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PresetsDialog(
    builtIn: List<Preset>,
    saved: List<SavedPreset>,
    onLoad: (Preset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (SavedPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val exists = saved.any { it.fileName == presetFileName(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Presets") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Heading("Save current circles")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(name.trim()); name = "" }, enabled = name.isNotBlank()) {
                        Text(if (exists) "Replace" else "Save")
                    }
                }
                Text(
                    "Microphone samples are not saved; sample circles load empty.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Heading("My presets")
                if (saved.isEmpty()) {
                    Text("No saved presets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (s in saved) {
                    PresetRow(s.preset, onLoad = { onLoad(s.preset) }, onDelete = { onDelete(s) })
                }

                Heading("Built-in")
                for (p in builtIn) PresetRow(p, onLoad = { onLoad(p) })
            }
        },
    )
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PresetRow(preset: Preset, onLoad: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(preset.name, fontWeight = FontWeight.Medium)
            val details = preset.description.ifBlank { "${preset.circles.size} circles" }
            Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onLoad) { Text("Load") }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("✕", color = Color(0xFFFF8A80)) }
        }
    }
}
