package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

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
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class call_receiver extends BroadcastReceiver {
    private int slot;

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case "android.intent.action.PHONE_STATE":
                TelephonyManager telephony = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                call_state_listener custom_phone_listener = new call_state_listener(context, slot);
                telephony.listen(custom_phone_listener, PhoneStateListener.LISTEN_CALL_STATE);
                break;
            case "android.intent.action.SUBSCRIPTION_PHONE_STATE":
                slot = intent.getIntExtra("slot", -1);
        }
    }
}

class call_state_listener extends PhoneStateListener {
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private Context context;
    private int slot;

    call_state_listener(Context context, int slot) {
        super();
        this.context = context;
        this.slot = slot;
    }

    public void onCallStateChanged(int state, String incomingNumber) {
        if (lastState == TelephonyManager.CALL_STATE_RINGING
                && state == TelephonyManager.CALL_STATE_IDLE) {
            sendSmgWhenMissedCall(incomingNumber);
        }

        lastState = state;
    }

    @SuppressLint("MissingPermission")
    private void sendSmgWhenMissedCall(String incomingNumber) {
        SubscriptionManager manager = SubscriptionManager.from(context);
        String DualSim = "";
        if (manager.getActiveSubscriptionInfoCount() == 2) {
            if (slot != -1) {
                DualSim = "\n" + context.getString(R.string.SIM_card_slot) + (slot + 1);
            }
        }

        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Receive Phone:Uninitialized");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
        final request_json request_body = new request_json();
        request_body.chat_id = chat_id;
        String display_address = incomingNumber;
        String display_name = public_func.get_phone_name(context, incomingNumber);
        if (display_name != null) {
            display_address = display_name + "(" + incomingNumber + ")";
        }
        request_body.text = context.getString(R.string.missed_call_head) + DualSim + "\n" + context.getString(R.string.Incoming_number) + display_address;
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj();
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Looper.prepare();
                String error_message = "Send Missed Call Error:" + e.getMessage();
                public_func.write_log(context, error_message);
                Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                Log.i(public_func.log_tag, error_message);
                if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    if (sharedPreferences.getBoolean("fallback_sms", false)) {
                        String msg_send_to = sharedPreferences.getString("trusted_phone_number", null);
                        String msg_send_content = request_body.text;
                        if (msg_send_to != null) {
                            public_func.send_sms(msg_send_to, msg_send_content, -1);
                        }
                    }
                }
                Looper.loop();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    Looper.prepare();
                    assert response.body() != null;
                    String error_message = "Send Missed Call Error:" + response.body().string();
                    public_func.write_log(context, error_message);
                    Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                    Log.i(public_func.log_tag, error_message);
                    Looper.loop();
                }
            }
        });
    }
}
