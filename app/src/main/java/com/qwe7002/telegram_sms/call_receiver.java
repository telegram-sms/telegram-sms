package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class call_receiver extends BroadcastReceiver {
    private static int slot;
    private static String incoming_number;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        switch (Objects.requireNonNull(intent.getAction())) {
            case "android.intent.action.PHONE_STATE":
                if (intent.getStringExtra("incoming_number") != null) {
                    incoming_number = intent.getStringExtra("incoming_number");
                }
                TelephonyManager telephony = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                call_state_listener custom_phone_listener = new call_state_listener(context, slot, incoming_number);
                assert telephony != null;
                telephony.listen(custom_phone_listener, PhoneStateListener.LISTEN_CALL_STATE);
                break;
            case "android.intent.action.SUBSCRIPTION_PHONE_STATE":
                slot = intent.getIntExtra("slot", -1);
                break;

        }
    }
}

class call_state_listener extends PhoneStateListener {
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private static String incoming_number;
    private final Context context;
    private final int slot;

    call_state_listener(Context context, int slot, String incoming_number) {
        super();
        this.context = context;
        this.slot = slot;
        call_state_listener.incoming_number = incoming_number;
    }

    public void onCallStateChanged(int state, String incomingNumber) {
        if (lastState == TelephonyManager.CALL_STATE_RINGING
                && state == TelephonyManager.CALL_STATE_IDLE) {
            final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
            if (!sharedPreferences.getBoolean("initialized", false)) {
                Log.i(public_func.log_tag, "Uninitialized, Phone receiver is deactivated.");
                return;
            }
            String bot_token = sharedPreferences.getString("bot_token", "");
            String chat_id = sharedPreferences.getString("chat_id", "");
            String request_uri = public_func.get_url(bot_token, "sendMessage");
            final message_json request_body = new message_json();
            request_body.chat_id = chat_id;
            String dual_sim = public_func.get_dual_sim_card_display(context, slot, sharedPreferences);
            request_body.text = "[" + dual_sim + context.getString(R.string.missed_call_head) + "]" + "\n" + context.getString(R.string.Incoming_number) + incoming_number;

            if (!public_func.check_network_status(context)) {
                public_func.write_log(context, public_func.network_error);
                public_func.send_fallback_sms(context, request_body.text, public_func.get_sub_id(context, slot));
                return;
            }

            String request_body_raw = new Gson().toJson(request_body);
            RequestBody body = RequestBody.create(request_body_raw, public_func.JSON);
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            final String error_head = "Send missed call error:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    String error_message = error_head + e.getMessage();
                    public_func.write_log(context, error_message);
                    public_func.send_fallback_sms(context, request_body.text, public_func.get_sub_id(context, slot));

                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    assert response.body() != null;
                    if (response.code() != 200) {
                        String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                        public_func.write_log(context, error_message);
                    } else {
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject().get("result").getAsJsonObject();
                        String message_id = result_obj.get("message_id").getAsString();
                        public_func.add_message_list(context, message_id, incoming_number, slot, public_func.get_sub_id(context, slot));
                    }
                }
            });
        }

        lastState = state;
    }

}
