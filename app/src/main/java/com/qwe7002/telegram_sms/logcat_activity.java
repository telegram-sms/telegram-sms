package com.qwe7002.telegram_sms;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class logcat_activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logcat);
        final TextView logcat = findViewById(R.id.logcat_view);
        this.setTitle(R.string.logcat);
        SharedPreferences sharedPreferences = getSharedPreferences("log-data", MODE_PRIVATE);
        logcat.setText(sharedPreferences.getString("error_log", ""));
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                logcat.setText(sharedPreferences.getString("error_log", ""));
            }
        });


    }
}
