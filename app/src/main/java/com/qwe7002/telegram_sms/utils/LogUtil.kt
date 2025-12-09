package com.qwe7002.telegram_sms.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG_PREFIX = "TelegramSMS"

/**
 * Extension function to write logs with automatic level detection
 */
fun Context.logInfo(message: String) {
    Log.i("$TAG_PREFIX:${this::class.simpleName}", message)
}

fun Context.logError(message: String) {
    Log.e("$TAG_PREFIX:${this::class.simpleName}", message)
}

fun Context.logWarning(message: String) {
    Log.w("$TAG_PREFIX:${this::class.simpleName}", message)
}

fun Context.logDebug(message: String) {
    Log.d("$TAG_PREFIX:${this::class.simpleName}", message)
}

/**
 * Smart log function that detects level based on content
 */
fun Context.logAuto(message: String) {
    val tag = "$TAG_PREFIX:${this::class.simpleName}"
    val messageLower = message.lowercase()

    when {
        messageLower.contains("error") ||
        messageLower.contains("exception") ||
        messageLower.contains("failed") ||
        messageLower.contains("fail") ||
        messageLower.contains("crash") -> {
            Log.e(tag, message)
        }
        messageLower.contains("warning") ||
        messageLower.contains("warn") ||
        messageLower.contains("deprecated") -> {
            Log.w(tag, message)
        }
        messageLower.contains("debug") ||
        messageLower.contains("trace") -> {
            Log.d(tag, message)
        }
        messageLower.contains("verbose") -> {
            Log.v(tag, message)
        }
        else -> {
            Log.i(tag, message)
        }
    }
}

/**
 * Read logs from logcat for the current process
 */
fun readLogcat(maxLines: Int = 100): String {
    return try {
        val pid = android.os.Process.myPid()
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-v", "time", "--pid=$pid", "-t", maxLines.toString(), "-d")
        )

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()

        reader.forEachLine { line ->
            if (line.isNotEmpty()) {
                output.append(line).append("\n")
            }
        }

        reader.close()
        process.waitFor()

        if (output.isEmpty()) {
            "No logs available"
        } else {
            output.toString().trim()
        }
    } catch (e: Exception) {
        "Error reading logcat: ${e.message}"
    }
}

/**
 * Clear the logcat buffer
 */
fun clearLogcat() {
    try {
        Runtime.getRuntime().exec("logcat -c")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

