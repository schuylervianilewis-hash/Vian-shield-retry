package com.example.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vpn.VpnState

/**
 * Minimal home screen. Full diagnostics and log search remain on their own screen.
 * No profile is silently substituted: the user must import a WireGuard config first.
 */
@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel,
    onOpenLogKeeper: () -> Unit
) {
    val context = LocalContext.current
    val vpnState by viewModel.vpnState.collectAsState()
    val currentConfig by viewModel.currentConfig.collectAsState()
    val importedProfilePreview by viewModel.importedProfilePreview.collectAsState()
    val importError by viewModel.importError.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpn()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadConfigFile(context, uri)
    }

    val statusText = when (vpnState) {
        is VpnState.Connected -> "Connected"
        is VpnState.Starting -> "Connecting"
        is VpnState.Stopping -> "Disconnecting"
        is VpnState.Error -> "Needs attention"
        is VpnState.Stopped -> "Disconnected"
    }
    val statusColor = when (vpnState) {
        is VpnState.Connected -> MaterialTheme.colorScheme.primary
        is VpnState.Starting, is VpnState.Stopping -> MaterialTheme.colorScheme.secondary
        is VpnState.Error -> MaterialTheme.colorScheme.error
        is VpnState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val busy = vpnState is VpnState.Starting || vpnState is VpnState.Stopping
    val connected = vpnState is VpnState.Connected

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Vianshield",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Firewall and VPN protection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Protection status",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = statusColor
                    )
                    when (val state = vpnState) {
                        is VpnState.Connected -> {
                            Text(
                                text = state.endpoint,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is VpnState.Starting -> Text(state.stage, style = MaterialTheme.typography.bodySmall)
                        is VpnState.Error -> Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(
                            text = if (currentConfig.isBlank()) {
                                "Import a WireGuard profile before connecting."
                            } else {
                                "Your imported WireGuard profile is ready."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.toggleVpn(vpnPermissionLauncher) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && (connected || currentConfig.isNotBlank())
            ) {
                Text(if (connected) "Disconnect" else if (busy) "Please wait…" else "Connect")
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentConfig.isBlank()) "Import WireGuard profile" else "Change WireGuard profile")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("WireGuard profile", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (currentConfig.isBlank()) "Not configured" else "Profile imported",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = onOpenLogKeeper,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logs & diagnostics")
            }
        }
    }

    if (importedProfilePreview != null) {
        val profile = importedProfilePreview!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = { Text("Review WireGuard profile") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.toDisplaySummary().forEach { (label, value) ->
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImportedProfile() }) {
                    Text("Use profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImportDialog() }) {
                    Text("Cancel")
                }
            }
        )
    } else if (importError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = { Text("Profile or connection issue") },
            text = { Text(importError ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}
