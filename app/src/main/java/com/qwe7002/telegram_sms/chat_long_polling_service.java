package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import com.google.gson.*;
import okhttp3.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

public class chat_long_polling_service extends Service {
    static int offset = 0;
    static int magnification = 1;
    static int error_magnification = 1;
    String chat_id;
    String bot_token;
    Context context;
    Boolean wakelock_switch;
    SharedPreferences sharedPreferences;
    OkHttpClient okhttp_client;
    private stop_broadcast_receiver stop_broadcast_receiver = null;
    private PowerManager.WakeLock wakelock;
    private WifiManager.WifiLock wifiLock;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(getApplicationContext(), getString(R.string.chat_command_service_name));
        startForeground(2, notification);
        return START_STICKY;
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        IntentFilter intentFilter = new IntentFilter(public_func.broadcast_stop_service);
        stop_broadcast_receiver = new stop_broadcast_receiver();
        registerReceiver(stop_broadcast_receiver, intentFilter);

        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));

        wifiLock = ((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "bot_command_polling_wifi");
        wifiLock.acquire();

        wakelock_switch = sharedPreferences.getBoolean("wakelock", false);
        if (wakelock_switch) {
            wakelock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PARTIAL_WAKE_LOCK, "bot_command_polling");
            wakelock.setReferenceCounted(false);
        }

        new Thread(() -> {
            while (true) {
                if (wakelock_switch) {
                    wakelock.acquire(90000);
                }
                start_long_polling();
                if (wakelock_switch) {
                    wakelock.release();
                }
            }
        }).start();

    }

    @Override
    public void onDestroy() {
        wifiLock.release();
        if (wakelock_switch) {
            wakelock.release();
        }
        unregisterReceiver(stop_broadcast_receiver);
        stopForeground(true);
        super.onDestroy();
    }


    void start_long_polling() {
        int read_timeout = 5 * magnification;
        OkHttpClient okhttp_client_new = okhttp_client.newBuilder()
                .readTimeout((read_timeout + 5), TimeUnit.SECONDS)
                .writeTimeout((read_timeout + 5), TimeUnit.SECONDS)
                .build();
        String request_uri = public_func.get_url(bot_token, "getUpdates");
        polling_json request_body = new polling_json();
        request_body.offset = offset;
        request_body.timeout = read_timeout;
        RequestBody body = RequestBody.create(public_func.JSON, new Gson().toJson(request_body));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client_new.newCall(request);
        Response response;
        try {
            if (!public_func.check_network(context)) {
                throw new IOException("Network");
            }
            response = call.execute();
            error_magnification = 1;
        } catch (IOException e) {
            e.printStackTrace();
            int sleep_time = 5 * error_magnification;
            public_func.write_log(context, "No network service,try again after " + sleep_time + " seconds");

            magnification = 1;
            if (error_magnification <= 59) {
                error_magnification++;
            }
            try {
                Thread.sleep(sleep_time * 1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            return;

        }
        if (response != null && response.code() == 200) {
            assert response.body() != null;
            String result;
            try {
                result = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject();
            if (result_obj.get("ok").getAsBoolean()) {
                JsonArray result_array = result_obj.get("result").getAsJsonArray();
                for (JsonElement item : result_array) {
                    receive_handle(item.getAsJsonObject());
                }
            }
            if (magnification <= 11) {
                magnification++;
            }
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void receive_handle(JsonObject result_obj) {
        int update_id = result_obj.get("update_id").getAsInt();
        if (update_id >= offset) {
            offset = update_id + 1;
        }
        final message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        JsonObject message_obj = null;
        if (result_obj.has("message")) {
            message_obj = result_obj.get("message").getAsJsonObject();
        }
        if (result_obj.has("channel_post")) {
            message_obj = result_obj.get("channel_post").getAsJsonObject();
        }
        if (message_obj == null) {
            public_func.write_log(context, "Request type is not allowed by security policy");
            return;
        }
        JsonObject from_obj = null;
        if (message_obj.has("from")) {
            from_obj = message_obj.get("from").getAsJsonObject();
        }
        if (message_obj.has("chat")) {
            from_obj = message_obj.get("chat").getAsJsonObject();
        }

        assert from_obj != null;
        String from_id = from_obj.get("id").getAsString();
        if (!Objects.equals(chat_id, from_id)) {
            public_func.write_log(context, "Chat ID[" + from_id + "] not allow");
            return;
        }

        String command = "";
        String request_msg = "";
        if (message_obj.has("text")) {
            request_msg = message_obj.get("text").getAsString();
        }
        if (message_obj.has("entities")) {
            JsonArray entities_arr = message_obj.get("entities").getAsJsonArray();
            JsonObject entities_obj_command = entities_arr.get(0).getAsJsonObject();
            if (entities_obj_command.get("type").getAsString().equals("bot_command")) {
                int command_offset = entities_obj_command.get("offset").getAsInt();
                int command_end_offset = command_offset + entities_obj_command.get("length").getAsInt();
                command = request_msg.substring(command_offset, command_end_offset).trim().toLowerCase();
                if (command.contains("@")) {
                    int command_at_location = command.indexOf("@");
                    command = command.substring(0, command_at_location);
                }
            }
        }

        Log.d(public_func.log_tag, "receive_handle: " + command);

        if (!message_obj.has("reply_to_message")) {
            switch (command) {
                case "/help":
                case "/start":
                    String dual_card = "\n" + getString(R.string.sendsms);
                    if (public_func.get_active_card(context) == 2) {
                        dual_card = "\n" + getString(R.string.sendsms_dual);
                    }
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + dual_card;
                    break;
                case "/ping":
                case "/getinfo":
                    BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
                    String card_info = "";
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        card_info = "\nSIM:" + public_func.get_sim_display_name(context, 0);
                        if (public_func.get_active_card(context) == 2) {
                            card_info = "\nSIM1:" + public_func.get_sim_display_name(context, 0) + "\nSIM2:" + public_func.get_sim_display_name(context, 1);
                        }
                    }
                    request_body.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%\n" + getString(R.string.current_network_connection_status) + public_func.get_network_type(context) + card_info;
                    break;
                case "/log":
                    String result = "\n" + getString(R.string.no_logs);
                    try {
                        FileInputStream file_stream = context.openFileInput("error.log");
                        FileChannel channel = file_stream.getChannel();
                        ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                        buffer.position((int) channel.size());
                        int count = 0;
                        StringBuilder builder = new StringBuilder();
                        for (long i = channel.size() - 1; i >= 0; i--) {
                            char c = (char) buffer.get((int) i);
                            builder.insert(0, c);
                            if (c == '\n') {
                                if (count == 9) {
                                    break;
                                }
                                count++;
                            }
                        }
                        channel.close();
                        if (!builder.toString().isEmpty()) {
                            result = builder.toString();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    request_body.text = getString(R.string.system_message_head) + result;
                    break;
                case "/sendsms":
                case "/sendsms1":
                case "/sendsms2":
                    request_body.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.command_format_error) + "\n\n" + getString(R.string.command_error_tip);
                    String[] msg_send_list = request_msg.split("\n");
                    if (msg_send_list.length > 2) {
                        String msg_send_to = public_func.get_send_phone_number(msg_send_list[1]);
                        if (public_func.is_numeric(msg_send_to)) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 2; i < msg_send_list.length; i++) {
                                if (msg_send_list.length != 3 && i != 2) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(msg_send_list[i]);
                            }
                            if (public_func.get_active_card(context) == 1) {
                                public_func.send_sms(context, msg_send_to, msg_send_content.toString(), -1, -1);
                                return;
                            }
                            int slot = -1;
                            switch (command) {
                                case "/sendsms":
                                case "/sendsms1":
                                    slot = 0;
                                    break;
                                case "/sendsms2":
                                    slot = 1;
                                    break;
                            }
                            request_body.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.unable_to_get_information);
                            int sub_id = public_func.get_sub_id(context, slot);
                            if (sub_id != -1) {
                                public_func.send_sms(context, msg_send_to, msg_send_content.toString(), slot, sub_id);
                                return;
                            }
                        }
                    }

                    break;
                default:
                    request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                    break;
            }
        }

        if (message_obj.has("reply_to_message")) {
            JsonObject reply_obj = message_obj.get("reply_to_message").getAsJsonObject();
            String reply_id = reply_obj.get("message_id").getAsString();
            String message_list_raw = public_func.read_file(context, "message.json");
            JsonObject message_list = new JsonParser().parse(message_list_raw).getAsJsonObject();
            request_body.text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.unable_to_get_information);
            if (message_list.has(reply_id)) {
                JsonObject message_item_obj = message_list.get(reply_id).getAsJsonObject();
                String phone_number = message_item_obj.get("phone").getAsString();
                int card_slot = message_item_obj.get("card").getAsInt();
                int sub_id = -1;
                if (message_item_obj.has("sub_id")) {
                    sub_id = message_item_obj.get("sub_id").getAsInt();
                }
                if (card_slot != -1 && sub_id == -1) {
                    sub_id = public_func.get_sub_id(context, card_slot);
                }
                public_func.send_sms(context, phone_number, request_msg, card_slot, sub_id);
                return;
            }
        }

        String request_uri = public_func.get_url(bot_token, "sendMessage");
        RequestBody body = RequestBody.create(public_func.JSON, new Gson().toJson(request_body));
        Request send_request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(send_request);
        final String error_head = "Send reply failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = error_head + e.getMessage();
                public_func.write_log(context, error_message);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    String error_message = error_head + response.code() + " " + response.body().string();
                    public_func.write_log(context, error_message);
                }
            }
        });
    }

    class stop_broadcast_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}

