package com.qwe7002.telegram_sms;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

public class registry_service extends Service {
    public registry_service() {
    }

    @Override
    public void onCreate() {
        battery_receiver receiver=new battery_receiver();
        IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_LOW);
        registerReceiver(receiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}