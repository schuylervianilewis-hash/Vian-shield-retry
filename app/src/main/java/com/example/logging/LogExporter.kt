package com.example.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogExporter:
 * Exports diagnostic logs and crash dumps to the Android Downloads directory.
 *
 * Privacy and Permission Architecture:
 * 1. Zero Broad Storage Permissions: Uses Android 10+ (API 29+) MediaStore.Downloads.EXTERNAL_CONTENT_URI.
 * 2. Graceful Compatibility Fallback: Uses app external files or public downloads directory when MediaStore is unavailable.
 * 3. Secret Redaction: Cryptographic keys, secrets, tokens, and passwords are fully redacted.
 * 4. System Metadata: Records export timestamp, app version, Android OS version, and device info.
 */
object LogExporter {

    fun exportOperationalLogs(context: Context, events: List<LogEvent>): Result<String> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Vianshield_Logs_$timeStamp.txt"

        val content = buildString {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val appVer = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.0"
            } catch (_: Throwable) {
                "1.0.0"
            }

            append("====================================================\n")
            append("      VIANSHIELD OPERATIONAL DIAGNOSTIC EXPORT      \n")
            append("====================================================\n")
            append("Export Timestamp:   ").append(dateFormat.format(Date())).append("\n")
            append("Application:        Vianshield v").append(appVer).append("\n")
            append("OS Platform:        Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Device:             ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("Engine Pinned:      sing-box v1.11.0\n")
            append("Total Events:       ").append(events.size).append("\n")
            append("Persistent Disk:    ").append(OperationalLogCatcher.getDiskLogSizeBytes()).append(" bytes (Cap: 3 MiB)\n")
            append("Memory Bound:       500 entries FIFO\n")
            append("Privacy Notice:     All cryptographic keys and credentials have been strictly redacted.\n")
            append("Phase 1 Reality:    Per-app routing decisions and DNS ad-blocking decisions are unobserved in Phase 1.\n")
            append("====================================================\n\n")

            events.forEach { e ->
                append("[").append(dateFormat.format(Date(e.timestamp))).append("] ")
                    .append("[").append(e.severity).append("] ")
                    .append("[").append(e.category).append("] ")
                    .append("[").append(e.component).append("]: ")
                    .append(e.message)
                if (!e.details.isNullOrBlank()) {
                    append(" | Details: ").append(e.details)
                }
                append("\n")
            }
            append("\n=== END OF OPERATIONAL LOG EXPORT ===\n")
        }

        return writeToDownloads(context, fileName, OperationalLogCatcher.redactSecrets(content))
    }

    fun exportCrashReports(context: Context, reports: List<CrashReport>): Result<String> {
        if (reports.isEmpty()) {
            return Result.failure(IllegalStateException("No pending crash reports to export"))
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Vianshield_CrashReports_$timeStamp.txt"

        val content = buildString {
            append("====================================================\n")
            append("        VIANSHIELD CRASH DIAGNOSTIC EXPORT          \n")
            append("====================================================\n")
            append("Export Timestamp: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())).append("\n")
            append("Total Reports:    ").append(reports.size).append("\n")
            append("Native Crash Note: Fatal C/Go signals (SIGSEGV/SIGABRT) bypass JVM exception handlers\n")
            append("                   and are captured by Android OS tombstone logs (/data/tombstones).\n")
            append("====================================================\n\n")

            reports.forEachIndexed { index, report ->
                append("--- CRASH REPORT #").append(index + 1).append(" ---\n")
                append("Crash ID:    ").append(report.id).append("\n")
                append("Timestamp:   ").append(report.formattedDate).append("\n")
                append("App Version: ").append(report.appVersion).append("\n")
                append("OS:          ").append(report.osVersion).append("\n")
                append("Device:      ").append(report.deviceModel).append("\n")
                append("Thread:      ").append(report.threadName).append(" (ID: ").append(report.threadId).append(")\n")
                append("Exception:   ").append(report.exceptionClass).append(": ").append(report.exceptionMessage).append("\n\n")
                append("Stack Trace:\n").append(report.stackTrace).append("\n\n")
                append("Recent Events Prior to Crash:\n")
                report.recentEvents.forEach { append("  ").append(it).append("\n") }
                append("\n")
            }
            append("=== END OF CRASH EXPORT ===\n")
        }

        return writeToDownloads(context, fileName, OperationalLogCatcher.redactSecrets(content))
    }

    private fun writeToDownloads(context: Context, fileName: String, content: String): Result<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val outputStream: OutputStream? = resolver.openOutputStream(uri)
                    if (outputStream != null) {
                        outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        return Result.success("Saved to Downloads/$fileName")
                    }
                }
            }

            // Fallback for API < 29 or test environments where MediaStore.Downloads is unavailable
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                File(downloadsDir, fileName)
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, fileName)
            }

            FileOutputStream(targetFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            Result.success("Saved to ${targetFile.name}")
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
