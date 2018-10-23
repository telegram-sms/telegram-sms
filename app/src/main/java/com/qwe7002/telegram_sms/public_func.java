package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.Objects;

import okhttp3.MediaType;

import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class public_func {
    static final String log_tag = "tg-sms";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    static void send_sms(String send_to, String content, int subid) {
        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        if (subid != -1) {
            smsManager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(subid);
        }
        ArrayList<String> divideContents = smsManager.divideMessage(content);
        smsManager.sendMultipartTextMessage(send_to, null, divideContents, null, null);

    }
    public static String get_phone_name(Context context, String address){
        if(checkSelfPermission(context, Manifest.permission.READ_CONTACTS)!= PackageManager.PERMISSION_GRANTED){
            return null;
        }
        String contactName = null;
        ContentResolver cr = context.getContentResolver();
        Cursor pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                new String[] { address }, null);
        if (Objects.requireNonNull(pCur).moveToFirst()) {
            contactName = pCur
                    .getString(pCur
                            .getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            pCur.close();
        }
        return contactName;

    }
}
