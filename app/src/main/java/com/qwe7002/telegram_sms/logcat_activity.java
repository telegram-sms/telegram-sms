package com.qwe7002.telegram_sms;

import android.content.Context;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class logcat_activity extends AppCompatActivity {

    private Context context;
    private file_observer observer;
    private TextView logcat;
    private final int line = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.activity_logcat);
        logcat = findViewById(R.id.logcat_view);
        this.setTitle(R.string.logcat);
        logcat.setText(public_func.read_log(context, line));
        observer = new file_observer(context, logcat);

    }

    @Override
    public void onPause() {
        super.onPause();
        observer.stopWatching();
    }

    @Override
    public void onResume() {
        super.onResume();
        logcat.setText(public_func.read_log(context, line));
        observer.startWatching();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        public_func.write_file(context, "error.log", "", Context.MODE_PRIVATE);
        return true;
    }

    class file_observer extends FileObserver {
        private final Context context;
        private final TextView logcat;

        file_observer(Context context, TextView logcat) {
            super(context.getFilesDir().getAbsolutePath());
            this.context = context;
            this.logcat = logcat;
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == FileObserver.MODIFY && path.contains("error.log")) {
                runOnUiThread(() -> logcat.setText(public_func.read_log(context, line)));
            }
        }
    }

}


