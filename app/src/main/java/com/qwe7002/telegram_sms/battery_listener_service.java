package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.BATTERY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class battery_listener_service extends Service {
    battery_receiver receiver = null;
    final String CHANNEL_ID = "1";
    final String CHANNEL_NAME = "tg-sms";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(public_func.log_tag, "onCreate: battery_receiver");
        battery_receiver receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(receiver, filter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID).build();
            startForeground(1, notification);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}

class battery_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
        if (bot_token.isEmpty() || chat_id.isEmpty()) {
            Log.i(public_func.log_tag, "onReceive: token not found");
            return;
        }
        final request_json request_body = new request_json();
        request_body.chat_id = chat_id;
        StringBuilder prebody = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
        switch (Objects.requireNonNull(intent.getAction())) {
            case Intent.ACTION_BATTERY_OKAY:
                prebody = prebody.append(context.getString(R.string.charging_is_complete));
                break;
            case Intent.ACTION_BATTERY_LOW:
                prebody = prebody.append(context.getString(R.string.battery_low));
                break;
            case Intent.ACTION_POWER_CONNECTED:
                prebody = prebody.append(context.getString(R.string.ac_connect));
                break;
            case Intent.ACTION_POWER_DISCONNECTED:
                prebody = prebody.append(context.getString(R.string.ac_disconnect));
                break;
        }
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        request_body.text = prebody.append("\n").append(context.getString(R.string.current_battery_level)).append(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%").toString();
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Looper.prepare();
                String error_message = "Send Battery Error:" + e.getMessage();
                Toast.makeText(context,error_message , Toast.LENGTH_SHORT).show();
                Log.i(public_func.log_tag, error_message);
                Looper.loop();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    Looper.prepare();
                    assert response.body() != null;
                    String error_message = "Send Battery Error:" + response.body().string();
                    Toast.makeText(context,error_message , Toast.LENGTH_SHORT).show();
                    Log.i(public_func.log_tag, error_message);
                    Looper.loop();
                }
            }
        });


    }
}
