package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.BroadcastReceiver;
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

import com.github.sumimakito.codeauxlib.CodeauxLibPortable;
import com.google.gson.Gson;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.static_class.service;
import com.qwe7002.telegram_sms.static_class.sms;
import com.qwe7002.telegram_sms.static_class.ussd;
import com.qwe7002.telegram_sms.value.const_value;

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


public class sms_receiver extends BroadcastReceiver {
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
        String message_thread_id = sharedPreferences.getString("message_thread_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");

        int intentSlot = extras.getInt("slot", -1);
        final int subId = extras.getInt("subscription", -1);
        if (other.getActiveCard(context) >= 2 && intentSlot == -1) {
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionInfo info = manager.getActiveSubscriptionInfo(subId);
                intentSlot = info.getSimSlotIndex();
            }
        }
        final int slot = intentSlot;
        String dualSim = other.getDualSimCardDisplay(context, intentSlot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));

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
            log.writeLog(context, "Message length is equal to 0.");
            return;
        }

        StringBuilder message_body_builder = new StringBuilder();
        for (SmsMessage item : messages) {
            message_body_builder.append(item.getMessageBody());
        }
        final String message_body = message_body_builder.toString();

        final String messageAddress = messages[0].getOriginatingAddress();
        assert messageAddress != null;

        String trusted_phone_number = sharedPreferences.getString("trusted_phone_number", null);
        boolean isTrustedPhone = false;
        if (trusted_phone_number != null && trusted_phone_number.length() != 0) {
            isTrustedPhone = messageAddress.contains(trusted_phone_number);
        }
        final request_message requestBody = new request_message();
        requestBody.chat_id = chat_id;
        requestBody.message_thread_id = message_thread_id;

        String message_body_html = message_body;
        final String message_head = "[" + dualSim + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + messageAddress + "\n" + context.getString(R.string.content);
        String rawRequestBodyText = message_head + message_body;
        boolean isVerificationCode = false;
        if (sharedPreferences.getBoolean("verification_code", false) && !isTrustedPhone) {
            if (message_body.length() <= 140) {
                String verification = code_aux_lib.find(message_body);
                if (verification != null) {
                    requestBody.parse_mode = "html";
                    message_body_html = message_body
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("&", "&amp;")
                            .replace(verification, "<code>" + verification + "</code>");
                    isVerificationCode = true;
                }
            } else {
                log.writeLog(context, "SMS exceeds 140 characters, no verification code is recognized.");
            }
        }
        requestBody.text = message_head + message_body_html;
        if (isTrustedPhone) {
            log.writeLog(context, "SMS from trusted mobile phone detected");
            String message_command = message_body.toLowerCase().replace("_", "").replace("-", "");
            String[] command_list = message_command.split("\n");
            if (command_list.length > 0) {
                String[] message_list = message_body.split("\n");
                switch (command_list[0].trim()) {
                    case "/restartservice":
                        new Thread(() -> {
                            service.stopAllService(context);
                            service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                        }).start();
                        rawRequestBodyText = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
                        requestBody.text = rawRequestBodyText;
                        break;
                    case "/sendsms":
                    case "/sendsms1":
                    case "/sendsms2":
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            Log.i(TAG, "No SMS permission.");
                            break;
                        }
                        String [] messageInfo =message_list[0].split(" ");
                        if(messageInfo.length==2) {
                            String msg_send_to = other.getSendPhoneNumber(messageInfo[1]);
                            if (other.isPhoneNumber(msg_send_to)) {
                                StringBuilder msg_send_content = new StringBuilder();
                                for (int i = 2; i < message_list.length; ++i) {
                                    if (i != 2) {
                                        msg_send_content.append("\n");
                                    }
                                    msg_send_content.append(message_list[i]);
                                }
                                int send_slot = slot;
                                if (other.getActiveCard(context) > 1) {
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
                                final int final_send_sub_id = other.getSubId(context, final_send_slot);
                                new Thread(() -> sms.sendSms(context, msg_send_to, msg_send_content.toString(), final_send_slot, final_send_sub_id)).start();
                                return;
                            }
                        }
                        break;
                    case "/sendussd":
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                if (message_list.length == 2) {
                                    ussd.sendUssd(context, message_list[1], subId);
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

        if (!isVerificationCode && !isTrustedPhone) {
            ArrayList<String> blackListArray = Paper.book("system_config").read("block_keyword_list", new ArrayList<>());
            assert blackListArray != null;
            for (String blackListItem : blackListArray) {
                if (blackListItem.isEmpty()) {
                    continue;
                }

                if (message_body.contains(blackListItem)) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
                    String writeMessage = requestBody.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    ArrayList<String> spamSmsList;
                    Paper.init(context);
                    spamSmsList = Paper.book().read("spam_sms_list", new ArrayList<>());
                    assert spamSmsList != null;
                    if (spamSmsList.size() >= 5) {
                        spamSmsList.remove(0);
                    }
                    spamSmsList.add(writeMessage);
                    Paper.book().write("spam_sms_list", spamSmsList);
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list");
                    return;
                }
            }
        }


        RequestBody body = RequestBody.create(new Gson().toJson(requestBody), const_value.JSON);
        OkHttpClient okhttpObj = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttpObj.newCall(request);
        final String errorHead = "Send SMS forward failed:";
        final String requestBodyText = rawRequestBodyText;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, errorHead + e.getMessage());
                sms.send_fallback_sms(context, requestBodyText, subId);
                resend.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log.writeLog(context, errorHead + response.code() + " " + result);
                    sms.send_fallback_sms(context, requestBodyText, subId);
                    resend.addResendLoop(context, requestBody.text);
                } else {
                    if (!other.isPhoneNumber(messageAddress)) {
                        log.writeLog(context, "[" + messageAddress + "] Not a regular phone number.");
                        return;
                    }
                    other.addMessageList(other.getMessageId(result), messageAddress, slot);
                }
            }
        });
    }
}


