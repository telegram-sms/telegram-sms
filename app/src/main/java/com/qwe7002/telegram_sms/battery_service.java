package com.qwe7002.telegram_sms;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.sendMessageBody;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.sms;
import com.qwe7002.telegram_sms.value.constValue;
import com.qwe7002.telegram_sms.value.notifyId;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class battery_service extends Service {
    static String botToken;
    static String chatId;
    static String messageThreadId;
    static boolean dohSwitch;
    private Context context;
    private batteryChangeReceiver batteryReceiver = null;
    static long lastReceiveTime = 0;
    static long lastReceiveMessageId = -1;

    private static ArrayList<sendObj> sendLoopList;

    @SuppressLint("InlinedApi")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = other.getNotificationObj(context, getString(R.string.battery_monitoring_notify));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notifyId.BATTERY, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }else{
            startForeground(notifyId.BATTERY, notification);
        }
        return START_STICKY;
    }

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "InlinedApi"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chatId = sharedPreferences.getString("chat_id", "");
        botToken = sharedPreferences.getString("bot_token", "");
        messageThreadId = sharedPreferences.getString("message_thread_id", "");
        dohSwitch = sharedPreferences.getBoolean("doh_switch", true);
        boolean charger_status = sharedPreferences.getBoolean("charger_status", false);
        batteryReceiver = new batteryChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        if (charger_status) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        }
        filter.addAction(constValue.BROADCAST_STOP_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED);
        }else{
            registerReceiver(batteryReceiver, filter);
        }
        sendLoopList = new ArrayList<>();
        new Thread(() -> {
            ArrayList<sendObj> need_remove = new ArrayList<>();
            while (true) {
                for (sendObj item : sendLoopList) {
                    networkHandle(item);
                    need_remove.add(item);
                }
                sendLoopList.removeAll(need_remove);
                need_remove.clear();
                if (sendLoopList.isEmpty()) {
                    //Only enter sleep mode when there are no messages
                    try {
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i("BatteryService", "onCreate: "+e);
                    }
                }
            }
        }).start();
    }

    private void networkHandle(sendObj obj) {
        String TAG = "network_handle";
        final sendMessageBody requestMessage = new sendMessageBody();
        requestMessage.chat_id = battery_service.chatId;
        requestMessage.text = obj.content;
        requestMessage.message_thread_id = battery_service.messageThreadId;
        String requestUri = network.getUrl(battery_service.botToken, "sendMessage");
        if ((System.currentTimeMillis() - lastReceiveTime) <= 5000L && lastReceiveMessageId != -1) {
            requestUri = network.getUrl(botToken, "editMessageText");
            requestMessage.message_id = lastReceiveMessageId;
            Log.d(TAG, "onReceive: edit_mode");
        }
        lastReceiveTime = System.currentTimeMillis();
        OkHttpClient okhttpObj = network.getOkhttpObj(battery_service.dohSwitch, Paper.book("system_config").read("proxy_config", new proxy()));
        String requestBodyRaw = new Gson().toJson(requestMessage);
        RequestBody body = RequestBody.create(requestBodyRaw, constValue.JSON);
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okhttpObj.newCall(request);
        final String errorHead = "Send battery info failed:";
        try {
            Response response = call.execute();
            if (response.code() == 200) {
                lastReceiveMessageId = other.getMessageId(Objects.requireNonNull(response.body()).string());
            } else {
                lastReceiveMessageId = -1;
                if (obj.action.equals(Intent.ACTION_BATTERY_LOW)) {
                    sms.fallbackSMS(context, requestMessage.text, -1);
                }
            }
        } catch (IOException e) {
            Log.i(TAG, "networkHandle: "+e);
            log.writeLog(context, errorHead + e.getMessage());
            if (obj.action.equals(Intent.ACTION_BATTERY_LOW)) {
                sms.fallbackSMS(context, requestMessage.text, -1);
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(batteryReceiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class sendObj {
        public String content;
        public String action;
    }

    class batteryChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, @NotNull final Intent intent) {

            String TAG = "battery_receiver";
            assert intent.getAction() != null;
            Log.d(TAG, "Receive action: " + intent.getAction());
            if (intent.getAction().equals(constValue.BROADCAST_STOP_SERVICE)) {
                Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            StringBuilder body = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
            final String action = intent.getAction();
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
            switch (Objects.requireNonNull(action)) {
                case Intent.ACTION_BATTERY_OKAY:
                    body.append(context.getString(R.string.low_battery_status_end));
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    body.append(context.getString(R.string.battery_low));
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    body.append(context.getString(R.string.charger_connect));
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    body.append(context.getString(R.string.charger_disconnect));
                    break;
            }
            assert batteryManager != null;
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (batteryLevel > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.");
                batteryLevel = 100;
            }
            String result = body.append("\n").append(context.getString(R.string.current_battery_level)).append(batteryLevel).append("%").toString();
            sendObj obj = new sendObj();
            obj.action = action;
            obj.content = result;
            sendLoopList.add(obj);

        }
    }
}

