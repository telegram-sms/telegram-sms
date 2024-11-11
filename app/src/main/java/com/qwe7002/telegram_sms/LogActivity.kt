@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import android.content.Context
import android.os.Bundle
import android.os.FileObserver
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qwe7002.telegram_sms.static_class.Logs

class LogActivity : AppCompatActivity() {
    private lateinit var observer: Observer
    private lateinit var logcatTextview: TextView
    private val line = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)
        logcatTextview = findViewById(R.id.logcat_textview)
        this.setTitle(R.string.logcat)
        logcatTextview.text = Logs.readLog(applicationContext, line)
        observer = Observer(applicationContext, logcatTextview)
    }

    public override fun onPause() {
        super.onPause()
        observer.stopWatching()
    }

    public override fun onResume() {
        super.onResume()
        logcatTextview.text = Logs.readLog(applicationContext, line)
        observer.startWatching()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Logs.resetLogFile(applicationContext)
        return true
    }

    private inner class Observer(private val context: Context, private val logcat: TextView) :
        FileObserver(context.filesDir.absolutePath) {
        override fun onEvent(event: Int, path: String?) {
            if (event == MODIFY && path!!.contains("error.log")) {
                runOnUiThread { logcat.text = Logs.readLog(context, line) }
            }
        }
    }
}


