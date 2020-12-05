package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.data_structure.proxy_config;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.public_func;
import com.qwe7002.telegram_sms.static_class.public_value;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class battery_service extends Service {
    static String bot_token;
    static String chat_id;
    static boolean doh_switch;
    private Context context;
    private battery_receiver battery_receiver = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, getString(R.string.battery_monitoring_notify));
        startForeground(public_value.BATTERY_NOTIFY_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        final boolean charger_status = sharedPreferences.getBoolean("charger_status", false);
        battery_receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(public_value.BROADCAST_STOP_SERVICE);
        if (charger_status) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        }
        registerReceiver(battery_receiver, filter);

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(battery_receiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class battery_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, @NotNull final Intent intent) {
            String TAG = "battery_receiver";
            assert intent.getAction() != null;
            Log.d(TAG, "Receive action: " + intent.getAction());
            if (intent.getAction().equals(public_value.BROADCAST_STOP_SERVICE)) {
                Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            String request_uri = public_func.get_url(battery_service.bot_token, "sendMessage");
            final request_message request_body = new request_message();
            request_body.chat_id = battery_service.chat_id;
            StringBuilder message_body = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
            final String action = intent.getAction();
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
            switch (Objects.requireNonNull(action)) {
                case Intent.ACTION_BATTERY_OKAY:
                    message_body.append(context.getString(R.string.low_battery_status_end));
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    message_body.append(context.getString(R.string.battery_low));
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    message_body.append(context.getString(R.string.charger_connect));
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    message_body.append(context.getString(R.string.charger_disconnect));
                    break;
            }
            assert batteryManager != null;
            int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (battery_level > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.");
                battery_level = 100;
            }
            request_body.text = message_body.append("\n").append(context.getString(R.string.current_battery_level)).append(battery_level).append("%").toString();
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(battery_service.doh_switch, Paper.book("system_config").read("proxy_config", new proxy_config()));
            String request_body_raw = new Gson().toJson(request_body);
            RequestBody body = RequestBody.create(request_body_raw, public_value.JSON);
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            final String error_head = "Send battery info failed:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    public_func.write_log(context, error_head + e.getMessage());
                    if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                        public_func.send_fallback_sms(context, request_body.text, -1);
                        public_func.add_resend_loop(context, request_body.text);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.code() != 200) {
                        assert response.body() != null;
                        String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                        public_func.write_log(context, error_message);
                        public_func.add_resend_loop(context, request_body.text);
                    }
                }
            });
        }
    }


}

