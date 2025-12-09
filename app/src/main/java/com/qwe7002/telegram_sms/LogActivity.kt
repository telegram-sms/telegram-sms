package com.qwe7002.telegram_sms

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var logcatProcess: Process? = null
    private var logcatJob: Job? = null
    private val maxLines = 500
    private val logBuffer = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)

        recyclerView = findViewById(R.id.log_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        logAdapter = LogAdapter(logBuffer)
        recyclerView.adapter = logAdapter

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
                        val shouldRemoveFirst: Boolean
                        val currentSize: Int

                        synchronized(logBuffer) {
                            logBuffer.add(line)
                            shouldRemoveFirst = logBuffer.size > maxLines
                            if (shouldRemoveFirst) {
                                logBuffer.removeAt(0)
                            }
                            currentSize = logBuffer.size
                        }

                        withContext(Dispatchers.Main) {
                            if (shouldRemoveFirst) {
                                logAdapter.notifyItemRemoved(0)
                                logAdapter.notifyItemInserted(currentSize - 1)
                            } else {
                                logAdapter.notifyItemInserted(currentSize - 1)
                            }
                            recyclerView.scrollToPosition(currentSize - 1)
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

    private fun clearLogcat() {
        try {
            Runtime.getRuntime().exec("logcat -c")
            val size = logBuffer.size
            logBuffer.clear()
            logAdapter.notifyItemRangeRemoved(0, size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcat()
    }
}

class LogAdapter(private val logBuffer: MutableList<String>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.typeface = Typeface.MONOSPACE
        textView.textSize = 12f
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val line = logBuffer[position]
        holder.textView.text = formatLogLine(line)
    }

    override fun getItemCount(): Int = logBuffer.size

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
}


