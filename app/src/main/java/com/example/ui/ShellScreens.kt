package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SkyBlueBackground
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SkyBlueSurfaceBorder
import com.example.ui.theme.SkyBlueTextSecondary

@Composable
fun VianshieldHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLogKeeper: () -> Unit
) {
    var previewRunning by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = SkyBlueBackground) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Vianshield",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Firewall",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyBlueTextSecondary
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (previewRunning) "PREVIEW ON" else "PROTECTION OFF",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (previewRunning) "Preview is active" else "Your firewall is stopped",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "The VPN engine is intentionally not started in this UI phase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyBlueTextSecondary
                    )
                    Button(
                        onClick = { previewRunning = !previewRunning },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (previewRunning) "Stop preview" else "Start preview")
                    }
                    Text(
                        text = "Preview only — no network traffic is protected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SkyBlueTextSecondary
                    )
                }
            }

            Text(
                text = "Quick access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedButton(
                onClick = onOpenLogKeeper,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Log Keeper")
                Spacer(Modifier.width(8.dp))
                Text("View diagnostic logs", color = SkyBlueTextSecondary)
            }

            Text(
                text = "Firewall controls and engine integration will be enabled in a later phase.",
                style = MaterialTheme.typography.bodySmall,
                color = SkyBlueTextSecondary
            )
        }
    }
}

@Composable
fun VianshieldSettingsScreen(onBack: () -> Unit) {
    var dnsFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var blockOnScreenOff by rememberSaveable { mutableStateOf(false) }

    Scaffold(containerColor = SkyBlueBackground) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Core preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PreferenceRow(
                title = "DNS ad & tracker filtering",
                description = "UI preview only; filtering is not active.",
                checked = dnsFilterEnabled,
                onCheckedChange = { dnsFilterEnabled = it }
            )
            HorizontalDivider(color = SkyBlueSurfaceBorder)
            PreferenceRow(
                title = "Block when screen is off",
                description = "UI preview only; this does not change traffic.",
                checked = blockOnScreenOff,
                onCheckedChange = { blockOnScreenOff = it }
            )
            HorizontalDivider(color = SkyBlueSurfaceBorder)
            SettingRow(
                title = "DNS provider",
                description = "Automatic (not connected)"
            )
            HorizontalDivider(color = SkyBlueSurfaceBorder)
            SettingRow(
                title = "Per-app rules",
                description = "Not configured in this UI phase"
            )
            HorizontalDivider(color = SkyBlueSurfaceBorder)
            SettingRow(
                title = "Speed limit",
                description = "Not active in this UI phase"
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "These controls are placeholders. No VPN, DNS filtering, app blocking, or speed limiting is active from this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = SkyBlueTextSecondary
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = SkyBlueTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = SkyBlueTextSecondary)
    }
}
