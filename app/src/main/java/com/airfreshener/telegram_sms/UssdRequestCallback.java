package com.airfreshener.telegram_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.model.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.ResendUtils;
import com.airfreshener.telegram_sms.utils.SmsUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.O)
public
class UssdRequestCallback extends TelephonyManager.UssdResponseCallback {
    private final Context context;
    private final boolean doh_switch;
    private String request_uri;
    private final String message_header;
    private final RequestMessage request_body;

    public UssdRequestCallback(Context context, @NotNull SharedPreferences sharedPreferences, long message_id) {
        this.context = context;
        Paper.init(context);
        String chat_id = sharedPreferences.getString("chat_id", "");
        this.doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        this.request_body = new RequestMessage();
        this.request_body.chat_id = chat_id;
        String bot_token = sharedPreferences.getString("bot_token", "");
        this.request_uri = NetworkUtils.get_url(bot_token, "SendMessage");
        if (message_id != -1) {
            this.request_uri = NetworkUtils.get_url(bot_token, "editMessageText");
            this.request_body.message_id = message_id;
        }
        this.message_header = context.getString(R.string.send_ussd_head);
    }

    @Override
    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
        super.onReceiveUssdResponse(telephonyManager, request, response);
        String message = message_header + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.content) + response.toString();
        network_progress_handle(message);
    }

    @Override
    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);
        String message = message_header + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.error_message) + get_error_code_string(failureCode);
        network_progress_handle(message);
    }

    private void network_progress_handle(String message) {
        request_body.text = message;
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(request_body);
        OkHttpClient okhttp_client = NetworkUtils.get_okhttp_obj(doh_switch, Paper.book("system_config").read("proxy_config", new ProxyConfigV2()));
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        final String error_head = "Send USSD failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.write_log(context, error_head + e.getMessage());
                SmsUtils.send_fallback_sms(context, request_body.text, -1);
                ResendUtils.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    LogUtils.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    SmsUtils.send_fallback_sms(context, request_body.text, -1);
                    ResendUtils.add_resend_loop(context, request_body.text);
                }
            }
        });
    }

    private String get_error_code_string(int error_code) {
        String result;
        switch (error_code) {
            case -1:
                result = "Connection problem or invalid MMI code.";
                break;
            case -2:
                result = "No service.";
                break;
            default:
                result = "An unknown error occurred (" + error_code + ")";
                break;
        }
        return result;
    }
}
