package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Objects;

import static android.content.Context.MODE_PRIVATE;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), "android.intent.action.BOOT_COMPLETED")) {
            final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
            if (sharedPreferences.getBoolean("initialized", false)) {
                public_func.start_service(context);
            }
        }
    }
}
