package com.airfreshener.telegram_sms.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.model.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.receivers.SmsSendReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmsUtils {
    public static void sendSms(Context context, String send_to, String content, int slot, int sub_id) {
        sendSms(context, send_to, content, slot, sub_id, -1);
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public static void sendSms(
            Context context,
            String sendTo,
            String content,
            int slot,
            int sub_id,
            long messageId
    ) {
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d("send_sms", "No permission.");
            return;
        }
        if (!OtherUtils.isPhoneNumber(sendTo)) {
            LogUtils.writeLog(context, "[" + sendTo + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");
        if (messageId != -1) {
            Log.d("send_sms", "Find the message_id and switch to edit mode.");
            requestUri = NetworkUtils.getUrl(botToken, "editMessageText");
        }
        RequestMessage requestBody = new RequestMessage();
        requestBody.chat_id = chatId;
        SmsManager smsManager;
        if (sub_id == -1) {
            smsManager = SmsManager.getDefault();
        } else {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dualSim = OtherUtils.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
        String sendContent = "[" + dualSim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + sendTo + "\n" + context.getString(R.string.content) + content;
        requestBody.text = sendContent + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        requestBody.message_id = messageId;
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
        OkHttpClient okHttpClient = NetworkUtils.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new ProxyConfigV2()));
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200 || response.body() == null) {
                throw new IOException(String.valueOf(response.code()));
            }
            if (messageId == -1) {
                messageId = OtherUtils.getMessageId(Objects.requireNonNull(response.body()).string());
            }
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.writeLog(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = smsManager.divideMessage(content);
        ArrayList<PendingIntent> sendReceiverList = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new SmsSendReceiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sentIntent = new Intent("send_sms");
        sentIntent.putExtra("message_id", messageId);
        sentIntent.putExtra("message_text", sendContent);
        sentIntent.putExtra("sub_id", smsManager.getSubscriptionId());
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE);
        }else{
            pendingIntent = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        sendReceiverList.add(pendingIntent);
        smsManager.sendMultipartTextMessage(sendTo, null, divideContents, sendReceiverList, null);
    }

    public static void sendFallbackSms(Context context, String content, int sub_id) {
        final String TAG = "send_fallback_sms";
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d(TAG, "No permission.");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", null);
        if (trustedPhoneNumber == null) {
            Log.i(TAG, "The trusted number is empty.");
            return;
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.i(TAG, "SMS fallback is not turned on.");
            return;
        }
        SmsManager smsManager;
        if (sub_id == -1) {
            smsManager = SmsManager.getDefault();
        } else {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        ArrayList<String> divideContents = smsManager.divideMessage(content);
        smsManager.sendMultipartTextMessage(trustedPhoneNumber, null, divideContents, null, null);
    }
}
