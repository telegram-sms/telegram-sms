package com.airfreshener.telegram_sms.services;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.OtherUtils;
import com.airfreshener.telegram_sms.utils.PaperUtils;
import com.airfreshener.telegram_sms.utils.ResendUtils;
import com.airfreshener.telegram_sms.model.ServiceNotifyId;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {
    static Map<String, String> appNameList = new HashMap<>();
    final String TAG = "notification_receiver";
    Context context;
    SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        PaperUtils.init(context);
        sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        Notification notification = OtherUtils.getNotificationObj(getApplicationContext(), getString(R.string.Notification_Listener_title));
        startForeground(ServiceNotifyId.NOTIFICATION_LISTENER_SERVICE, notification);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(@NotNull StatusBarNotification sbn) {
        final String packageName = sbn.getPackageName();
        Log.d(TAG, "onNotificationPosted: " + packageName);

        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, Notification receiver is deactivated.");
            return;
        }

        List<String> listenList = PaperUtils.getSystemBook().read("notify_listen_list", new ArrayList<>());
        if (!listenList.contains(packageName)) {
            Log.i(TAG, "[" + packageName + "] Not in the list of listening packages.");
            return;
        }
        Bundle extras = sbn.getNotification().extras;
        assert extras != null;
        String appName = "unknown";
        Log.d(TAG, "onNotificationPosted: " + appNameList);
        if (appNameList.containsKey(packageName)) {
            appName = appNameList.get(packageName);
        } else {
            final PackageManager pm = getApplicationContext().getPackageManager();
            try {
                ApplicationInfo applicationInfo = pm.getApplicationInfo(sbn.getPackageName(), 0);
                appName = (String) pm.getApplicationLabel(applicationInfo);
                appNameList.put(packageName, appName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        String title = extras.getString(Notification.EXTRA_TITLE, "None");
        String content = extras.getString(Notification.EXTRA_TEXT, "None");

        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");
        RequestMessage requestBody = new RequestMessage();
        requestBody.chat_id = chatId;
        requestBody.text = getString(R.string.receive_notification_title) + "\n" + getString(R.string.app_name_title) + appName + "\n" + getString(R.string.title) + title + "\n" + getString(R.string.content) + content;
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
        OkHttpClient okhttpClient = NetworkUtils.getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", true),
                PaperUtils.getProxyConfig()
        );
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okhttpClient.newCall(request);
        final String errorHead = "Send notification failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.writeLog(context, errorHead + e.getMessage());
                ResendUtils.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    LogUtils.writeLog(context, errorHead + response.code() + " " + result);
                    ResendUtils.addResendLoop(context, requestBody.text);
                }
            }
        });
    }


    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

}
