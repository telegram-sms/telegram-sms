package com.qwe7002.telegram_sms.static_class;

import static android.content.Context.RECEIVER_EXPORTED;

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

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.R;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.RequestMessage;
import com.qwe7002.telegram_sms.SMSSendResultReceiver;
import com.qwe7002.telegram_sms.value.constValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class sms {
    public static void sendSms(Context context, String send_to, String content, int slot, int sub_id) {
        send(context, send_to, content, slot, sub_id, -1);
    }

    @SuppressLint({"UnspecifiedImmutableFlag", "UnspecifiedRegisterReceiverFlag"})
    public static void send(Context context, String send_to, String content, int slot, int sub_id, long message_id) {
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d("send_sms", "No permission.");
            return;
        }
        if (!other.isPhoneNumber(send_to)) {
            log.writeLog(context, "[" + send_to + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");
        if (message_id != -1) {
            Log.d("send_sms", "Find the message_id and switch to edit mode.");
            request_uri = network.getUrl(bot_token, "editMessageText");
        }
        RequestMessage request_body = new RequestMessage();
        request_body.chatId = chat_id;
        SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dual_sim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
        String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + send_to + "\n" + context.getString(R.string.content) + content;
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        request_body.setMessageId(message_id);
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, constValue.JSON);
        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200) {
                throw new IOException(String.valueOf(response.code()));
            }
            if (message_id == -1) {
                message_id = other.getMessageId(Objects.requireNonNull(response.body()).string());
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.writeLog(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new SMSSendResultReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter,RECEIVER_EXPORTED);
        }else{
            context.registerReceiver(receiver, filter);
        }
        Log.d("onSend", "onReceive: "+message_id);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("message_id", message_id);
        sent_intent.putExtra("message_text", send_content);
        sent_intent.putExtra("sub_id", sms_manager.getSubscriptionId());
        PendingIntent sentIntent;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sentIntent = PendingIntent.getBroadcast(context, (int) message_id, sent_intent, PendingIntent.FLAG_IMMUTABLE);
        }else{
            sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
    }

    public static void fallbackSMS(Context context, String content, int sub_id) {
        final String TAG = "send_fallback_sms";
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d(TAG, "No permission.");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String trust_number = sharedPreferences.getString("trusted_phone_number", null);
        if (trust_number == null) {
            Log.i(TAG, "The trusted number is empty.");
            return;
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.i(TAG, "SMS fallback is not turned on.");
            return;
        }
        SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        sms_manager.sendMultipartTextMessage(trust_number, null, divideContents, null, null);

    }
}
