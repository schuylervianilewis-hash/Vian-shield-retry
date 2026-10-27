package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.logging.LogCategory
import com.example.logging.LogEvent
import com.example.logging.LogSeverity
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusStarting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogKeeperScreen(
    viewModel: DiagnosticViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val events by viewModel.filteredLogEvents.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val timeFilter by viewModel.selectedTimeFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSeverity by viewModel.selectedSeverity.collectAsState()
    val newestFirst by viewModel.sortNewestFirst.collectAsState()
    val loggingEnabled by viewModel.isLoggingEnabled.collectAsState()
    val pendingCrashCount by viewModel.pendingCrashCount.collectAsState()
    val hasPendingCrashes by viewModel.hasPendingCrashReports.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Logs & diagnostics") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { viewModel.toggleSortOrder() }) {
                        Text(if (newestFirst) "Newest" else "Oldest")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search logs") }
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeFilter.values().forEach { filter ->
                    FilterChip(
                        selected = timeFilter == filter,
                        onClick = { viewModel.setTimeFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedSeverity == null,
                    onClick = { viewModel.setSeverityFilter(null) },
                    label = { Text("All levels") }
                )
                LogSeverity.values().forEach { severity ->
                    FilterChip(
                        selected = selectedSeverity == severity,
                        onClick = { viewModel.setSeverityFilter(severity) },
                        label = { Text(severity.name) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("All categories") }
                )
                LogCategory.values().forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.setCategoryFilter(category) },
                        label = { Text(category.name.replace('_', ' ')) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = loggingEnabled, onCheckedChange = viewModel::setLoggingEnabled)
                    Column {
                        Text("Diagnostic logging")
                        Text(
                            if (loggingEnabled) "Enabled" else "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val result = viewModel.exportLogsToDownloads()
                        val message = result.getOrElse { "Export failed: ${it.message}" }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }) { Text("Export") }
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            }

            if (hasPendingCrashes) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("$pendingCrashCount crash report(s) saved locally")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val result = viewModel.exportCrashReportsToDownloads()
                                val message = result.getOrElse { "Export failed: ${it.message}" }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }) { Text("Export crash reports") }
                            TextButton(onClick = { viewModel.dismissCrashReports() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            Text(
                text = "${events.size} event(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    LogEventCard(event)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear operational logs?") },
            text = { Text("This clears the current in-memory and rotating operational logs. Saved crash reports are separate.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    confirmClear = false
                }) { Text("Clear logs") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LogEventCard(event: LogEvent) {
    val severityColor = when (event.severity) {
        LogSeverity.ERROR -> StatusError
        LogSeverity.WARN -> StatusStarting
        LogSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${event.severity} · ${event.category.name.replace('_', ' ')}",
                style = MaterialTheme.typography.labelLarge,
                color = severityColor
            )
            Text(
                text = "${event.formattedTime} · ${event.component}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
            if (!event.details.isNullOrBlank()) {
                Text(
                    text = event.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
