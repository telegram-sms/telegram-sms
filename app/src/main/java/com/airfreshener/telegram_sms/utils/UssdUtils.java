package com.airfreshener.telegram_sms.utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.UssdRequestCallback;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UssdUtils {
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void sendUssd(Context context, String ussdRaw, int subId) {
        final String TAG = "send_ussd";
        final String ussd = OtherUtils.getNineKeyMapConvert(ussdRaw);

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert tm != null;

        if (subId != -1) {
            tm = tm.createForSubscriptionId(subId);
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "send_ussd: No permission.");
        }

        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");
        RequestMessage requestMessage = new RequestMessage();
        requestMessage.chat_id = chatId;
        requestMessage.text = context.getString(R.string.send_ussd_head) + "\n" + context.getString(R.string.ussd_code_running);
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestMessage);
        OkHttpClient okHttpClient = NetworkUtils.getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", true),
                PaperUtils.getProxyConfig()
        );
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(request);
        TelephonyManager finalTm = tm;
        new Thread(() -> {
            long messageId = -1L;
            try {
                Response response = call.execute();
                messageId = OtherUtils.getMessageId(Objects.requireNonNull(response.body()).string());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Looper.prepare();
                finalTm.sendUssdRequest(ussd, new UssdRequestCallback(context, sharedPreferences, messageId), new Handler());
                Looper.loop();
            }
        }).start();
    }
}
