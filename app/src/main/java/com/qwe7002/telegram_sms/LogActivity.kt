package com.qwe7002.telegram_sms

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_sms.value.DEBUG_TAG_FILTER
import com.qwe7002.telegram_sms.value.TAG
import com.qwe7002.telegram_sms.value.TAG_FILTER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

class LogActivity : AppCompatActivity() {
    private val logTag = "${TAG}.LogActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var logcatProcess: Process? = null
    private var logcatJob: Job? = null
    private val maxLines = 500
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private var entryId = 0L
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)

        recyclerView = findViewById(R.id.log_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<ImageView>(R.id.log_character_set)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        logAdapter = LogAdapter()
        logAdapter.setHasStableIds(true)
        recyclerView.adapter = logAdapter

        // Add the empty view to the activity's content child (the root FrameLayout from our layout)
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        val activityRoot = (contentRoot.getChildAt(0) as? ViewGroup) ?: contentRoot

        emptyView = TextView(this).apply {
            text = getString(R.string.no_logs)
            textSize = 16f
            setTextColor(Color.GRAY)
            visibility = View.GONE
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )

        activityRoot.addView(emptyView, lp)
        // Ensure empty view is above other content
        emptyView.post {
            emptyView.bringToFront()
            ViewCompat.setTranslationZ(emptyView, 100f)
            emptyView.invalidate()
        }

        this.setTitle(R.string.logcat)

        // Ensure UI reflects current buffer state on open (show empty state if no logs)
        updateAdapter()

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
        // Refresh UI state in case logs changed while activity was paused
        updateAdapter()
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
        // Create a new immutable list to avoid concurrent modification
        val newList = ArrayList(logBuffer)
        logAdapter.submitList(newList) {
            // Scroll after the list has been updated
            if (newList.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.scrollToPosition(newList.size - 1)
                }
            }
        }

        // Toggle empty state view visibility
        if (this::emptyView.isInitialized) {
            if (newList.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun startLogcat() {
        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(logTag, "startLogcat: Starting logcat process")
            try {
                // Filters to pass to logcat (tag names only)
                var filterArray = TAG_FILTER
                var level = "I"
                if (BuildConfig.DEBUG) {

                    filterArray = filterArray.plus(DEBUG_TAG_FILTER)
                    level = "V" // Verbose in debug builds
                }

                // Map tags to "Tag:LEVEL" strings for logcat arguments
                val addFilterArray =
                    filterArray.map { tag -> "${TAG}.$tag:$level" }.toTypedArray()
                        .plus("${TAG}:$level")
                val command = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    arrayOf(
                        "logcat",
                        *addFilterArray,
                        "*:S",
                        "-d",
                        "-v",
                        "time",
                        "--pid=${android.os.Process.myPid()}"
                    )
                } else {
                    arrayOf("logcat", *addFilterArray, "*:S","-d", "-v", "time")
                }
                Log.d(logTag, command.joinToString(" ") )
                logcatProcess = Runtime.getRuntime().exec(command)

                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))
                var lastEntry: LogEntry? = null
                var lastTimestamp: String? = null
                var lastTag: String? = null

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty() && !line.startsWith("------")) {
                        val parsed = parseLogLine(entryId, line)

                        // Check if this is a continuation line (same timestamp, tag, and message is part of a stack trace)
                        val msg = parsed.message
                        val isContinuation = lastEntry != null &&
                                lastTimestamp == parsed.timestamp &&
                                lastTag == parsed.tag &&
                                (msg.startsWith("\t") ||
                                        msg.startsWith("    ") ||
                                        msg.matches(Regex("^\\s*at\\s+.*")) ||
                                        msg.matches(Regex("^\\s*Caused by:.*")) ||
                                        msg.matches(Regex("^\\s*Suppressed:.*")) ||
                                        msg.matches(Regex("^\\s*\\.{3}\\s+\\d+\\s+more\\s*$")) ||
                                        msg.matches(Regex("""^[a-zA-Z]+(\.[a-zA-Z0-9_$]+)*Exception.*""")) ||
                                        msg.matches(Regex("""^[a-zA-Z]+(\.[a-zA-Z0-9_$]+)*Error.*""")) ||
                                        msg.matches(Regex("""^[a-zA-Z]+(\.[a-zA-Z0-9_$]+)*:\s+.*""")) ||
                                        (msg.trim().isEmpty() && msg.isNotEmpty()))

                        if (isContinuation) {
                            // Add to the last entry's continuation lines
                            lastEntry.continuationLines.add(parsed.message)
                        } else {
                            // Send the previous entry if exists
                            if (lastEntry != null) {
                                logChannel.trySend(lastEntry)
                            }
                            // Start a new entry
                            entryId++
                            lastEntry = parsed
                            lastTimestamp = parsed.timestamp
                            lastTag = parsed.tag
                        }
                    }
                }

                // Send the last entry if exists
                if (lastEntry != null) {
                    logChannel.trySend(lastEntry)
                }
            } catch (e: Exception) {
                Log.e(logTag, "startLogcat: ${e.message}", e)
            }
        }
    }

    private fun parseLogLine(id: Long, line: String): LogEntry {
        // Logcat format with -v time: "MM-DD HH:MM:SS.mmm D/Tag(PID): Message"
        val regex =
            Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(\s*\d+\):\s*(.*)$""")
        val match = regex.find(line)

        return if (match != null) {
            val (timestamp, level, tag, message) = match.destructured
            LogEntry(
                id = id,
                timestamp = timestamp,
                level = level.first(),
                tag = tag.trim(),
                message = message,
                rawLine = line,
                continuationLines = mutableListOf()
            )
        } else {
            // Fallback for unparseable lines
            LogEntry(
                id = id,
                timestamp = "",
                level = 'V',
                tag = "",
                message = line,
                rawLine = line,
                continuationLines = mutableListOf()
            )
        }
    }

    private fun stopLogcat() {
        lifecycleScope.launch {
            logcatJob?.cancel()
            logcatProcess?.destroy()
            logcatProcess = null
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
    val rawLine: String,
    val continuationLines: MutableList<String> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    fun hasContinuation(): Boolean = continuationLines.isNotEmpty()
}

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tagView: TextView = itemView.findViewById(R.id.log_tag)
        val timestampView: TextView = itemView.findViewById(R.id.log_timestamp)
        val messageView: TextView = itemView.findViewById(R.id.log_message)
        val levelView: TextView = itemView.findViewById(R.id.log_level)
        val expandIndicator: TextView = itemView.findViewById(R.id.log_expand_indicator)
        val detailsView: TextView = itemView.findViewById(R.id.log_details)
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

        // Set level emoji
        holder.levelView.text = getLevelString(entry.level)
        holder.levelView.setTextColor(getLevelColor(entry.level))

        // Set tag (without emoji, but with level color)
        holder.tagView.text =entry.tag.ifEmpty { "Unknown" }.removePrefix("${TAG}.")

        // Set timestamp
        holder.timestampView.text = entry.timestamp

        // Set message (keep default text color from XML)
        holder.messageView.text = entry.message

        // Handle expand/collapse for continuation lines
        if (entry.hasContinuation()) {
            holder.expandIndicator.visibility = View.VISIBLE
            holder.expandIndicator.text =
                if (entry.isExpanded) "▼ ${entry.continuationLines.size}" else "▶ ${entry.continuationLines.size}"

            if (entry.isExpanded) {
                holder.detailsView.visibility = View.VISIBLE
                holder.detailsView.text = entry.continuationLines.joinToString("\n")
            } else {
                holder.detailsView.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val currentEntry = getItem(currentPosition)
                    currentEntry.isExpanded = !currentEntry.isExpanded
                    notifyItemChanged(currentPosition)
                }
            }
        } else {
            holder.expandIndicator.visibility = View.GONE
            holder.detailsView.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    private fun getLevelString(level: Char): String {
        return when (level) {
            'E' -> "❌ Error"  // Error
            'W' -> "⚠️ Warning"  // Warning
            'I' -> "ℹ️ Info"  // Info
            'D' -> "🐛 Debug"  // Debug
            'V' -> "📝 Verbose"  // Verbose
            else -> "❓"
        }
    }

    private fun getLevelColor(level: Char): Int {
        return when (level) {
            'E' -> Color.RED
            'W' -> Color.rgb(255, 165, 0)  // Orange
            'I' -> Color.rgb(100, 149, 237) // Cornflower Blue
            'D' -> Color.rgb(0, 200, 0)     // Green
            'V' -> Color.GRAY
            else -> Color.WHITE
        }
    }
}






