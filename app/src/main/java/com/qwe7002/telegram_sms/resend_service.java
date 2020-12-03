package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.public_func;
import com.qwe7002.telegram_sms.static_class.public_value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class resend_service extends Service {
    Context context;
    String request_uri;
    stop_notify_receiver receiver;
    static ArrayList<String> resend_list;
    final String table_name = "resend_list";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        resend_list = Paper.book().read(table_name, new ArrayList<>());
        Notification notification = public_func.get_notification_obj(context, getString(R.string.failed_resend));
        startForeground(public_value.RESEND_SERVICE_NOTIFY_ID, notification);
        return START_NOT_STICKY;
    }

    private void network_progress_handle(String message, String chat_id, OkHttpClient okhttp_client) {
        request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        request_body.text = message;
        if (message.contains("<code>") && message.contains("</code>")) {
            request_body.parse_mode = "html";
        }
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_value.JSON);
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        try {
            Response response = call.execute();
            if (response.code() == 200) {
                ArrayList<String> resend_list_local = Paper.book().read(table_name, new ArrayList<>());
                resend_list_local.remove(message);
                Paper.book().write(table_name, resend_list_local);
            }
        } catch (IOException e) {
            public_func.write_log(context, "An error occurred while resending: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        IntentFilter filter = new IntentFilter();
        filter.addAction(public_value.BROADCAST_STOP_SERVICE);
        receiver = new stop_notify_receiver();
        registerReceiver(receiver, filter);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        request_uri = public_func.get_url(sharedPreferences.getString("bot_token", ""), "SendMessage");
        new Thread(() -> {
            resend_list = Paper.book().read(table_name, new ArrayList<>());
            while (true) {
                if (public_func.check_network_status(context)) {
                    ArrayList<String> send_list = resend_list;
                    OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book().read("proxy_config", new proxy_config()));
                    for (String item : send_list) {
                        network_progress_handle(item, sharedPreferences.getString("chat_id", ""), okhttp_client);
                    }
                    resend_list = Paper.book().read(table_name, new ArrayList<>());
                    if (resend_list == send_list || resend_list.size() == 0) {
                        break;
                    }
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            public_func.write_log(context, "The resend failure message is complete.");
            stopSelf();
        }).start();
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class stop_notify_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), public_value.BROADCAST_STOP_SERVICE)) {
                Log.i("resend_loop", "Received stop signal, quitting now...");
                stopSelf();
            }
        }
    }
}
