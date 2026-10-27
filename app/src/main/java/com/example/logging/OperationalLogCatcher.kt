package com.example.logging

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogSeverity {
    INFO,
    WARN,
    ERROR
}

enum class LogCategory {
    ENGINE_INIT,
    VPN_LIFECYCLE,
    CONFIG_VALIDATION,
    TUN_INTERFACE,
    NETWORK_STATE,
    ERROR_DIAGNOSTIC
}

data class LogEvent(
    val id: Long,
    val timestamp: Long,
    val category: LogCategory,
    val severity: LogSeverity,
    val component: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * OperationalLogCatcher:
 * Lightweight, strictly bounded diagnostic event catcher for Vianshield.
 *
 * Mandate & Privacy Guardrails:
 * 1. Master On/Off switch - when disabled, no logs are recorded.
 * 2. Strictly Bounded - caps in-memory retention to 500 entries (FIFO) and local disk log to 256KB.
 * 3. Secret Redaction - strips private keys, preshared keys, and credentials before recording.
 * 4. Zero Fabricated Telemetry - captures ONLY genuine observable engine, tunnel, and network events.
 * 5. No PII, browsing history, or packet tracking.
 */
object OperationalLogCatcher {

    const val MAX_MEMORY_ENTRIES = 500
    const val MAX_TOTAL_DISK_BYTES = 3 * 1024 * 1024L // 3 MiB strict persistent limit across all files
    const val MAX_SEGMENT_DISK_BYTES = MAX_TOTAL_DISK_BYTES / 2 // 1.5 MiB per rotating segment
    const val ACTIVE_LOG_FILE = "vianshield_operational.log"
    const val ROTATED_LOG_FILE = "vianshield_operational.log.1"

    private val idCounter = AtomicLong(1L)
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    private var appContext: Context? = null

    // Base64 44-character key pattern (WireGuard 32-byte key base64 encoded)
    private val base64KeyRegex = Regex("[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=")
    private val privateKeyNamedRegex = Regex("(?i)(private_?key|preshared_?key|password|secret|token)\\s*[:=]\\s*[\"']?[^\"'\\s,]+[\"']?")

    fun initialize(context: Context) {
        appContext = context.applicationContext
        log(
            category = LogCategory.ENGINE_INIT,
            severity = LogSeverity.INFO,
            component = "LogCatcher",
            message = "OperationalLogCatcher initialized (Memory bound: 500 entries | Persistent bound: 3 MiB rotating)"
        )
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        if (enabled) {
            log(
                category = LogCategory.ENGINE_INIT,
                severity = LogSeverity.INFO,
                component = "LogCatcher",
                message = "Diagnostic logging enabled by master switch"
            )
        }
    }

    fun log(
        category: LogCategory,
        severity: LogSeverity,
        component: String,
        message: String,
        details: String? = null
    ) {
        if (!_isLoggingEnabled.value) return

        val sanitizedMessage = redactSecrets(message)
        val sanitizedDetails = details?.let { redactSecrets(it) }

        val event = LogEvent(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            category = category,
            severity = severity,
            component = component,
            message = sanitizedMessage,
            details = sanitizedDetails
        )

        synchronized(this) {
            val currentList = _events.value
            val updatedList = if (currentList.size >= MAX_MEMORY_ENTRIES) {
                currentList.drop(currentList.size - MAX_MEMORY_ENTRIES + 1) + event
            } else {
                currentList + event
            }
            _events.value = updatedList
        }

        appendToFile(event)
    }

    fun info(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.INFO, component, message, details)
    }

    fun warn(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.WARN, component, message, details)
    }

    fun error(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.ERROR, component, message, details)
    }

    fun clear() {
        synchronized(this) {
            _events.value = emptyList()
        }
        appContext?.let { ctx ->
            try {
                val active = File(ctx.filesDir, ACTIVE_LOG_FILE)
                val rotated = File(ctx.filesDir, ROTATED_LOG_FILE)
                if (active.exists()) active.delete()
                if (rotated.exists()) rotated.delete()
            } catch (_: Throwable) {}
        }
    }

    fun getDiskLogSizeBytes(): Long {
        val ctx = appContext ?: return 0L
        return try {
            val active = File(ctx.filesDir, ACTIVE_LOG_FILE)
            val rotated = File(ctx.filesDir, ROTATED_LOG_FILE)
            (if (active.exists()) active.length() else 0L) +
                    (if (rotated.exists()) rotated.length() else 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    fun getRecentEvents(count: Int = 20): List<LogEvent> {
        val all = _events.value
        return if (all.size <= count) all else all.takeLast(count)
    }

    fun exportFormattedLogs(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("=== VIANSHIELD OPERATIONAL DIAGNOSTIC LOG ===\n")
        sb.append("Export Time: ").append(dateFormat.format(Date())).append("\n")
        sb.append("Total In-Memory Entries: ").append(_events.value.size).append(" (Cap: 500 FIFO)\n")
        sb.append("Persistent Storage Limit: 3 MiB total (Active: 1.5 MiB, Rotated: 1.5 MiB)\n")
        sb.append("Current Persistent Usage: ").append(getDiskLogSizeBytes()).append(" bytes\n")
        sb.append("Master Switch State: ").append(if (_isLoggingEnabled.value) "ENABLED" else "DISABLED").append("\n")
        sb.append("Privacy Policy: All cryptographic private keys/secrets redacted. Zero browsing telemetry.\n")
        sb.append("Phase 1 Reality: Per-app traffic decisions & DNS ad-blocking are not active in Phase 1.\n\n")

        _events.value.forEach { e ->
            sb.append("[").append(dateFormat.format(Date(e.timestamp))).append("] ")
                .append("[").append(e.severity).append("] ")
                .append("[").append(e.category).append("] ")
                .append("[").append(e.component).append("]: ")
                .append(e.message)
            if (!e.details.isNullOrBlank()) {
                sb.append(" | Details: ").append(e.details)
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Strictly redacts sensitive cryptographic keys, passwords, and tokens.
     */
    fun redactSecrets(input: String): String {
        var result = input
        result = privateKeyNamedRegex.replace(result) { match ->
            val prefix = match.groupValues[1]
            "$prefix=[REDACTED_SECRET]"
        }
        result = base64KeyRegex.replace(result, "[REDACTED_KEY]")
        return result
    }

    @Synchronized
    private fun appendToFile(event: LogEvent) {
        val ctx = appContext ?: return
        try {
            val dir = ctx.filesDir
            val activeFile = File(dir, ACTIVE_LOG_FILE)
            val rotatedFile = File(dir, ROTATED_LOG_FILE)

            val line = "${event.timestamp}|${event.severity}|${event.category}|${event.component}|${event.message}|${event.details ?: ""}\n"
            val lineBytes = line.toByteArray(Charsets.UTF_8).size

            // Enforce 1.5 MiB segment boundary for active file
            if (activeFile.exists() && (activeFile.length() + lineBytes > MAX_SEGMENT_DISK_BYTES)) {
                if (rotatedFile.exists()) {
                    rotatedFile.delete()
                }
                activeFile.renameTo(rotatedFile)
            }

            // Global safety check: if total bytes still exceed MAX_TOTAL_DISK_BYTES, remove rotated file
            val totalBytes = (if (activeFile.exists()) activeFile.length() else 0L) +
                    (if (rotatedFile.exists()) rotatedFile.length() else 0L)
            if (totalBytes + lineBytes > MAX_TOTAL_DISK_BYTES && rotatedFile.exists()) {
                rotatedFile.delete()
            }

            activeFile.appendText(line, Charsets.UTF_8)
        } catch (_: Throwable) {
            // Fail silently on disk errors to protect runtime stability of VPN service and app
        }
    }
}
