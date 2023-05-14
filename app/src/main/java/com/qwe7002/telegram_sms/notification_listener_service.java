package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.sendMessageBody;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.value.constValue;
import com.qwe7002.telegram_sms.value.notifyId;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class notification_listener_service extends NotificationListenerService {
    static Map<String, String> appNameList = new HashMap<>();
    final String TAG = "notification_receiver";
    Context context;
    SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        Notification notification = other.getNotificationObj(getApplicationContext(), getString(R.string.Notification_Listener_title));
        startForeground(notifyId.NOTIFICATION_LISTENER_SERVICE, notification);
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
        final String package_name = sbn.getPackageName();
        Log.d(TAG, "onNotificationPosted: " + package_name);

        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, Notification receiver is deactivated.");
            return;
        }

        List<String> listen_list = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
        assert listen_list != null;
        if (!listen_list.contains(package_name)) {
            Log.i(TAG, "[" + package_name + "] Not in the list of listening packages.");
            return;
        }
        Bundle extras = sbn.getNotification().extras;
        assert extras != null;
        String app_name = "unknown";
        Log.d(TAG, "onNotificationPosted: " + appNameList);
        if (appNameList.containsKey(package_name)) {
            app_name = appNameList.get(package_name);
        } else {
            final PackageManager pm = getApplicationContext().getPackageManager();
            try {
                ApplicationInfo application_info = pm.getApplicationInfo(sbn.getPackageName(), 0);
                app_name = (String) pm.getApplicationLabel(application_info);
                appNameList.put(package_name, app_name);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        String title = extras.getString(Notification.EXTRA_TITLE, "None");
        String content = extras.getString(Notification.EXTRA_TEXT, "None");

        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String messageThreadId = sharedPreferences.getString("message_thread_id", "");
        String requestUri = network.getUrl(botToken, "sendMessage");
        sendMessageBody requestBody = new sendMessageBody();
        requestBody.chat_id = chatId;
        requestBody.message_thread_id = messageThreadId;
        requestBody.text = getString(R.string.receive_notification_title) + "\n" + getString(R.string.app_name_title) + app_name + "\n" + getString(R.string.title) + title + "\n" + getString(R.string.content) + content;
        RequestBody body = RequestBody.create(new Gson().toJson(requestBody), constValue.JSON);
        OkHttpClient okhttpObj = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okhttpObj.newCall(request);
        final String error_head = "Send notification failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, error_head + e.getMessage());
                resend.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log.writeLog(context, error_head + response.code() + " " + result);
                    resend.addResendLoop(context, requestBody.text);
                }
            }
        });
    }


    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

}
