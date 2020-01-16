package com.qwe7002.telegram_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.O)
class ussd_request_callback extends TelephonyManager.UssdResponseCallback {

    private Context context;
    private boolean doh_switch;
    private String request_uri;
    private String message_header;
    private message_json request_body;

    ussd_request_callback(Context context, SharedPreferences sharedPreferences, long message_id) {
        this.context = context;
        String chat_id = sharedPreferences.getString("chat_id", "");
        this.doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        this.request_body = new message_json();
        this.request_body.chat_id = chat_id;
        String bot_token = sharedPreferences.getString("bot_token", "");
        this.request_uri = public_func.get_url(bot_token, "SendMessage");
        if (message_id != -1) {
            this.request_uri = public_func.get_url(bot_token, "editMessageText");
            this.request_body.message_id = message_id;
        }
        this.message_header = context.getString(R.string.received_ussd_title);
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
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch);
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        final String error_head = "Send USSD failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                public_func.write_log(context, error_head + e.getMessage());
                public_func.send_fallback_sms(context, request_body.text, -1);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    public_func.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    public_func.send_fallback_sms(context, request_body.text, -1);
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