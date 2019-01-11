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
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

class public_func {
    private static final String log_tag = "tg-sms";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

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

    static void send_sms(Context context, String send_to, String content, int sub_id) {
        android.telephony.SmsManager sms_manager;
        String sim_card = "1";
        switch (sub_id) {
            case -1:
                sms_manager = android.telephony.SmsManager.getDefault();

                break;
            default:
                sms_manager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(sub_id);
                sim_card = "1";
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new send_status_receiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("sim_card", sim_card);
        sent_intent.putExtra("send_to", send_to);
        sent_intent.putExtra("content", content);
        PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_CANCEL_CURRENT);
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
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

    static void start_service(Context context, SharedPreferences sharedPreferences) {
        Intent battery_service = new Intent(context, battery_monitoring_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_long_polling_service.class);
        boolean chat_command_switch = sharedPreferences.getBoolean("chat_command", false);
        boolean battery_switch = sharedPreferences.getBoolean("battery_monitoring_switch", false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (battery_switch) {
                context.startForegroundService(battery_service);
            }
            if (chat_command_switch) {
                context.startForegroundService(chat_long_polling_service);
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (battery_switch) {
                context.startService(battery_service);
            }
            if (chat_command_switch) {
                context.startService(chat_long_polling_service);
            }
        }
    }

    static String get_phone_name(Context context, String phone_number) {
        String contact_name = null;
        if (checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
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
        }
        return contact_name;
    }

    static void write_log(Context context, String log) {
        Log.i(public_func.log_tag, log);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        String error_log = read_log_file(context) + "\n" + simpleDateFormat.format(date) + " " + log;
        write_log_file(context, error_log);
    }

    static void write_log_file(Context context, String write_string) {
        try {
            FileOutputStream file_stream = context.openFileOutput("error.log", MODE_PRIVATE);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
            file_stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String read_log_file(Context context) {
        String result = "";
        try {
            FileInputStream file_stream = context.openFileInput("error.log");
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
