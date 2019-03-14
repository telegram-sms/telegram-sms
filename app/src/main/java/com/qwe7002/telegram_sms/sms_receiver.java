package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.telephony.SmsMessage;
import android.util.Log;

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
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        final boolean is_default = Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(public_func.log_tag, "Uninitialized, SMS receiver is deactivated");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            if (is_default) {
                //When it is the default application, it will receive two broadcasts.
                return;
            }
        }
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            int slot = bundle.getInt("slot", -1);
            String dual_sim = public_func.get_dual_sim_card_display(context, slot, sharedPreferences);

            final int sub = bundle.getInt("subscription", -1);
            Object[] pdus = (Object[]) bundle.get("pdus");
            assert pdus != null;
            final SmsMessage[] messages = new SmsMessage[pdus.length];
            for (int i = 0; i < pdus.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                if (is_default) {
                    ContentValues values = new ContentValues();
                    values.put(Telephony.Sms.ADDRESS, messages[i].getOriginatingAddress());
                    values.put(Telephony.Sms.BODY, messages[i].getMessageBody());
                    values.put(Telephony.Sms.SUBSCRIPTION_ID, String.valueOf(sub));
                    values.put(Telephony.Sms.READ, "1");
                    context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, values);
                }

            }
            if (messages.length > 0) {
                StringBuilder msgBody = new StringBuilder();
                for (SmsMessage item : messages) {
                    msgBody.append(item.getMessageBody());
                }
                String msg_address = messages[0].getOriginatingAddress();

                final message_json request_body = new message_json();
                request_body.chat_id = chat_id;
                String display_address = msg_address;
                if (display_address != null) {
                    String display_name = public_func.get_contact_name(context, display_address);
                    if (display_name != null) {
                        display_address = display_name + "(" + display_address + ")";
                    }
                }
                request_body.text = "[" + dual_sim + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + display_address + "\n" + context.getString(R.string.content) + msgBody;
                assert msg_address != null;
                if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    if (msg_address.equals(sharedPreferences.getString("trusted_phone_number", null))) {
                        String[] msg_send_list = msgBody.toString().split("\n");
                        String msg_send_to = public_func.get_send_phone_number(msg_send_list[0]);
                        if (msgBody.toString().equals("restart-service")) {
                            public_func.stop_all_service(context.getApplicationContext());
                            public_func.start_service(context.getApplicationContext(), sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                            request_body.text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
                        }
                        if (public_func.is_numeric(msg_send_to) && msg_send_list.length != 1) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 1; i < msg_send_list.length; i++) {
                                if (msg_send_list.length != 2 && i != 1) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(msg_send_list[i]);
                            }
                            new Thread(() -> public_func.send_sms(context, msg_send_to, msg_send_content.toString(), slot, sub)).start();
                            return;
                        }
                    }
                }

                String request_body_json = new Gson().toJson(request_body);
                RequestBody body = RequestBody.create(public_func.JSON, request_body_json);
                OkHttpClient okhttp_client = public_func.get_okhttp_obj();
                okhttp_client.retryOnConnectionFailure();
                okhttp_client.connectTimeoutMillis();
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        String error_message = "SMS forwarding failed:" + e.getMessage();
                        public_func.write_log(context, error_message);
                        if (checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED && sharedPreferences.getBoolean("fallback_sms", false)) {
                            String msg_send_to = sharedPreferences.getString("trusted_phone_number", null);
                            String msg_send_content = request_body.text;
                            if (msg_send_to != null) {
                                public_func.send_fallback_sms(msg_send_to, msg_send_content, sub);
                            }
                        }
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.code() != 200) {
                            assert response.body() != null;
                            String error_message = "SMS forwarding failed:" + response.body().string();
                            public_func.write_log(context, error_message);
                        }
                        if (response.code() == 200) {
                            assert response.body() != null;
                            public_func.add_message_list(context, public_func.get_message_id(response.body().string()), msg_address, bundle.getInt("slot", -1));
                        }
                    }
                });
            }
        }
    }
}

