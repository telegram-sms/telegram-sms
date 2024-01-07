package com.airfreshener.telegram_sms

import android.os.Bundle
import android.os.FileObserver
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airfreshener.telegram_sms.utils.LogUtils

class LogcatActivity : AppCompatActivity() {

    private var observer: LogcatFileObserver? = null
    private var logcatTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)
        val logcatTextView = findViewById<TextView>(R.id.logcat_textview).apply { logcatTextView = this }
        this.setTitle(R.string.logcat)
        logcatTextView.text = LogUtils.readLog(applicationContext, LINES_COUNT)
        observer = LogcatFileObserver(applicationContext.filesDir.absolutePath, logcatTextView)
    }

    public override fun onPause() {
        super.onPause()
        observer?.stopWatching()
    }

    public override fun onResume() {
        super.onResume()
        logcatTextView?.text = LogUtils.readLog(applicationContext, LINES_COUNT)
        observer?.startWatching()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        LogUtils.resetLogFile(applicationContext)
        return true
    }

    private inner class LogcatFileObserver(
        path: String,
        private val logcat: TextView
    ) : FileObserver(path) {
        override fun onEvent(event: Int, path: String?) {
            if (event == MODIFY && path?.contains("error.log") == true) {
                runOnUiThread { logcat.text = LogUtils.readLog(logcat.context, LINES_COUNT) }
            }
        }
    }

    companion object {
        private const val LINES_COUNT = 100
    }
}
