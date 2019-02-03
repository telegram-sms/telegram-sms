package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

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
        context.getApplicationContext().unregisterReceiver(this);
        intent.getCategories();
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Receive Phone:Uninitialized");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        final request_json request_body = new request_json();
        request_body.chat_id = chat_id;
        SubscriptionManager manager = SubscriptionManager.from(context);
        String dual_sim = "";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (manager.getActiveSubscriptionInfoCount() >= 2) {
                String sim_card = Objects.requireNonNull(intent.getExtras()).getString("sim_card");
                int slot = -1;
                assert sim_card != null;
                switch (sim_card) {
                    case "1":
                        slot = 0;
                        break;
                    case "2":
                        slot = 1;
                        break;
                }
                String display_name = public_func.get_sim_name_title(context, slot);
                String display = "";
                if (display_name != null) {
                    display = "(" + display_name + ")";
                }
                dual_sim = "SIM" + sim_card + display + " ";
            }
        }
        String send_to = Objects.requireNonNull(intent.getExtras()).getString("send_to");
        String display_to_address = send_to;
        String display_to_name = public_func.get_phone_name(context, display_to_address);
        if (display_to_name != null) {
            display_to_address = display_to_name + "(" + send_to + ")";
        }
        String content = Objects.requireNonNull(intent.getExtras()).getString("content");
        request_body.text = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + display_to_address + "\n" + context.getString(R.string.content) + content + "\n" + context.getString(R.string.status);
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                request_body.text += context.getString(R.string.success);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                request_body.text += context.getString(R.string.send_failed);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                request_body.text += context.getString(R.string.airplan_mode);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                request_body.text += context.getString(R.string.no_network);
                break;
        }
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj();
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error_message = "failed to send SMS:" + e.getMessage();
                public_func.write_log(context, error_message);
                public_func.write_log(context, "message body:\n" + request_body.text);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    String error_message = "failed to send SMS:" + response.body().string();
                    public_func.write_log(context, error_message);
                    public_func.write_log(context, "message body:\n" + request_body.text);
                }
            }
        });
    }
}
