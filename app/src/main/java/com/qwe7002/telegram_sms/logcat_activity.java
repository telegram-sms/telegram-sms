package com.qwe7002.telegram_sms;

import android.content.Context;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.qwe7002.telegram_sms.static_class.log;

import org.jetbrains.annotations.NotNull;

public class logcat_activity extends AppCompatActivity {

    private Context context;
    private fileObserver observer;
    private TextView logcatTextview;
    private final int line = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.activity_logcat);
        logcatTextview = findViewById(R.id.logcat_textview);
        this.setTitle(R.string.logcat);
        logcatTextview.setText(log.readLog(context, line));
        observer = new fileObserver(context, logcatTextview);
    }

    @Override
    public void onPause() {
        super.onPause();
        observer.stopWatching();
    }

    @Override
    public void onResume() {
        super.onResume();
        logcatTextview.setText(log.readLog(context, line));
        observer.startWatching();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        log.resetLogFile(context);
        return true;
    }

    private class fileObserver extends FileObserver {
        private final Context context;
        private final TextView logcat;

        fileObserver(@NotNull Context context, TextView logcat) {
            super(context.getFilesDir().getAbsolutePath());
            this.context = context;
            this.logcat = logcat;
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == FileObserver.MODIFY && path.contains("error.log")) {
                runOnUiThread(() -> logcat.setText(log.readLog(context, line)));
            }
        }
    }
}


