package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.telephony.SmsMessage;
import android.util.Log;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;

import static android.content.Context.MODE_PRIVATE;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class sms_receiver extends BroadcastReceiver {
    public void onReceive(final Context context, Intent intent) {
        Log.d(public_func.log_tag, "onReceive: " + intent.getAction());
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.d(public_func.log_tag, "reject: Error Extras");
            return;
        }

        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(public_func.log_tag, "Uninitialized, SMS receiver is deactivated");
            return;
        }
        final boolean is_default = Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction()) && is_default) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(public_func.log_tag, "reject: android.provider.Telephony.SMS_RECEIVED");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");

        final int slot = extras.getInt("slot", -1);
        String dual_sim = public_func.get_dual_sim_card_display(context, slot, sharedPreferences);

        final int sub = extras.getInt("subscription", -1);
        Object[] pdus = (Object[]) extras.get("pdus");
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
            if (!public_func.check_network(context)) {
                public_func.write_log(context, public_func.network_error);
                public_func.send_fallback_sms(context, request_body.text, sub);
                return;
            }
            String send_message_raw = request_body.text;
            String verification = public_func.get_verification_code(msgBody.toString());
            if (verification != null) {
                request_body.parse_mode = "html";
                request_body.text = request_body.text.replace(verification, "<code>" + verification + "</code>");
            }
            RequestBody body = RequestBody.create(public_func.JSON, new Gson().toJson(request_body));
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            final String error_head = "Send SMS forward failed:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    String error_message = error_head + e.getMessage();
                    public_func.write_log(context, error_message);
                    public_func.send_fallback_sms(context, send_message_raw, sub);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.code() != 200) {
                        assert response.body() != null;
                        String error_message = error_head + response.code() + " " + response.body().string();
                        public_func.write_log(context, error_message);
                        public_func.send_fallback_sms(context, send_message_raw, sub);
                    }
                    if (response.code() == 200) {
                        assert response.body() != null;
                        if (public_func.is_numeric(msg_address)) {
                            public_func.add_message_list(context, public_func.get_message_id(response.body().string()), msg_address, slot, sub);
                        }
                    }
                }
            });
        }

    }
}

