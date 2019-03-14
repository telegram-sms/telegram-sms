package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

class public_func {
    static final String log_tag = "telegram-sms";
    static final String broadcast_stop_service = "com.qwe7002.telegram_sms.stop_all";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static String get_send_phone_number(String phone_number) {
        return phone_number.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
    }

    static String get_dual_sim_card_display(Context context, int slot, SharedPreferences sharedPreferences) {
        String dual_sim = "";
        if (slot == -1) {
            return dual_sim;
        }
        if (public_func.get_active_card(context) >= 2) {
            String display_name = public_func.get_sim_name_title(context, sharedPreferences, slot);
            dual_sim = "SIM" + (slot + 1) + display_name + " ";
        }
        return dual_sim;
    }
    static boolean check_network(Context context) {

        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkInfo networkinfo = manager.getActiveNetworkInfo();
        return networkinfo != null && networkinfo.isAvailable();
    }

    static String get_url(String token, String func) {
        return "https://api.telegram.org/bot" + token + "/" + func;
    }

    static OkHttpClient get_okhttp_obj() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    static boolean is_numeric(String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                continue; //Allowed characters +
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    static String get_network_type(Context context) {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network_info = connect_manager.getActiveNetworkInfo();
        if (network_info == null) {
            return net_type;
        }
        switch (network_info.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                net_type = "WIFI";
                break;
            case ConnectivityManager.TYPE_MOBILE:
                switch (network_info.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        net_type = "LTE/4G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                        net_type = "3G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        net_type = "2G";
                        break;
                }
        }
        return net_type;
    }

    static void send_sms(Context context, String send_to, String content, int slot, int sub_id) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        android.telephony.SmsManager sms_manager;
        switch (sub_id) {
            case -1:
                sms_manager = android.telephony.SmsManager.getDefault();
                break;
            default:
                sms_manager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dual_sim = get_dual_sim_card_display(context, slot, sharedPreferences);
        String display_to_address = send_to;
        String display_to_name = public_func.get_contact_name(context, display_to_address);
        if (display_to_name != null) {
            display_to_address = display_to_name + "(" + send_to + ")";
        }
        String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + display_to_address + "\n" + context.getString(R.string.content) + content;
        String message_id = "-1";
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj();
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200 || response.body() == null) {
                throw new IOException(String.valueOf(response.code()));
            }
            message_id = get_message_id(response.body().string());
        } catch (IOException e) {
            public_func.write_log(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new sms_send_receiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("message_id", message_id);
        sent_intent.putExtra("message_text", send_content);
        sent_intent.putExtra("sub_id", sms_manager.getSubscriptionId());
        PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_CANCEL_CURRENT);
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
    }

    static void send_fallback_sms(String send_to, String content, int sub_id) {
        android.telephony.SmsManager sms_manager;
        switch (sub_id) {
            case -1:
                sms_manager = android.telephony.SmsManager.getDefault();
                break;
            default:
                sms_manager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, null, null);
    }

    static String get_message_id(String result) {
        JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsString();
    }

    static Notification get_notification_obj(Context context, String notification_name) {
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_name, public_func.log_tag,
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);

            notification = new Notification.Builder(context, notification_name)
                    .setAutoCancel(false)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setTicker(context.getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(notification_name + context.getString(R.string.service_is_running))
                    .build();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notification = new Notification.Builder(context)
                    .setAutoCancel(false)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setTicker(context.getString(R.string.app_name))
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(notification_name + context.getString(R.string.service_is_running))
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
        }
        return notification;
    }

    static void stop_all_service(Context context) {
        Intent intent = new Intent(broadcast_stop_service);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void start_service(Context context, Boolean battery_switch, Boolean chat_command_switch) {
        Intent battery_service = new Intent(context, battery_monitoring_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_long_polling_service.class);
        boolean foreground = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            foreground = true;
            if (battery_switch) {
                context.startForegroundService(battery_service);
            }
            if (chat_command_switch) {
                context.startForegroundService(chat_long_polling_service);
            }
        }
        if (!foreground) {
            if (battery_switch) {
                context.startService(battery_service);
            }
            if (chat_command_switch) {
                context.startService(chat_long_polling_service);
            }
        }
    }

    static int get_sub_id(Context context, int slot) {
        int active_card = public_func.get_active_card(context);
        if (active_card >= 2) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return -1;
            }
            return SubscriptionManager.from(context).getActiveSubscriptionInfoForSimSlotIndex(slot).getSubscriptionId();
        }
        return -1;
    }

    static int get_active_card(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -1;
        }
        return SubscriptionManager.from(context).getActiveSubscriptionInfoCount();
    }

    private static String get_sim_name_title(Context context, SharedPreferences sharedPreferences, int slot) {
        String result = "";
        if (sharedPreferences.getBoolean("display_dual_sim_display_name", false)) {
            result = "(" + get_sim_display_name(context, slot) + ")";
        }
        return result;
    }

    static String get_sim_display_name(Context context, int slot) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "Unknown";
        }
        SubscriptionInfo info = SubscriptionManager.from(context).getActiveSubscriptionInfoForSimSlotIndex(slot);
        if (info == null) {
            if (get_active_card(context) == 1 && slot == 0) {
                info = SubscriptionManager.from(context).getActiveSubscriptionInfoForSimSlotIndex(1);
            }
        }
        assert info != null;
        String result = info.getDisplayName().toString();
        if (info.getDisplayName().toString().contains("CARD") || info.getDisplayName().toString().contains("SUB")) {
            result = info.getCarrierName().toString();
        }
        return result;
    }

    static String get_contact_name(Context context, String phone_number) {
        String contact_name = null;
        if (checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone_number));
                String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
                Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        String cursor_name = cursor.getString(0);
                        if (!cursor_name.isEmpty())
                            contact_name = cursor_name;
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return contact_name;
    }

    static void write_log(Context context, String log) {
        Log.i(public_func.log_tag, log);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date ts = new Date(System.currentTimeMillis());
        String error_log = read_file(context, "error.log") + "\n" + simpleDateFormat.format(ts) + " " + log;
        write_file(context, "error.log", error_log);
    }

    static String read_log(Context context) {
        return read_file(context, "error.log");
    }

    static void add_message_list(Context context, String message_id, String phone, int slot) {
        String message_list_raw = public_func.read_file(context, "message.json");
        if (message_list_raw.length() == 0) {
            message_list_raw = "{}";
        }
        JsonObject message_list_obj = new JsonParser().parse(message_list_raw).getAsJsonObject();
        JsonObject object = new JsonObject();
        object.addProperty("phone", phone);
        object.addProperty("card", slot);
        message_list_obj.add(message_id, object);
        public_func.write_file(context, "message.json", new Gson().toJson(message_list_obj));
    }

    static void write_file(Context context, String file_name, String write_string) {
        try {
            FileOutputStream file_stream = context.openFileOutput(file_name, MODE_PRIVATE);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
            file_stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String read_file(Context context, String file_name) {
        String result = "";
        try {
            FileInputStream file_stream = context.openFileInput(file_name);
            int length = file_stream.available();
            byte[] buffer = new byte[length];
            file_stream.read(buffer);
            result = new String(buffer, StandardCharsets.UTF_8);
            file_stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;

    }
}
