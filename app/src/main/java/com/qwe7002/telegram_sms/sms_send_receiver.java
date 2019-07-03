package com.qwe7002.telegram_sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class sms_send_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        int sub = extras.getInt("sub_id");
        context.getApplicationContext().unregisterReceiver(this);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Receive Phone:Uninitialized");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        final message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        int message_id = Integer.parseInt(Objects.requireNonNull(extras.getString("message_id")));
        if (message_id != -1) {
            Log.d(public_func.log_tag, "sms_send_receiver: Find the message_id and switch to edit mode.");
            request_uri = public_func.get_url(bot_token, "editMessageText");
            request_body.message_id = message_id;
        }
        request_body.text = extras.getString("message_text") + "\n" + context.getString(R.string.status);
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                request_body.text += context.getString(R.string.success);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                request_body.text += context.getString(R.string.send_failed);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                request_body.text += context.getString(R.string.airplane_mode);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                request_body.text += context.getString(R.string.no_network);
                break;
        }
        if (!public_func.check_network_status(context)) {
            public_func.write_log(context, public_func.network_error);
            public_func.send_fallback_sms(context, request_body.text, sub);
            return;
        }
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS status failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = error_head + e.getMessage();
                public_func.write_log(context, error_message);
                public_func.send_fallback_sms(context, request_body.text, sub);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                    public_func.write_log(context, error_message);
                }
            }
        });
    }
}
