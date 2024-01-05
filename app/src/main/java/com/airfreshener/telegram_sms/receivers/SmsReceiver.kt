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
import androidx.core.content.ContextCompat;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.utils.PaperUtils;
import com.github.sumimakito.codeauxlib.CodeauxLibPortable;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmsReceiver extends BroadcastReceiver {
    private final static CodeauxLibPortable codeAuxLib = new CodeauxLibPortable();
    final static String TAG = "sms_receiver";

    public void onReceive(final Context context, Intent intent) {
        PaperUtils.init(context);
        Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        final boolean isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context).equals(context.getPackageName());
        assert intent.getAction() != null;
        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED") && isDefaultSmsApp) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(TAG, "reject: android.provider.Telephony.SMS_RECEIVED.");
            return;
        }
        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");

        int intentSlot = extras.getInt("slot", -1);
        final int subId = extras.getInt("subscription", -1);
        if (OtherUtils.getActiveCard(context) >= 2 && intentSlot == -1) {
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionInfo info = manager.getActiveSubscriptionInfo(subId);
                intentSlot = info.getSimSlotIndex();
            }
        }
        final int slot = intentSlot;
        String dualSim = OtherUtils.getDualSimCardDisplay(context, intentSlot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));

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
            LogUtils.writeLog(context, "Message length is equal to 0.");
            return;
        }

        StringBuilder messageBodyBuilder = new StringBuilder();
        for (SmsMessage item : messages) {
            messageBodyBuilder.append(item.getMessageBody());
        }
        final String messageBody = messageBodyBuilder.toString();

        final String messageAddress = messages[0].getOriginatingAddress();
        assert messageAddress != null;

        if (isDefaultSmsApp) {
            Log.i(TAG, "onReceive: Write to the system database.");
            new Thread(() -> {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.ADDRESS, messageBody);
                values.put(Telephony.Sms.BODY, messageAddress);
                values.put(Telephony.Sms.SUBSCRIPTION_ID, String.valueOf(subId));
                values.put(Telephony.Sms.READ, "1");
                context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, values);
            }).start();
        }

        String trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", null);
        boolean isTrustedPhone = false;
        if (trustedPhoneNumber != null && trustedPhoneNumber.length() != 0) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber);
        }
        final RequestMessage requestBody = new RequestMessage();
        requestBody.chat_id = chatId;

        String messageBodyHtml = messageBody;
        final String messageHead = "[" + dualSim + context.getString(R.string.receive_sms_head) + "]" + "\n" + context.getString(R.string.from) + messageAddress + "\n" + context.getString(R.string.content);
        String rawRequestBodyText = messageHead + messageBody;
        boolean isVerificationCode = false;
        if (sharedPreferences.getBoolean("verification_code", false) && !isTrustedPhone) {
            if (messageBody.length() <= 140) {
                String verification = codeAuxLib.find(messageBody);
                if (verification != null) {
                    requestBody.parse_mode = "html";
                    messageBodyHtml = messageBody
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("&", "&amp;")
                            .replace(verification, "<code>" + verification + "</code>");
                    isVerificationCode = true;
                }
            } else {
                LogUtils.writeLog(context, "SMS exceeds 140 characters, no verification code is recognized.");
            }
        }
        requestBody.text = messageHead + messageBodyHtml;
        if (isTrustedPhone) {
            LogUtils.writeLog(context, "SMS from trusted mobile phone detected");
            String messageCommand = messageBody.toLowerCase().replace("_", "").replace("-", "");
            String[] commandList = messageCommand.split("\n");
            if (commandList.length > 0) {
                String[] messageList = messageBody.split("\n");
                switch (commandList[0].trim()) {
                    case "/restartservice":
                        new Thread(() -> {
                            ServiceUtils.stopAllService(context);
                            ServiceUtils.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
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
                        String msgSendTo = OtherUtils.getSendPhoneNumber(messageList[1]);
                        if (OtherUtils.isPhoneNumber(msgSendTo) && messageList.length > 2) {
                            StringBuilder msgSendContent = new StringBuilder();
                            for (int i = 2; i < messageList.length; ++i) {
                                if (i != 2) {
                                    msgSendContent.append("\n");
                                }
                                msgSendContent.append(messageList[i]);
                            }
                            int sendSlot = slot;
                            if (OtherUtils.getActiveCard(context) > 1) {
                                switch (commandList[0].trim()) {
                                    case "/sendsms1":
                                        sendSlot = 0;
                                        break;
                                    case "/sendsms2":
                                        sendSlot = 1;
                                        break;
                                }
                            }
                            final int finalSendSlot = sendSlot;
                            final int finalSendSubId = OtherUtils.getSubId(context, finalSendSlot);
                            new Thread(() -> SmsUtils.sendSms(context, msgSendTo, msgSendContent.toString(), finalSendSlot, finalSendSubId)).start();
                            return;
                        }
                        break;
                    case "/sendussd":
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                if (messageList.length == 2) {
                                    UssdUtils.sendUssd(context, messageList[1], subId);
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
            ArrayList<String> blackListArray = PaperUtils.getSystemBook().read("block_keyword_list", new ArrayList<>());
            for (String blackListItem : blackListArray) {
                if (blackListItem.isEmpty()) {
                    continue;
                }

                if (messageBody.contains(blackListItem)) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
                    String writeMessage = requestBody.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    ArrayList<String> spamSmsList;
                    spamSmsList = PaperUtils.getDefaultBook().read("spam_sms_list", new ArrayList<>());
                    if (spamSmsList.size() >= 5) {
                        spamSmsList.remove(0);
                    }
                    spamSmsList.add(writeMessage);
                    PaperUtils.getDefaultBook().write("spam_sms_list", spamSmsList);
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list");
                    return;
                }
            }
        }


        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
        OkHttpClient okHttpClient = NetworkUtils.getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", true),
                PaperUtils.getProxyConfig()
        );
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(request);
        final String errorHead = "Send SMS forward failed:";
        final String finalRawRequestBodyText = rawRequestBodyText;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.writeLog(context, errorHead + e.getMessage());
                SmsUtils.sendFallbackSms(context, finalRawRequestBodyText, subId);
                ResendUtils.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    LogUtils.writeLog(context, errorHead + response.code() + " " + result);
                    SmsUtils.sendFallbackSms(context, finalRawRequestBodyText, subId);
                    ResendUtils.addResendLoop(context, requestBody.text);
                } else {
                    if (!OtherUtils.isPhoneNumber(messageAddress)) {
                        LogUtils.writeLog(context, "[" + messageAddress + "] Not a regular phone number.");
                        return;
                    }
                    OtherUtils.addMessageList(OtherUtils.getMessageId(result), messageAddress, slot);
                }
            }
        });
    }
}
