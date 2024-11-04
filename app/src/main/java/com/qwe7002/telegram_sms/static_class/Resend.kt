package com.qwe7002.telegram_sms.static_class;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.qwe7002.telegram_sms.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.paperdb.Paper;

public class
resend {
    public static void addResendLoop(Context context, String message) {
        ArrayList<String> resend_list;
        Paper.init(context);
        resend_list = Paper.book().read("resend_list", new ArrayList<>());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
        message += "\n"+context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
        assert resend_list != null;
        resend_list.add(message);
        Paper.book().write("resend_list", resend_list);
    }

}
