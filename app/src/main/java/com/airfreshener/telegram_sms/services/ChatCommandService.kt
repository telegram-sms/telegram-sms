package com.airfreshener.telegram_sms.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.utils.Consts;
import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.utils.PaperUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.airfreshener.telegram_sms.model.PollingJson;
import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.model.SmsRequestInfo;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.OtherUtils;
import com.airfreshener.telegram_sms.utils.ResendUtils;
import com.airfreshener.telegram_sms.utils.ServiceUtils;
import com.airfreshener.telegram_sms.utils.SmsUtils;
import com.airfreshener.telegram_sms.utils.UssdUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatCommandService extends Service {
    private static long offset = 0;
    private static int magnification = 1;
    private static int errorMagnification = 1;
    private static SharedPreferences sharedPreferences;
    private static int sendSmsNextStatus = Consts.SEND_SMS_STATUS.STANDBY_STATUS;
    private static Thread threadMain;
    private static boolean firstRequest = true;

    private static class CALLBACK_DATA_VALUE {
        final static String SEND = "send";
        final static String CANCEL = "cancel";
    }

    private String chatId;
    private String botToken;
    private Context context;
    private OkHttpClient okHttpClient;
    private BroadcastReceiver broadcastReceiver;
    private PowerManager.WakeLock wakelock;
    private WifiManager.WifiLock wifiLock;
    private String botUsername = "";
    private final String TAG = "chat_command_service";
    private boolean privacyMode;

    private static boolean isNumeric(String str) {
        for (int i = 0; i < str.length(); i++) {
            System.out.println(str.charAt(i));
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    private void receiveHandle(@NotNull JsonObject resultObj, boolean getIdOnly) {
        long updateId = resultObj.get("update_id").getAsLong();
        offset = updateId + 1;
        if (getIdOnly) {
            Log.d(TAG, "receive_handle: get_id_only");
            return;
        }
        String messageType = "";
        final RequestMessage requestBody = new RequestMessage();
        requestBody.chat_id = chatId;
        JsonObject messageObj = null;

        if (resultObj.has("message")) {
            messageObj = resultObj.get("message").getAsJsonObject();
            messageType = messageObj.get("chat").getAsJsonObject().get("type").getAsString();
        }
        if (resultObj.has("channel_post")) {
            messageType = "channel";
            messageObj = resultObj.get("channel_post").getAsJsonObject();
        }
        String callbackData = null;
        if (resultObj.has("callback_query")) {
            messageType = "callback_query";
            JsonObject callbackQuery = resultObj.get("callback_query").getAsJsonObject();
            callbackData = callbackQuery.get("data").getAsString();
        }
        if (messageType.equals("callback_query") && sendSmsNextStatus != Consts.SEND_SMS_STATUS.STANDBY_STATUS) {
            int slot = PaperUtils.getSendTempBook().read("slot", -1);
            long messageId = PaperUtils.getSendTempBook().read("message_id", -1L);
            String to = PaperUtils.getSendTempBook().read("to", "");
            String content = PaperUtils.getSendTempBook().read("content", "");
            assert callbackData != null;
            if (!callbackData.equals(CALLBACK_DATA_VALUE.SEND)) {
                setSmsSendStatusStandby();
                String requestUri = NetworkUtils.getUrl(botToken, "editMessageText");
                String dualSim = OtherUtils.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                String sendContent = "[" + dualSim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + to + "\n" + context.getString(R.string.content) + content;
                requestBody.text = sendContent + "\n" + context.getString(R.string.status) + context.getString(R.string.cancel_button);
                requestBody.message_id = messageId;
                RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
                OkHttpClient okhttpClient = NetworkUtils.getOkhttpObj(
                        sharedPreferences.getBoolean("doh_switch", true),
                        PaperUtils.getProxyConfig()
                );
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okhttpClient.newCall(request);
                try {
                    Response response = call.execute();
                    if (response.code() != 200 || response.body() == null) {
                        throw new IOException(String.valueOf(response.code()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    LogUtils.writeLog(context, "failed to send message:" + e.getMessage());
                }
                return;
            }
            int subId = -1;
            if (OtherUtils.getActiveCard(context) == 1) {
                slot = -1;
            } else {
                subId = OtherUtils.getSubId(context, slot);
            }
            SmsUtils.sendSms(context, to, content, slot, subId, messageId);
            setSmsSendStatusStandby();
            return;
        }
        if (messageObj == null) {
            LogUtils.writeLog(context, "Request type is not allowed by security policy.");
            return;
        }
        JsonObject fromObj = null;
        final boolean messageTypeIsPrivate = messageType.equals("private");
        if (messageObj.has("from")) {
            fromObj = messageObj.get("from").getAsJsonObject();
            if (!messageTypeIsPrivate && fromObj.get("is_bot").getAsBoolean()) {
                Log.i(TAG, "receive_handle: receive from bot.");
                return;
            }
        }
        if (messageObj.has("chat")) {
            fromObj = messageObj.get("chat").getAsJsonObject();
        }

        assert fromObj != null;
        String fromId = fromObj.get("id").getAsString();
        if (!Objects.equals(chatId, fromId)) {
            LogUtils.writeLog(context, "Chat ID[" + fromId + "] not allow.");
            return;
        }
        String command = "";
        String commandBotUsername = "";
        String requestMsg = "";
        if (messageObj.has("text")) {
            requestMsg = messageObj.get("text").getAsString();
        }
        if (messageObj.has("reply_to_message")) {
            SmsRequestInfo saveItem = PaperUtils.getDefaultBook().read(
                    messageObj.get("reply_to_message").getAsJsonObject().get("message_id").getAsString(),
                    null
            );
            if (saveItem != null && !requestMsg.isEmpty()) {
                String phoneNumber = saveItem.phone;
                int cardSlot = saveItem.card;
                sendSmsNextStatus = Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                PaperUtils.getSendTempBook()
                        .write("slot", cardSlot)
                        .write("to", phoneNumber)
                        .write("content", requestMsg);
            }
            if (!messageTypeIsPrivate) {
                Log.i(TAG, "receive_handle: The message id could not be found, ignored.");
                return;
            }
        }
        if (messageObj.has("entities")) {
            String tempCommand;
            String tempCommandLowercase;
            JsonArray entitiesArr = messageObj.get("entities").getAsJsonArray();
            JsonObject entitiesObjCommand = entitiesArr.get(0).getAsJsonObject();
            if (entitiesObjCommand.get("type").getAsString().equals("bot_command")) {
                int commandOffset = entitiesObjCommand.get("offset").getAsInt();
                int commandEndOffset = commandOffset + entitiesObjCommand.get("length").getAsInt();
                tempCommand = requestMsg.substring(commandOffset, commandEndOffset).trim();
                tempCommandLowercase = tempCommand.toLowerCase().replace("_", "");
                command = tempCommandLowercase;
                if (tempCommandLowercase.contains("@")) {
                    int commandAtLocation = tempCommandLowercase.indexOf("@");
                    command = tempCommandLowercase.substring(0, commandAtLocation);
                    commandBotUsername = tempCommand.substring(commandAtLocation + 1);
                }

            }
        }
        if (!messageTypeIsPrivate && privacyMode && !commandBotUsername.equals(botUsername)) {
            Log.i(TAG, "receive_handle: Privacy mode, no username found.");
            return;
        }
        Log.d(TAG, "receive_handle: " + command);
        boolean hasCommand = false;
        switch (command) {
            case "/help":
            case "/start":
            case "/commandlist":
                String smsCommand = getString(R.string.sendsms);
                if (OtherUtils.getActiveCard(context) == 2) {
                    smsCommand = getString(R.string.sendsms_dual);
                }
                smsCommand += "\n" + getString(R.string.get_spam_sms);

                String ussdCommand = "";
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ussdCommand = "\n" + getString(R.string.send_ussd_command);
                        if (OtherUtils.getActiveCard(context) == 2) {
                            ussdCommand = "\n" + getString(R.string.send_ussd_dual_command);
                        }
                    }
                }

                if (command.equals("/commandlist")) {
                    requestBody.text = (getString(R.string.available_command) + "\n" + smsCommand + ussdCommand).replace("/", "");
                    break;
                }

                String result = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + "\n" + smsCommand + ussdCommand;

                if (!messageTypeIsPrivate && privacyMode && !botUsername.equals("")) {
                    result = result.replace(" -", "@" + botUsername + " -");
                }
                requestBody.text = result;
                hasCommand = true;
                break;
            case "/ping":
            case "/getinfo":
                String cardInfo = "";
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    cardInfo = "\nSIM: " + OtherUtils.getSimDisplayName(context, 0);
                    if (OtherUtils.getActiveCard(context) == 2) {
                        cardInfo = "\nSIM1: " + OtherUtils.getSimDisplayName(context, 0) + "\nSIM2: " + OtherUtils.getSimDisplayName(context, 1);
                    }
                }
                String spamCount = "";
                ArrayList<String> spamList = PaperUtils.getDefaultBook().read("spam_sms_list", new ArrayList<>());
                if (spamList.size() != 0) {
                    spamCount = "\n" + getString(R.string.spam_count_title) + spamList.size();
                }
                requestBody.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo() + "\n" + getString(R.string.current_network_connection_status) + getNetworkType() + spamCount + cardInfo;
                hasCommand = true;
                break;
            case "/log":
                String[] cmdList = requestMsg.split(" ");
                int line = 10;
                if (cmdList.length == 2 && isNumeric(cmdList[1])) {
                    assert cmdList[1] != null;
                    //noinspection ConstantConditions
                    int lineCommand = Integer.getInteger(cmdList[1]);
                    if (lineCommand > 50) {
                        lineCommand = 50;
                    }
                    line = lineCommand;
                }
                requestBody.text = getString(R.string.system_message_head) + LogUtils.readLog(context, line);
                hasCommand = true;
                break;
            case "/sendussd":
            case "/sendussd1":
            case "/sendussd2":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        String[] commandList = requestMsg.split(" ");
                        int subId = -1;
                        if (OtherUtils.getActiveCard(context) == 2) {
                            if (command.equals("/sendussd2")) {
                                subId = OtherUtils.getSubId(context, 1);
                            }
                        }
                        if (commandList.length == 2) {
                            UssdUtils.sendUssd(context, commandList[1], subId);
                            return;
                        }
                    }
                }
                requestBody.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
            case "/getspamsms":
                ArrayList<String> spamSmsList = PaperUtils.getDefaultBook().read("spam_sms_list", new ArrayList<>());
                if (spamSmsList.size() == 0) {
                    requestBody.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history);
                    break;
                }
                new Thread(() -> {
                    if (NetworkUtils.checkNetworkStatus(context)) {
                        OkHttpClient okhttpClient = NetworkUtils.getOkhttpObj(
                                sharedPreferences.getBoolean("doh_switch", true),
                                PaperUtils.getProxyConfig()
                        );
                        for (String item : spamSmsList) {
                            RequestMessage sendSmsRequestBody = new RequestMessage();
                            sendSmsRequestBody.chat_id = chatId;
                            sendSmsRequestBody.text = item;
                            String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");
                            RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(sendSmsRequestBody);
                            Request requestObj = new Request.Builder().url(requestUri).method("POST", body).build();
                            Call call = okhttpClient.newCall(requestObj);
                            call.enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                    Log.d(TAG, "onFailure: " + e.getMessage());
                                    e.printStackTrace();
                                    LogUtils.writeLog(context, e.getMessage());
                                }

                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) {
                                    Log.d(TAG, "onResponse: " + response.code());
                                }
                            });
                            ArrayList<String> resendListLocal = PaperUtils.getDefaultBook().read("spam_sms_list", new ArrayList<>());
                            resendListLocal.remove(item);
                            PaperUtils.getDefaultBook().write("spam_sms_list", resendListLocal);
                        }
                    }
                    LogUtils.writeLog(context, "Send spam message is complete.");
                }).start();
                return;
            case "/sendsms":
            case "/sendsms1":
            case "/sendsms2":
                String[] msgSendList = requestMsg.split("\n");
                if (msgSendList.length > 2) {
                    String msgSendTo = OtherUtils.getSendPhoneNumber(msgSendList[1]);
                    if (OtherUtils.isPhoneNumber(msgSendTo)) {
                        StringBuilder msgSendContent = new StringBuilder();
                        for (int i = 2; i < msgSendList.length; ++i) {
                            if (msgSendList.length != 3 && i != 2) {
                                msgSendContent.append("\n");
                            }
                            msgSendContent.append(msgSendList[i]);
                        }
                        if (OtherUtils.getActiveCard(context) == 1) {
                            SmsUtils.sendSms(context, msgSendTo, msgSendContent.toString(), -1, -1);
                            return;
                        }
                        int sendSlot = -1;
                        if (OtherUtils.getActiveCard(context) > 1) {
                            sendSlot = 0;
                            if (command.equals("/sendsms2")) {
                                sendSlot = 1;
                            }
                        }
                        int subId = OtherUtils.getSubId(context, sendSlot);
                        if (subId != -1) {
                            SmsUtils.sendSms(context, msgSendTo, msgSendContent.toString(), sendSlot, subId);
                            return;
                        }
                    }
                } else {
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.PHONE_INPUT_STATUS;
                    int sendSlot = -1;
                    if (OtherUtils.getActiveCard(context) > 1) {
                        sendSlot = 0;
                        if (command.equals("/sendsms2")) {
                            sendSlot = 1;
                        }
                    }
                    PaperUtils.getSendTempBook().write("slot", sendSlot);
                }
                requestBody.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.failed_to_get_information);
                break;
            default:
                if (!messageTypeIsPrivate && sendSmsNextStatus == -1) {
                    Log.i(TAG, "receive_handle: The conversation is not Private and does not prompt an error.");
                    return;
                }
                requestBody.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
        }

        if (hasCommand) {
            setSmsSendStatusStandby();
        }
        if (!hasCommand && sendSmsNextStatus != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.");
            String dualSim = "";
            int sendSlotTemp = PaperUtils.getSendTempBook().read("slot", -1);
            if (sendSlotTemp != -1) {
                dualSim = "SIM" + (sendSlotTemp + 1) + " ";
            }
            String head = "[" + dualSim + context.getString(R.string.send_sms_head) + "]";
            String resultSend = getString(R.string.failed_to_get_information);
            Log.d(TAG, "Sending mode status: " + sendSmsNextStatus);
            switch (sendSmsNextStatus) {
                case Consts.SEND_SMS_STATUS.PHONE_INPUT_STATUS:
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.MESSAGE_INPUT_STATUS;
                    resultSend = getString(R.string.enter_number);
                    break;
                case Consts.SEND_SMS_STATUS.MESSAGE_INPUT_STATUS:
                    String tempTo = OtherUtils.getSendPhoneNumber(requestMsg);
                    if (OtherUtils.isPhoneNumber(tempTo)) {
                        PaperUtils.getSendTempBook().write("to", tempTo);
                        resultSend = getString(R.string.enter_content);
                        sendSmsNextStatus = Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                    } else {
                        setSmsSendStatusStandby();
                        resultSend = getString(R.string.unable_get_phone_number);
                    }
                    break;
                case Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS:
                    PaperUtils.getSendTempBook().write("content", requestMsg);
                    ReplyMarkupKeyboard.KeyboardMarkup keyboardMarkup = new ReplyMarkupKeyboard.KeyboardMarkup();
                    ArrayList<ArrayList<ReplyMarkupKeyboard.InlineKeyboardButton>> inlineKeyboardButtons = new ArrayList<>();
                    inlineKeyboardButtons.add(ReplyMarkupKeyboard.getInlineKeyboardObj(context.getString(R.string.send_button), CALLBACK_DATA_VALUE.SEND));
                    inlineKeyboardButtons.add(ReplyMarkupKeyboard.getInlineKeyboardObj(context.getString(R.string.cancel_button), CALLBACK_DATA_VALUE.CANCEL));
                    keyboardMarkup.inline_keyboard = inlineKeyboardButtons;
                    requestBody.reply_markup = keyboardMarkup;
                    resultSend = context.getString(R.string.to) + PaperUtils.getSendTempBook().read("to") + "\n"
                            + context.getString(R.string.content) + PaperUtils.getSendTempBook().read("content", "");
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.SEND_STATUS;
                    break;
            }
            requestBody.text = head + "\n" + resultSend;
        }

        String requestUri = NetworkUtils.getUrl(botToken, "sendMessage");
        RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
        Request sendRequest = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(sendRequest);
        final String errorHead = "Send reply failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                LogUtils.writeLog(context, errorHead + e.getMessage());
                ResendUtils.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseString = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    assert response.body() != null;
                    LogUtils.writeLog(context, errorHead + response.code() + " " + responseString);
                    ResendUtils.addResendLoop(context, requestBody.text);
                }
                if (sendSmsNextStatus == Consts.SEND_SMS_STATUS.SEND_STATUS) {
                    PaperUtils.getSendTempBook().write("message_id", OtherUtils.getMessageId(responseString));
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = OtherUtils.getNotificationObj(getApplicationContext(), getString(R.string.chat_command_service_name));
        startForeground(Consts.ServiceNotifyId.CHAT_COMMAND, notification);
        return START_STICKY;
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        PaperUtils.init(context);
        setSmsSendStatusStandby();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chatId = sharedPreferences.getString("chat_id", "");
        botToken = sharedPreferences.getString("bot_token", "");
        okHttpClient = NetworkUtils.getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", true),
                PaperUtils.getProxyConfig()
        );
        privacyMode = sharedPreferences.getBoolean("privacy_mode", false);
        WifiManager wifiManager = ((WifiManager) Objects.requireNonNull(context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)));
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "bot_command_polling_wifi");
        PowerManager powerManager = ((PowerManager) Objects.requireNonNull(context.getSystemService(Context.POWER_SERVICE)));
        wakelock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling");
        wifiLock.setReferenceCounted(false);
        wakelock.setReferenceCounted(false);

        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
        if (!wakelock.isHeld()) {
            wakelock.acquire();
        }

        threadMain = new Thread(new ThreadMainRunnable());
        threadMain.start();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Consts.BROADCAST_STOP_SERVICE);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcastReceiver = new BroadcastReceiver();
        registerReceiver(broadcastReceiver, intentFilter);
    }

    private boolean getMe() {
        OkHttpClient okhttp_client_new = okHttpClient;
        String request_uri = NetworkUtils.getUrl(botToken, "getMe");
        Request request = new Request.Builder().url(request_uri).build();
        Call call = okhttp_client_new.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.writeLog(context, "Get username failed:" + e.getMessage());
            return false;
        }
        if (response.code() == 200) {
            String result;
            try {
                result = Objects.requireNonNull(response.body()).string();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
            if (result_obj.get("ok").getAsBoolean()) {
                botUsername = result_obj.get("result").getAsJsonObject().get("username").getAsString();
                PaperUtils.getDefaultBook().write("bot_username", botUsername);
                Log.d(TAG, "bot_username: " + botUsername);
                LogUtils.writeLog(context, "Get the bot username: " + botUsername);
            }
            return true;
        }
        return false;
    }

    private void setSmsSendStatusStandby() {
        Log.d(TAG, "set_sms_send_status_standby: ");
        sendSmsNextStatus = Consts.SEND_SMS_STATUS.STANDBY_STATUS;
        PaperUtils.getSendTempBook().destroy();
    }

    @Override
    public void onDestroy() {
        wifiLock.release();
        wakelock.release();
        unregisterReceiver(broadcastReceiver);
        stopForeground(true);
        super.onDestroy();
    }

    @SuppressWarnings("BusyWait")
    private class ThreadMainRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: thread main start");
            if (OtherUtils.parseStringToLong(chatId) < 0) {
                botUsername = PaperUtils.getDefaultBook().read("bot_username", null);
                if (botUsername == null) {
                    while (!getMe()) {
                        LogUtils.writeLog(context, "Failed to get bot Username, Wait 5 seconds and try again.");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: " + botUsername);
            }
            while (true) {
                int timeout = 5 * magnification;
                int httpTimeout = timeout + 5;
                OkHttpClient okHttpClientNew = okHttpClient.newBuilder()
                        .readTimeout(httpTimeout, TimeUnit.SECONDS)
                        .writeTimeout(httpTimeout, TimeUnit.SECONDS)
                        .build();
                Log.d(TAG, "run: Current timeout: " + timeout + "S");
                String requestUri = NetworkUtils.getUrl(botToken, "getUpdates");
                PollingJson requestBody = new PollingJson();
                requestBody.offset = offset;
                requestBody.timeout = timeout;
                if (firstRequest) {
                    requestBody.timeout = 0;
                    Log.d(TAG, "run: first_request");
                }
                RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okHttpClientNew.newCall(request);
                Response response;
                try {
                    response = call.execute();
                    errorMagnification = 1;
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!NetworkUtils.checkNetworkStatus(context)) {
                        LogUtils.writeLog(context, "No network connections available, Wait for the network to recover.");
                        errorMagnification = 1;
                        magnification = 1;
                        Log.d(TAG, "run: break loop.");
                        break;
                    }
                    int sleepTime = 5 * errorMagnification;
                    LogUtils.writeLog(context, "Connection to the Telegram API service failed, try again after " + sleepTime + " seconds.");
                    magnification = 1;
                    if (errorMagnification <= 59) {
                        ++errorMagnification;
                    }
                    try {
                        Thread.sleep(sleepTime * 1000L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
                if (response.code() == 200) {
                    assert response.body() != null;
                    String result;
                    try {
                        result = Objects.requireNonNull(response.body()).string();
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }
                    JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                    if (resultObj.get("ok").getAsBoolean()) {
                        JsonArray resultArray = resultObj.get("result").getAsJsonArray();
                        for (JsonElement item : resultArray) {
                            receiveHandle(item.getAsJsonObject(), firstRequest);
                        }
                        firstRequest = false;
                    }
                    if (magnification <= 11) {
                        ++magnification;
                    }
                } else {
                    Log.d(TAG, "response code: " + response.code());
                    if (response.code() == 401) {
                        assert response.body() != null;
                        String result;
                        try {
                            result = Objects.requireNonNull(response.body()).string();
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                        JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                        String result_message = getString(R.string.system_message_head) + "\n"
                                + getString(R.string.error_stop_message) + "\n"
                                + getString(R.string.error_message_head) + resultObj.get("description").getAsString() + "\n"
                                + "Code: " + response.code();
                        SmsUtils.sendFallbackSms(context, result_message, -1);
                        ServiceUtils.stopAllService(context);
                        break;
                    }
                }
            }
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @NotNull
    private String getBatteryInfo() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        assert batteryManager != null;
        int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (batteryLevel > 100) {
            Log.i(TAG, "The previous battery is over 100%, and the correction is 100%.");
            batteryLevel = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder stringBuilder = new StringBuilder().append(batteryLevel).append("%");
        switch (chargeStatus) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                stringBuilder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        stringBuilder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return stringBuilder.toString();
    }

    private String getNetworkType() {
        String netType = "Unknown";
        ConnectivityManager connectManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connectManager != null;
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Network[] networks = connectManager.getAllNetworks();
            if (networks.length != 0) {
                for (Network network : networks) {
                    NetworkCapabilities networkCapabilities = connectManager.getNetworkCapabilities(network);
                    assert networkCapabilities != null;
                    if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            netType = "WIFI";
                        }
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                                continue;
                            }
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                                Log.d("get_network_type", "No permission.");
                            }
                            netType = checkCellularNetworkType(telephonyManager.getDataNetworkType());
                        }
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                            netType = "Bluetooth";
                        }
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            netType = "Ethernet";
                        }
                    }
                }
            }
        } else {
            NetworkInfo networkInfo = connectManager.getActiveNetworkInfo();
            if (networkInfo == null) {
                return netType;
            }
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    netType = "WIFI";
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    netType = checkCellularNetworkType(networkInfo.getSubtype());
                    break;
            }
        }

        return netType;
    }

    private String checkCellularNetworkType(int type) {
        String netType = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                netType = "NR";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                netType = "LTE";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
                netType = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                netType = "2G";
                break;
        }
        return netType;
    }

    private class BroadcastReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, @NotNull Intent intent) {
            Log.d(TAG, "onReceive: " + intent.getAction());
            assert intent.getAction() != null;
            switch (intent.getAction()) {
                case Consts.BROADCAST_STOP_SERVICE:
                    Log.i(TAG, "Received stop signal, quitting now...");
                    stopSelf();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (NetworkUtils.checkNetworkStatus(context)) {
                        if (!threadMain.isAlive()) {
                            LogUtils.writeLog(context, "Network connections has been restored.");
                            threadMain = new Thread(new ThreadMainRunnable());
                            threadMain.start();
                        }
                    }
                    break;
            }
        }
    }

}

