package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class wap_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        public_func.write_log(context, "Received wap message.");
    }
}
