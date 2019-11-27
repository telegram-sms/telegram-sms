package com.qwe7002.telegram_sms;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

public class notification_listener_monitor_service extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        if (public_func.is_notify_listener(context)) {
            Log.i("notify_monitor_service", "Permission denied.");
            return;
        }
        ComponentName collectorComponent = new ComponentName(this, notification_listener_monitor_service.class);
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean collectorRunning = false;
        assert manager != null;
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices == null) {
            return;
        }
        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (service.service.equals(collectorComponent)) {
                if (service.pid == android.os.Process.myPid()) {
                    collectorRunning = true;
                }
            }
        }
        if (!collectorRunning) {
            toggleNotificationListenerService();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void toggleNotificationListenerService() {
        ComponentName thisComponent = new ComponentName(this, notification_listener_monitor_service.class);
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
