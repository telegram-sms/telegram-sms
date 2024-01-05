package com.airfreshener.telegram_sms

import android.content.Context
import android.os.Bundle
import android.os.FileObserver
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airfreshener.telegram_sms.utils.LogUtils

class LogcatActivity : AppCompatActivity() {
    private var context: Context? = null
    private var observer: file_observer? = null
    private var logcat_textview: TextView? = null
    private val line = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = applicationContext
        setContentView(R.layout.activity_logcat)
        logcat_textview = findViewById(R.id.logcat_textview)
        this.setTitle(R.string.logcat)
        logcat_textview?.setText(LogUtils.read_log(context, line))
        observer = file_observer(applicationContext, logcat_textview)
    }

    public override fun onPause() {
        super.onPause()
        observer!!.stopWatching()
    }

    public override fun onResume() {
        super.onResume()
        logcat_textview?.text = LogUtils.read_log(context, line)
        observer?.startWatching()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        LogUtils.reset_log_file(context)
        return true
    }

    private inner class file_observer(
        private val context: Context,
        private val logcat: TextView?
    ) : FileObserver(context.filesDir.absolutePath) {
        override fun onEvent(event: Int, path: String?) {
            if (event == MODIFY && path!!.contains("error.log")) {
                runOnUiThread { logcat!!.text = LogUtils.read_log(context, line) }
            }
        }
    }
}
