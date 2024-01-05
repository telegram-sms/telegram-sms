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

import com.google.gson.Gson;
import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.config.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.sms_send_receiver;
import com.airfreshener.telegram_sms.value.const_value;

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
    public static void send_sms(Context context, String send_to, String content, int slot, int sub_id) {
        send_sms(context, send_to, content, slot, sub_id, -1);
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public static void send_sms(Context context, String send_to, String content, int slot, int sub_id, long message_id) {
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d("send_sms", "No permission.");
            return;
        }
        if (!OtherUrils.is_phone_number(send_to)) {
            LogUtils.write_log(context, "[" + send_to + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = NetworkUtils.get_url(bot_token, "sendMessage");
        if (message_id != -1) {
            Log.d("send_sms", "Find the message_id and switch to edit mode.");
            request_uri = NetworkUtils.get_url(bot_token, "editMessageText");
        }
        RequestMessage request_body = new RequestMessage();
        request_body.chat_id = chat_id;
        SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dual_sim = OtherUrils.get_dual_sim_card_display(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
        String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + send_to + "\n" + context.getString(R.string.content) + content;
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        request_body.message_id = message_id;
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
        OkHttpClient okhttp_client = NetworkUtils.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new ProxyConfigV2()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200 || response.body() == null) {
                throw new IOException(String.valueOf(response.code()));
            }
            if (message_id == -1) {
                message_id = OtherUrils.get_message_id(Objects.requireNonNull(response.body()).string());
            }
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.write_log(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new sms_send_receiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("message_id", message_id);
        sent_intent.putExtra("message_text", send_content);
        sent_intent.putExtra("sub_id", sms_manager.getSubscriptionId());
        PendingIntent sentIntent;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_IMMUTABLE);
        }else{
            sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
    }

    public static void send_fallback_sms(Context context, String content, int sub_id) {
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
