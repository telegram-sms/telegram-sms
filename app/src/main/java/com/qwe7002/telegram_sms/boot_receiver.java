package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Objects;

import static android.content.Context.MODE_PRIVATE;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), "android.intent.action.BOOT_COMPLETED")) {
            Log.d(public_func.log_tag, "onReceive: boot_completed");
            final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
            String bot_token = sharedPreferences.getString("bot_token", "");
            String chat_id = sharedPreferences.getString("chat_id", "");
            if (!bot_token.isEmpty() && !chat_id.isEmpty()) {
                Intent battery_service = new Intent(context, battery_listener_service.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(battery_service);
                } else {
                    context.startService(battery_service);
                }

                if (sharedPreferences.getBoolean("webhook", false)) {
                    Intent webhook_service = new Intent(context, webhook_service.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(webhook_service);
                    } else {
                        context.startService(webhook_service);
                    }
                }

            }
        }
    }
}
