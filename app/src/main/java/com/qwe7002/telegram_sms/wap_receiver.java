package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import okhttp3.MediaType;

public class wap_receiver extends BroadcastReceiver {
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d("sms-tg", "onReceive: wap");

    }
}
