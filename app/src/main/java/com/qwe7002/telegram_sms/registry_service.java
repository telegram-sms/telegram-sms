package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class registry_service extends Service {
    battery_receiver receiver = null;
    final String CHANNEL_ID = "1";
    final String CHANNEL_NAME="tg-sms";
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("tg-sms", "onCreate: battery_receiver");
        battery_receiver receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
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