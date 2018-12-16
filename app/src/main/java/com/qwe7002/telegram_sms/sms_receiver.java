package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class sms_receiver extends BroadcastReceiver {
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Receive SMS:Uninitialized");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String dual_sim = "";
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (manager.getActiveSubscriptionInfoCount() == 2) {
                    int slot = bundle.getInt("slot", -1);
                    if (slot != -1) {
                        dual_sim = "SIM" + (slot + 1) + " ";
                    }
                }
            }
            final int sub = bundle.getInt("subscription", -1);
            Object[] pdus = (Object[]) bundle.get("pdus");
            assert pdus != null;
            final SmsMessage[] messages = new SmsMessage[pdus.length];
            for (int i = 0; i < pdus.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }
            if (messages.length > 0) {
                StringBuilder msgBody = new StringBuilder();
                for (SmsMessage item : messages) {
                    msgBody.append(item.getMessageBody());
                }
                String msg_address = messages[0].getOriginatingAddress();

                final request_json request_body = new request_json();
                request_body.chat_id = chat_id;
                String display_address = msg_address;
                String display_name = public_func.get_phone_name(context, display_address);
                if (display_name != null) {
                    display_address = display_name + "(" + display_address + ")";
                }
                request_body.text = "[" + dual_sim + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + display_address + "\n" + context.getString(R.string.content) + msgBody;
                assert msg_address != null;
                if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    if (msg_address.equals(sharedPreferences.getString("trusted_phone_number", null))) {
                        String[] msg_send_list = msgBody.toString().split("\n");
                        String msg_send_to = msg_send_list[0].trim().replaceAll(" ", "");
                        if (public_func.is_numeric(msg_send_to) && msg_send_list.length != 1) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 1; i < msg_send_list.length; i++) {
                                if (msg_send_list.length != 2 && i != 1) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(msg_send_list[i]);
                            }
                            String display_to_address = msg_send_to;
                            String display_to_name = public_func.get_phone_name(context, display_to_address);
                            if (display_to_name != null) {
                                display_to_address = display_to_name + "(" + msg_send_to + ")";
                            }
                            public_func.send_sms(msg_send_to, msg_send_content.toString(), sub);
                            request_body.text = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + display_to_address + "\n" + context.getString(R.string.content) + msg_send_content.toString();
                        }
                    }
                }

                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
                OkHttpClient okhttp_client = public_func.get_okhttp_obj();
                okhttp_client.retryOnConnectionFailure();
                okhttp_client.connectTimeoutMillis();
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Looper.prepare();
                        String error_message = "Send SMS Error:" + e.getMessage();
                        public_func.write_log(context, error_message);
                        Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                        public_func.write_log(context, "message body:" + request_body.text);
                        if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            if (sharedPreferences.getBoolean("fallback_sms", false)) {
                                String msg_send_to = sharedPreferences.getString("trusted_phone_number", null);
                                String msg_send_content = request_body.text;
                                if (msg_send_to != null) {
                                    public_func.send_sms(msg_send_to, msg_send_content, sub);
                                }
                            }
                        }
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.code() != 200) {
                            Looper.prepare();
                            String error_message = "Send SMS Error:" + response.body().string();
                            public_func.write_log(context, error_message);
                            public_func.write_log(context, "message body:" + request_body.text);
                            Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                            Looper.loop();
                        }
                    }
                });
            }
        }
    }
}

