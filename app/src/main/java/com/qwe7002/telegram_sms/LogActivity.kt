package com.qwe7002.telegram_sms

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

class LogActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var logcatProcess: Process? = null
    private var logcatJob: Job? = null
    private val maxLines = 500
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private var entryId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)

        recyclerView = findViewById(R.id.log_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        logAdapter = LogAdapter()
        recyclerView.adapter = logAdapter

        this.setTitle(R.string.logcat)

        // Start consuming log entries
        startLogConsumer()
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

    private fun startLogConsumer() {
        lifecycleScope.launch(Dispatchers.Main) {
            for (entry in logChannel) {
                logBuffer.add(entry)
                if (logBuffer.size > maxLines) {
                    logBuffer.removeAt(0)
                }
                updateAdapter()
            }
        }
    }

    private fun updateAdapter() {
        val newList = logBuffer.toList()
        logAdapter.submitList(newList) {
            if (newList.isNotEmpty()) {
                recyclerView.scrollToPosition(newList.size - 1)
            }
        }
    }

    private fun startLogcat() {
        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get PID of current app
                val pid = android.os.Process.myPid()

                // Start logcat process filtering by PID
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "--pid=$pid", "-t", "100")
                )

                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty() && !line.startsWith("------")) {
                        val entry = parseLogLine(entryId++, line)
                        logChannel.trySend(entry)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseLogLine(id: Long, line: String): LogEntry {
        // Logcat format with -v time: "MM-DD HH:MM:SS.mmm D/Tag(PID): Message"
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(\s*\d+\):\s*(.*)$""")
        val match = regex.find(line)

        return if (match != null) {
            val (timestamp, level, tag, message) = match.destructured
            LogEntry(
                id = id,
                timestamp = timestamp,
                level = level.first(),
                tag = tag.trim(),
                message = message,
                rawLine = line
            )
        } else {
            // Fallback for unparseable lines
            LogEntry(
                id = id,
                timestamp = "",
                level = 'V',
                tag = "",
                message = line,
                rawLine = line
            )
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
            logBuffer.clear()
            logAdapter.submitList(emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcat()
        logChannel.close()
    }
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: Char,
    val tag: String,
    val message: String,
    val rawLine: String
)

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tagView: TextView = itemView.findViewById(R.id.log_tag)
        val timestampView: TextView = itemView.findViewById(R.id.log_timestamp)
        val messageView: TextView = itemView.findViewById(R.id.log_message)
    }

    class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = getItem(position)

        // Set tag with level indicator
        val tagText = if (entry.tag.isNotEmpty()) {
            "${getLevelString(entry.level)} ${entry.tag}"
        } else {
            getLevelString(entry.level)
        }
        holder.tagView.text = tagText
        holder.tagView.setTextColor(getLevelColor(entry.level))

        // Set timestamp
        holder.timestampView.text = entry.timestamp

        // Set message (use default text color from XML)
        holder.messageView.text = entry.message
    }

    private fun getLevelString(level: Char): String {
        return when (level) {
            'E' -> "❌"  // Error
            'W' -> "⚠️"  // Warning
            'I' -> "ℹ️"  // Info
            'D' -> "🐛"  // Debug
            'V' -> "📝"  // Verbose
            else -> "❓"
        }
    }

    private fun getLevelColor(level: Char): Int {
        return when (level) {
            'E' -> Color.RED
            'W' -> Color.rgb(255, 165, 0)  // Orange
            'I' -> Color.rgb(0, 200, 0)     // Green
            'D' -> Color.rgb(100, 149, 237) // Cornflower Blue
            'V' -> Color.GRAY
            else -> Color.WHITE
        }
    }
}
