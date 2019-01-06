package com.qwe7002.telegram_sms;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class logcat_activity extends AppCompatActivity {
    private SharedPreferences.OnSharedPreferenceChangeListener shared_Preference_change_listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            TextView logcat = findViewById(R.id.logcat_view);
            logcat.setText(sharedPreferences.getString("error_log", ""));
        }
    };
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logcat);
        TextView logcat = findViewById(R.id.logcat_view);
        this.setTitle(R.string.logcat);
        sharedPreferences = getSharedPreferences("log-data", MODE_PRIVATE);
        logcat.setText(sharedPreferences.getString("error_log", ""));
    }

    @Override
    protected void onResume() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(shared_Preference_change_listener);
        super.onResume();
    }

    @Override
    protected void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(shared_Preference_change_listener);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        sharedPreferences.edit().clear().apply();
        return true;
    }
}
