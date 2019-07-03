package com.qwe7002.telegram_sms;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
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


public class sim_status_receiver extends BroadcastReceiver {
    static int last_status;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        String message = context.getString(R.string.system_message_head) + "\n";
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(public_func.log_tag, "Uninitialized, SIM status receiver is deactivated");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        assert tm != null;
        int state = tm.getSimState();
        if (last_status == state) {
            Log.d(public_func.log_tag, "sim_status_receiver: SIM status is the same as the previous one");
            return;
        }
        last_status = state;
        switch (state) {
            case TelephonyManager.SIM_STATE_READY:
                message += context.getString(R.string.sim_card_insert);
                break;
            case TelephonyManager.SIM_STATE_UNKNOWN:
            case TelephonyManager.SIM_STATE_ABSENT:
                message += context.getString(R.string.sim_card_remove);
                break;
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                message += context.getString(R.string.sim_card_lock);
                break;
            default:
                Log.d("sim_status", "onReceive: " + state);
                message += context.getString(R.string.sim_card_error);
                break;
        }
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        request_body.text = message;
        if (!public_func.check_network_status(context)) {
            public_func.write_log(context, public_func.network_error);
            return;
        }
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS status failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                String error_message = error_head + e.getMessage();
                public_func.write_log(context, error_message);
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
