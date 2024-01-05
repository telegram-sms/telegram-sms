package com.airfreshener.telegram_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.PaperUtils;
import com.airfreshener.telegram_sms.utils.ResendUtils;
import com.airfreshener.telegram_sms.utils.SmsUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.O)
public class UssdRequestCallback extends TelephonyManager.UssdResponseCallback {
    private final Context context;
    private final boolean dohSwitch;
    private String requestUri;
    private final String messageHeader;
    private final RequestMessage requestBody;

    public UssdRequestCallback(Context context, @NotNull SharedPreferences sharedPreferences, long messageId) {
        this.context = context;
        PaperUtils.init(context);
        String chatId = sharedPreferences.getString("chat_id", "");
        this.dohSwitch = sharedPreferences.getBoolean("doh_switch", true);
        this.requestBody = new RequestMessage();
        this.requestBody.chat_id = chatId;
        String botToken = sharedPreferences.getString("bot_token", "");
        this.requestUri = NetworkUtils.getUrl(botToken, "SendMessage");
        if (messageId != -1) {
            this.requestUri = NetworkUtils.getUrl(botToken, "editMessageText");
            this.requestBody.message_id = messageId;
        }
        this.messageHeader = context.getString(R.string.send_ussd_head);
    }

    @Override
    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
        super.onReceiveUssdResponse(telephonyManager, request, response);
        String message = messageHeader + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.content) + response.toString();
        networkProgressHandle(message);
    }

    @Override
    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);
        String message = messageHeader + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.error_message) + getErrorCodeString(failureCode);
        networkProgressHandle(message);
    }

    private void networkProgressHandle(String message) {
        requestBody.text = message;
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
        OkHttpClient okHttpClient = NetworkUtils.getOkhttpObj(dohSwitch, PaperUtils.getProxyConfig());
        Request requestObj = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(requestObj);
        final String errorHead = "Send USSD failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.writeLog(context, errorHead + e.getMessage());
                SmsUtils.sendFallbackSms(context, requestBody.text, -1);
                ResendUtils.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    LogUtils.writeLog(context, errorHead + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    SmsUtils.sendFallbackSms(context, requestBody.text, -1);
                    ResendUtils.addResendLoop(context, requestBody.text);
                }
            }
        });
    }

    private String getErrorCodeString(int errorCode) {
        String result;
        switch (errorCode) {
            case -1:
                result = "Connection problem or invalid MMI code.";
                break;
            case -2:
                result = "No service.";
                break;
            default:
                result = "An unknown error occurred (" + errorCode + ")";
                break;
        }
        return result;
    }
}
