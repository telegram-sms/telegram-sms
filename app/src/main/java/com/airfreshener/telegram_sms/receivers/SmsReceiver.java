package com.airfreshener.telegram_sms.receivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.github.sumimakito.codeauxlib.CodeauxLibPortable;
import com.airfreshener.telegram_sms.model.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.OtherUtils;
import com.airfreshener.telegram_sms.utils.ResendUtils;
import com.airfreshener.telegram_sms.utils.ServiceUtils;
import com.airfreshener.telegram_sms.utils.SmsUtils;
import com.airfreshener.telegram_sms.utils.UssdUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmsReceiver extends BroadcastReceiver {
    final static CodeauxLibPortable code_aux_lib = new CodeauxLibPortable();
    public void onReceive(final Context context, Intent intent) {
        Paper.init(context);
        final String TAG = "sms_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        final boolean is_default_sms_app = Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
        assert intent.getAction() != null;
        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED") && is_default_sms_app) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(TAG, "reject: android.provider.Telephony.SMS_RECEIVED.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = NetworkUtils.get_url(bot_token, "sendMessage");

        int intent_slot = extras.getInt("slot", -1);
        final int sub_id = extras.getInt("subscription", -1);
        if (OtherUtils.get_active_card(context) >= 2 && intent_slot == -1) {
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionInfo info = manager.getActiveSubscriptionInfo(sub_id);
                intent_slot = info.getSimSlotIndex();
            }
        }
        final int slot = intent_slot;
        String dual_sim = OtherUtils.get_dual_sim_card_display(context, intent_slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));

        Object[] pdus = (Object[]) extras.get("pdus");
        assert pdus != null;
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; ++i) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], extras.getString("format"));
            } else {
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            }
        }
        if (messages.length == 0) {
            LogUtils.write_log(context, "Message length is equal to 0.");
            return;
        }

        StringBuilder message_body_builder = new StringBuilder();
        for (SmsMessage item : messages) {
            message_body_builder.append(item.getMessageBody());
        }
        final String message_body = message_body_builder.toString();

        final String message_address = messages[0].getOriginatingAddress();
        assert message_address != null;

        if (is_default_sms_app) {
            Log.i(TAG, "onReceive: Write to the system database.");
            new Thread(() -> {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.ADDRESS, message_body);
                values.put(Telephony.Sms.BODY, message_address);
                values.put(Telephony.Sms.SUBSCRIPTION_ID, String.valueOf(sub_id));
                values.put(Telephony.Sms.READ, "1");
                context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, values);
            }).start();
        }

        String trusted_phone_number = sharedPreferences.getString("trusted_phone_number", null);
        boolean is_trusted_phone = false;
        if (trusted_phone_number != null && trusted_phone_number.length() != 0) {
            is_trusted_phone = message_address.contains(trusted_phone_number);
        }
        final RequestMessage request_body = new RequestMessage();
        request_body.chat_id = chat_id;

        String message_body_html = message_body;
        final String message_head = "[" + dual_sim + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + message_address + "\n" + context.getString(R.string.content);
        String raw_request_body_text = message_head + message_body;
        boolean is_verification_code = false;
        if (sharedPreferences.getBoolean("verification_code", false) && !is_trusted_phone) {
            if (message_body.length() <= 140) {
                String verification = code_aux_lib.find(message_body);
                if (verification != null) {
                    request_body.parse_mode = "html";
                    message_body_html = message_body
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("&", "&amp;")
                            .replace(verification, "<code>" + verification + "</code>");
                    is_verification_code = true;
                }
            } else {
                LogUtils.write_log(context, "SMS exceeds 140 characters, no verification code is recognized.");
            }
        }
        request_body.text = message_head + message_body_html;
        if (is_trusted_phone) {
            LogUtils.write_log(context, "SMS from trusted mobile phone detected");
            String message_command = message_body.toLowerCase().replace("_", "").replace("-", "");
            String[] command_list = message_command.split("\n");
            if (command_list.length > 0) {
                String[] message_list = message_body.split("\n");
                switch (command_list[0].trim()) {
                    case "/restartservice":
                        new Thread(() -> {
                            ServiceUtils.stop_all_service(context);
                            ServiceUtils.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                        }).start();
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/sendsms":
                    case "/sendsms1":
                    case "/sendsms2":
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            Log.i(TAG, "No SMS permission.");
                            break;
                        }
                        String msg_send_to = OtherUtils.get_send_phone_number(message_list[1]);
                        if (OtherUtils.is_phone_number(msg_send_to) && message_list.length > 2) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 2; i < message_list.length; ++i) {
                                if (i != 2) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(message_list[i]);
                            }
                            int send_slot = slot;
                            if (OtherUtils.get_active_card(context) > 1) {
                                switch (command_list[0].trim()) {
                                    case "/sendsms1":
                                        send_slot = 0;
                                        break;
                                    case "/sendsms2":
                                        send_slot = 1;
                                        break;
                                }
                            }
                            final int final_send_slot = send_slot;
                            final int final_send_sub_id = OtherUtils.get_sub_id(context, final_send_slot);
                            new Thread(() -> SmsUtils.send_sms(context, msg_send_to, msg_send_content.toString(), final_send_slot, final_send_sub_id)).start();
                            return;
                        }
                        break;
                    case "/sendussd":
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                if (message_list.length == 2) {
                                    UssdUtils.send_ussd(context, message_list[1], sub_id);
                                    return;
                                }
                            }
                        } else {
                            Log.i(TAG, "send_ussd: No permission.");
                            return;
                        }
                        break;
                }
            }
        }

        if (!is_verification_code && !is_trusted_phone) {
            ArrayList<String> black_list_array = Paper.book("system_config").read("block_keyword_list", new ArrayList<>());
            for (String black_list_item : black_list_array) {
                if (black_list_item.isEmpty()) {
                    continue;
                }

                if (message_body.contains(black_list_item)) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
                    String write_message = request_body.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    ArrayList<String> spam_sms_list;
                    Paper.init(context);
                    spam_sms_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                    if (spam_sms_list.size() >= 5) {
                        spam_sms_list.remove(0);
                    }
                    spam_sms_list.add(write_message);
                    Paper.book().write("spam_sms_list", spam_sms_list);
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list");
                    return;
                }
            }
        }


        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(request_body);
        OkHttpClient okhttp_client = NetworkUtils.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new ProxyConfigV2()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS forward failed:";
        final String final_raw_request_body_text = raw_request_body_text;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.write_log(context, error_head + e.getMessage());
                SmsUtils.send_fallback_sms(context, final_raw_request_body_text, sub_id);
                ResendUtils.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    LogUtils.write_log(context, error_head + response.code() + " " + result);
                    SmsUtils.send_fallback_sms(context, final_raw_request_body_text, sub_id);
                    ResendUtils.add_resend_loop(context, request_body.text);
                } else {
                    if (!OtherUtils.is_phone_number(message_address)) {
                        LogUtils.write_log(context, "[" + message_address + "] Not a regular phone number.");
                        return;
                    }
                    OtherUtils.add_message_list(OtherUtils.get_message_id(result), message_address, slot);
                }
            }
        });
    }
}


