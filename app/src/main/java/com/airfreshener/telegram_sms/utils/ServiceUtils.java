package com.airfreshener.telegram_sms.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.airfreshener.telegram_sms.services.BatteryService;
import com.airfreshener.telegram_sms.services.ChatCommandService;
import com.airfreshener.telegram_sms.services.NotificationListenerService;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class ServiceUtils {
    public static void stopAllService(@NotNull Context context) {
        Intent intent = new Intent(Consts.BROADCAST_STOP_SERVICE);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void startService(
            Context context,
            Boolean batterySwitch,
            Boolean chatCommandSwitch
    ) {
        Intent batteryService = new Intent(context, BatteryService.class);
        Intent chatPollingService = new Intent(context, ChatCommandService.class);
        if (isNotifyListener(context)) {
            Log.d("start_service", "start_service: ");
            ComponentName thisComponent = new ComponentName(context, NotificationListenerService.class);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (batterySwitch) {
                context.startForegroundService(batteryService);
            }
            if (chatCommandSwitch) {
                context.startForegroundService(chatPollingService);
            }
        } else {
            if (batterySwitch) {
                context.startService(batteryService);
            }
            if (chatCommandSwitch) {
                context.startService(chatPollingService);
            }
        }
    }

    public static boolean isNotifyListener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
