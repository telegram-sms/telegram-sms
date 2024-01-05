package com.airfreshener.telegram_sms.static_class;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.resend_service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.paperdb.Paper;

public class
resend_func {
    public static void add_resend_loop(Context context, String message) {
        ArrayList<String> resend_list;
        Paper.init(context);
        resend_list = Paper.book().read("resend_list", new ArrayList<>());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
        message += "\n"+context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
        resend_list.add(message);
        Paper.book().write("resend_list", resend_list);
        start_resend(context);
    }

    public static void start_resend(Context context) {
        Intent intent = new Intent(context, resend_service.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
