package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.data_structure.proxy;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.public_func;
import com.qwe7002.telegram_sms.static_class.public_value;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.O)
public class send_ussd_service extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@NotNull Intent intent, int flags, int startId) {
        Context context = getApplicationContext();
        Paper.init(context);
        String notification_name = context.getString(R.string.ussd_code_running);
        Notification.Builder notification;
        NotificationChannel channel = new NotificationChannel(notification_name, notification_name,
                NotificationManager.IMPORTANCE_MIN);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(channel);
        notification = new Notification.Builder(context, notification_name).setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name);
        startForeground(public_value.SEND_USSD_SERVCE_NOTIFY_ID, notification.build());

        Handler handler = new Handler();
        String ussd = public_func.get_nine_key_map_convert(intent.getStringExtra("ussd"));
        int sub_id = intent.getIntExtra("sub_id", -1);


        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        String TAG = "Send ussd";

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;

        if (sub_id != -1) {
            telephonyManager = telephonyManager.createForSubscriptionId(sub_id);
            Log.d(TAG, "onStartCommand: " + sub_id);
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "send_ussd: No permission.");
        }

        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        request_body.text = context.getString(R.string.send_ussd_head) + "\n" + context.getString(R.string.ussd_code_running);

        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, public_value.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        TelephonyManager finalTelephonyManager = telephonyManager;
        new Thread(() -> {
            long message_id = -1L;
            try {
                Response response = call.execute();
                message_id = public_func.get_message_id(Objects.requireNonNull(response.body()).string());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                finalTelephonyManager.sendUssdRequest(ussd, new ussd_request_callback(context, sharedPreferences, message_id), handler);
            }
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

}
