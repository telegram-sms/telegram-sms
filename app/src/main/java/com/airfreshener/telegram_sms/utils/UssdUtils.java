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

import com.google.gson.Gson;
import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.config.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.ussd_request_callback;
import com.airfreshener.telegram_sms.value.const_value;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UssdUtils {
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void send_ussd(Context context, String ussd_raw, int sub_id) {
        final String TAG = "send_ussd";
        final String ussd = OtherUrils.get_nine_key_map_convert(ussd_raw);

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert tm != null;

        if (sub_id != -1) {
            tm = tm.createForSubscriptionId(sub_id);
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "send_ussd: No permission.");
        }

        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = NetworkUtils.get_url(bot_token, "sendMessage");
        RequestMessage request_body = new RequestMessage();
        request_body.chat_id = chat_id;
        request_body.text = context.getString(R.string.send_ussd_head) + "\n" + context.getString(R.string.ussd_code_running);
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
        OkHttpClient okhttp_client = NetworkUtils.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new ProxyConfigV2()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        TelephonyManager final_tm = tm;
        new Thread(() -> {
            long message_id = -1L;
            try {
                Response response = call.execute();
                message_id = OtherUrils.get_message_id(Objects.requireNonNull(response.body()).string());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Looper.prepare();
                final_tm.sendUssdRequest(ussd, new ussd_request_callback(context, sharedPreferences, message_id), new Handler());
                Looper.loop();
            }
        }).start();
    }
}
