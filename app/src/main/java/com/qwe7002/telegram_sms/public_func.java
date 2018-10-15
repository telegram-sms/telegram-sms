package com.qwe7002.telegram_sms;

import java.util.ArrayList;

public class public_func {
    static void send_sms(String send_to, String content, int subid) {
        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        if (subid != -1) {
            smsManager = android.telephony.SmsManager.getSmsManagerForSubscriptionId(subid);
        }
        ArrayList<String> divideContents = smsManager.divideMessage(content);
        smsManager.sendMultipartTextMessage(send_to, null, divideContents, null, null);

    }
}
