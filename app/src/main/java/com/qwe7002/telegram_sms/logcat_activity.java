package com.qwe7002.telegram_sms;

import android.content.Context;
import android.os.Bundle;
import android.os.FileObserver;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class logcat_activity extends AppCompatActivity {

    Context context;
    file_observer observer;
    TextView logcat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.activity_logcat);
        logcat = findViewById(R.id.logcat_view);
        this.setTitle(R.string.logcat);
        logcat.setText(public_func.read_log(context));
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
        logcat.setText(public_func.read_log(context));
        observer.startWatching();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        public_func.write_file(context, "error.log", "");
        public_func.write_file(context, "message.json", "{}");
        return true;
    }

    class file_observer extends FileObserver {
        private Context context;
        private TextView logcat;

        file_observer(Context context, TextView logcat) {
            super(context.getFilesDir().getAbsolutePath());
            this.context = context;
            this.logcat = logcat;
        }

        @Override
        public void onEvent(int event, String path) {
            if (event == FileObserver.MODIFY) {
                runOnUiThread(() -> logcat.setText(public_func.read_log(context)));
            }
        }
    }

}


