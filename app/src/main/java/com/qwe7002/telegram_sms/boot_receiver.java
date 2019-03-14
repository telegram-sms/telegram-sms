package com.qwe7002.telegram_sms;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import static android.content.Context.MODE_PRIVATE;

public class boot_receiver extends BroadcastReceiver {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
        }
    }
}
