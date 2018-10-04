package com.qwe7002.telegram_sms;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class sms_receiver extends BroadcastReceiver {
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    public void onReceive(final Context context, Intent intent) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);

        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
        try {
            if (bot_token.isEmpty() || chat_id.isEmpty()) {
                Log.i("tg-sms", "onReceive: token not found");
                return;
            }
            if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    assert pdus != null;
                    final SmsMessage[] messages = new SmsMessage[pdus.length];
                    for (int i = 0; i < pdus.length; i++) {
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    }
                    if (messages.length > 0) {
                        String msgBody = messages[0].getMessageBody();
                        String msgAddress = messages[0].getOriginatingAddress();
                        Date date = new Date(messages[0].getTimestampMillis());
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String msgDate = format.format(date);
                        request_json request_body = new request_json();
                        request_body.chat_id = chat_id;
                        request_body.text = "From: " + msgAddress + "\nDate: " + msgDate + "\nContent: " + msgBody;
                        Gson gson = new Gson();
                        String request_body_raw = gson.toJson(request_body);
                        RequestBody body = RequestBody.create(JSON, request_body_raw);
                        OkHttpClient okHttpClient = new OkHttpClient();
                        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                        Call call = okHttpClient.newCall(request);
                        call.enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                Looper.prepare();
                                Toast.makeText(context, "SendSMSError:" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Looper.loop();
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                if (response.code() != 200) {
                                    Looper.prepare();
                                    assert response.body() != null;
                                    Toast.makeText(context, "SendSMSError:" + response.body().string(), Toast.LENGTH_SHORT).show();
                                    Looper.loop();
                                }
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
