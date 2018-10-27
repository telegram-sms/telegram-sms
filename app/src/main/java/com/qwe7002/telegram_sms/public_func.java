package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

import okhttp3.MediaType;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class public_func {
    public static final String log_tag = "tg-sms";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static void send_sms(String send_to, String content, int subid) {
        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        if (subid != -1) {
            smsManager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(subid);
        }
        ArrayList<String> divideContents = smsManager.divideMessage(content);
        smsManager.sendMultipartTextMessage(send_to, null, divideContents, null, null);

    }

    public static String get_phone_name(Context context, String address) {
        if (checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        String contactName = null;
        ContentResolver cr = context.getContentResolver();
        Cursor pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                new String[]{address}, null);
        if (Objects.requireNonNull(pCur).moveToFirst()) {
            contactName = pCur
                    .getString(pCur
                            .getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            pCur.close();
        }
        return contactName;

    }

    public static void write_log(Context context, String log) {
        Log.i(public_func.log_tag, log);
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        SharedPreferences sharedPreferences = context.getSharedPreferences("log-data", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String error_log = sharedPreferences.getString("error_log", "") + "\n" + simpleDateFormat.format(date)+" " + log;
        editor.putString("error_log", error_log);
        editor.apply();

    }
}
