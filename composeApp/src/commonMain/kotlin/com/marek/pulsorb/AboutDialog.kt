// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("${AppInfo.NAME} ${AppInfo.VERSION}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("A drum machine of glowing circles. Move a circle up for a faster tempo, right for more volume, and rotate it for a higher pitch.")

                Section("Author")
                Text("© 2026 ${AppInfo.AUTHOR}")

                Section("License")
                Text(
                    "This program is free software under the ${AppInfo.LICENSE_NAME}. " +
                        "It comes with ABSOLUTELY NO WARRANTY.",
                )
                TextButton(onClick = { uriHandler.openUri(AppInfo.LICENSE_URL) }) { Text("Read the license") }
                if (AppInfo.SOURCE_URL.isNotBlank()) {
                    TextButton(onClick = { uriHandler.openUri(AppInfo.SOURCE_URL) }) { Text("Source code") }
                }

                Section("Privacy")
                Text(
                    "The app works fully offline. It collects no data, has no ads or analytics, " +
                        "and does not use the internet. Microphone audio is only used to record samples. " +
                        "Samples stay in memory on your device, and recordings are only saved when you press Record.",
                )

                Section("Made with AI")
                Text("This app was developed with the help of Claude, an AI assistant by Anthropic.")

                Section("Open-source components")
                Text(
                    "Kotlin, Compose Multiplatform, kotlinx.coroutines and AndroidX (Apache License 2.0). " +
                        "The reverb follows the public-domain Freeverb design.",
                )
            }
        },
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
}
