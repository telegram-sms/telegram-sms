package com.qwe7002.telegram_sms

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogActivity : AppCompatActivity() {
    private lateinit var logcatTextview: TextView
    private lateinit var scrollView: ScrollView
    private var logcatProcess: Process? = null
    private var logcatJob: Job? = null
    private val maxLines = 500
    private val logBuffer = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)
        logcatTextview = findViewById(R.id.logcat_textview)
        scrollView = findViewById(R.id.logcat_scroll)
        this.setTitle(R.string.logcat)
    }

    public override fun onPause() {
        super.onPause()
        stopLogcat()
    }

    public override fun onResume() {
        super.onResume()
        startLogcat()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        clearLogcat()
        return true
    }

    private fun startLogcat() {
        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Clear previous logs and read existing logs first
                logBuffer.clear()

                // Get PID of current app
                val pid = android.os.Process.myPid()

                // Start logcat process filtering by PID
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "--pid=$pid", "-t", "100")
                )

                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        synchronized(logBuffer) {
                            logBuffer.add(line)
                            if (logBuffer.size > maxLines) {
                                logBuffer.removeAt(0)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            updateLogDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopLogcat() {
        logcatJob?.cancel()
        logcatProcess?.destroy()
        logcatProcess = null
    }

    private fun updateLogDisplay() {
        val spannableBuilder = SpannableStringBuilder()

        synchronized(logBuffer) {
            logBuffer.forEach { line ->
                val formattedLine = formatLogLine(line)
                spannableBuilder.append(formattedLine)
                spannableBuilder.append("\n")
            }
        }

        logcatTextview.text = spannableBuilder

        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun formatLogLine(line: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(line)

        // Detect log level and apply color
        when {
            line.contains(" E ") || line.contains("/E ") -> {
                // Error - Red
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.contains(" W ") || line.contains("/W ") -> {
                // Warning - Yellow/Orange
                spannable.setSpan(
                    ForegroundColorSpan(Color.rgb(255, 165, 0)),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.contains(" I ") || line.contains("/I ") -> {
                // Info - Green
                spannable.setSpan(
                    ForegroundColorSpan(Color.rgb(0, 200, 0)),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.contains(" D ") || line.contains("/D ") -> {
                // Debug - Blue
                spannable.setSpan(
                    ForegroundColorSpan(Color.rgb(100, 149, 237)),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            line.contains(" V ") || line.contains("/V ") -> {
                // Verbose - Gray
                spannable.setSpan(
                    ForegroundColorSpan(Color.GRAY),
                    0, line.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

    private fun clearLogcat() {
        try {
            Runtime.getRuntime().exec("logcat -c")
            logBuffer.clear()
            logcatTextview.text = ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcat()
    }
}


