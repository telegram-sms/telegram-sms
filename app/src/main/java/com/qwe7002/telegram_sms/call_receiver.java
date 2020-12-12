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
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.const_value;
import com.qwe7002.telegram_sms.static_class.log_func;
import com.qwe7002.telegram_sms.static_class.network_func;
import com.qwe7002.telegram_sms.static_class.other_func;
import com.qwe7002.telegram_sms.static_class.resend_func;
import com.qwe7002.telegram_sms.static_class.sms_func;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
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
    public void onReceive(Context context, @NotNull Intent intent) {
        Paper.init(context);
        Log.d("call_receiver", "Receive action: " + intent.getAction());
        switch (Objects.requireNonNull(intent.getAction())) {
            case "android.intent.action.PHONE_STATE":
                if (intent.getStringExtra("incoming_number") != null) {
                    incoming_number = intent.getStringExtra("incoming_number");
                }
                TelephonyManager telephony = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                call_status_listener custom_phone_listener = new call_status_listener(context, slot, incoming_number);
                assert telephony != null;
                telephony.listen(custom_phone_listener, PhoneStateListener.LISTEN_CALL_STATE);
                break;
            case "android.intent.action.SUBSCRIPTION_PHONE_STATE":
                slot = intent.getIntExtra("slot", -1);
                break;

        }
    }

    static class call_status_listener extends PhoneStateListener {
        private static int last_receive_status = TelephonyManager.CALL_STATE_IDLE;
        private static String incoming_number;
        private final Context context;
        private final int slot;

        call_status_listener(Context context, int slot, String incoming_number) {
            super();
            this.context = context;
            this.slot = slot;
            call_status_listener.incoming_number = incoming_number;
        }

        public void onCallStateChanged(int now_state, String now_incoming_number) {
            if (last_receive_status == TelephonyManager.CALL_STATE_RINGING
                    && now_state == TelephonyManager.CALL_STATE_IDLE) {
                final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.");
                    return;
                }
                String bot_token = sharedPreferences.getString("bot_token", "");
                String chat_id = sharedPreferences.getString("chat_id", "");
                String request_uri = network_func.get_url(bot_token, "sendMessage");
                final request_message request_body = new request_message();
                request_body.chat_id = chat_id;
                String dual_sim = other_func.get_dual_sim_card_display(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                request_body.text = "[" + dual_sim + context.getString(R.string.missed_call_head) + "]" + "\n" + context.getString(R.string.Incoming_number) + call_status_listener.incoming_number;
                String request_body_raw = new Gson().toJson(request_body);
                RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
                OkHttpClient okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                final String error_head = "Send missed call error:";
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        e.printStackTrace();
                        log_func.write_log(context, error_head + e.getMessage());
                        sms_func.send_fallback_sms(context, request_body.text, other_func.get_sub_id(context, slot));
                        resend_func.add_resend_loop(context, request_body.text);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        assert response.body() != null;
                        if (response.code() != 200) {
                            String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                            log_func.write_log(context, error_message);
                            resend_func.add_resend_loop(context, request_body.text);
                        } else {
                            String result = Objects.requireNonNull(response.body()).string();
                            if (!other_func.is_phone_number(call_status_listener.incoming_number)) {
                                log_func.write_log(context, "[" + call_status_listener.incoming_number + "] Not a regular phone number.");
                                return;
                            }
                            other_func.add_message_list(other_func.get_message_id(result), call_status_listener.incoming_number, slot);
                        }
                    }
                });
            }
            last_receive_status = now_state;
        }
    }
}


