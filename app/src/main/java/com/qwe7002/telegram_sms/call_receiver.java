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
import com.qwe7002.telegram_sms.data_structure.sendMessageBody;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.static_class.sms;
import com.qwe7002.telegram_sms.value.constValue;

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
    private static String incomingNumber;

    @Override
    public void onReceive(Context context, @NotNull Intent intent) {
        Paper.init(context);
        Log.d("call_receiver", "Receive action: " + intent.getAction());
        switch (Objects.requireNonNull(intent.getAction())) {
            case "android.intent.action.PHONE_STATE":
                if (intent.getStringExtra("incoming_number") != null) {
                    incomingNumber = intent.getStringExtra("incoming_number");
                }
                TelephonyManager telephony = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                callStatusListener custom_phone_listener = new callStatusListener(context, slot, incomingNumber);
                assert telephony != null;
                telephony.listen(custom_phone_listener, PhoneStateListener.LISTEN_CALL_STATE);
                break;
            case "android.intent.action.SUBSCRIPTION_PHONE_STATE":
                slot = intent.getIntExtra("slot", -1);
                break;

        }
    }

    static class callStatusListener extends PhoneStateListener {
        private static int lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE;
        private static String incomingNumber;
        private final Context context;
        private final int slot;

        callStatusListener(Context context, int slot, String incomingNumber) {
            super();
            this.context = context;
            this.slot = slot;
            callStatusListener.incomingNumber = incomingNumber;
        }

        public void onCallStateChanged(int now_state, String now_incoming_number) {
            if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING
                    && now_state == TelephonyManager.CALL_STATE_IDLE) {
                final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.");
                    return;
                }
                String botToken = sharedPreferences.getString("bot_token", "");
                String chatId = sharedPreferences.getString("chat_id", "");
                String messageThreadId = sharedPreferences.getString("message_thread_id", "");
                String requestUri = network.getUrl(botToken, "sendMessage");
                final sendMessageBody requestBody = new sendMessageBody();
                requestBody.chat_id = chatId;
                requestBody.message_thread_id = messageThreadId;
                String dual_sim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                requestBody.text = "[" + dual_sim + context.getString(R.string.missed_call_head) + "]" + "\n" + context.getString(R.string.Incoming_number) + callStatusListener.incomingNumber;
                String requestBodyRaw = new Gson().toJson(requestBody);
                RequestBody body = RequestBody.create(requestBodyRaw, constValue.JSON);
                OkHttpClient okhttpObj = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okhttpObj.newCall(request);
                final String error_head = "Send missed call error:";
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        e.printStackTrace();
                        log.writeLog(context, error_head + e.getMessage());
                        sms.fallbackSMS(context, requestBody.text, other.getSubId(context, slot));
                        resend.addResendLoop(context, requestBody.text);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        assert response.body() != null;
                        if (response.code() != 200) {
                            String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                            log.writeLog(context, error_message);
                            resend.addResendLoop(context, requestBody.text);
                        } else {
                            String result = Objects.requireNonNull(response.body()).string();
                            if (!other.isPhoneNumber(callStatusListener.incomingNumber)) {
                                log.writeLog(context, "[" + callStatusListener.incomingNumber + "] Not a regular phone number.");
                                return;
                            }
                            other.addMessageList(other.getMessageId(result), callStatusListener.incomingNumber, slot);
                        }
                    }
                });
            }
            lastReceiveStatus = now_state;
        }
    }
}


