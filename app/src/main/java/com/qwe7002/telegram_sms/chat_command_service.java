package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.polling_json;
import com.qwe7002.telegram_sms.data_structure.reply_markup_keyboard;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.data_structure.smsRequestInfo;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.static_class.service;
import com.qwe7002.telegram_sms.static_class.sms;
import com.qwe7002.telegram_sms.static_class.ussd;
import com.qwe7002.telegram_sms.value.const_value;
import com.qwe7002.telegram_sms.value.notify_id;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class chat_command_service extends Service {
    private static long offset = 0;
    private static SharedPreferences sharedPreferences;
    private static int sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS;
    private static Thread threadMain;
    private static boolean firstRequest = true;

    private static class CALLBACK_DATA_VALUE {
        final static String SEND = "send";
        final static String CANCEL = "cancel";
    }
    private static class SEND_SMS_STATUS {
        static final int STANDBY_STATUS = -1;
        static final int PHONE_INPUT_STATUS = 0;
        static final int MESSAGE_INPUT_STATUS = 1;
        static final int WAITING_TO_SEND_STATUS = 2;
        static final int SEND_STATUS = 3;
    }

    private String chatId;
    private String botToken;
    private String messageThreadId;
    private Context context;
    private OkHttpClient okHttpClient;
    private stopReceive stopReceive;
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
        final request_message requestBody = new request_message();
        requestBody.chat_id = chatId;
        requestBody.message_thread_id = messageThreadId;
        JsonObject jsonObject = null;

        if (resultObj.has("message")) {
            jsonObject = resultObj.get("message").getAsJsonObject();
            messageType = jsonObject.get("chat").getAsJsonObject().get("type").getAsString();
        }
        if (resultObj.has("channel_post")) {
            messageType = "channel";
            jsonObject = resultObj.get("channel_post").getAsJsonObject();
        }
        String callbackData = null;
        if (resultObj.has("callback_query")) {
            messageType = "callback_query";
            JsonObject callback_query = resultObj.get("callback_query").getAsJsonObject();
            callbackData = callback_query.get("data").getAsString();
        }
        if (messageType.equals("callback_query") && sendSmsNextStatus != SEND_SMS_STATUS.STANDBY_STATUS) {
            //noinspection ConstantConditions
            int slot = Paper.book("send_temp").read("slot", -1);
            //noinspection ConstantConditions
            long messageId = Paper.book("send_temp").read("message_id", -1L);
            String to = Paper.book("send_temp").read("to", "");
            String content = Paper.book("send_temp").read("content", "");
            assert callbackData != null;
            if (!callbackData.equals(CALLBACK_DATA_VALUE.SEND)) {
                setSmsSendStatusStandby();
                String requestUri = network.getUrl(botToken, "editMessageText");
                String dualSim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                String sendContent = "[" + dualSim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + to + "\n" + context.getString(R.string.content) + content;
                requestBody.text = sendContent + "\n" + context.getString(R.string.status) + context.getString(R.string.cancel_button);
                requestBody.message_id = messageId;
                Gson gson = new Gson();
                String requestBodyRaw = gson.toJson(requestBody);
                RequestBody body = RequestBody.create(requestBodyRaw, const_value.JSON);
                OkHttpClient okhttpObj = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okhttpObj.newCall(request);
                try {
                    Response response = call.execute();
                    if (response.code() != 200 || response.body() == null) {
                        throw new IOException(String.valueOf(response.code()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    log.writeLog(context, "failed to send message:" + e.getMessage());
                }
                return;
            }
            int subId = -1;
            if (other.getActiveCard(context) == 1) {
                slot = -1;
            } else {
                subId = other.getSubId(context, slot);
            }
            sms.sendSms(context, to, content, slot, subId, messageId);
            setSmsSendStatusStandby();
            return;
        }
        if (jsonObject == null) {
            log.writeLog(context, "Request type is not allowed by security policy.");
            return;
        }
        JsonObject fromObj = null;
        final boolean isPrivate = messageType.equals("private");
        if (jsonObject.has("from")) {
            fromObj = jsonObject.get("from").getAsJsonObject();
            if (!isPrivate && fromObj.get("is_bot").getAsBoolean()) {
                Log.i(TAG, "receive_handle: receive from bot.");
                return;
            }
        }
        if (jsonObject.has("chat")) {
            fromObj = jsonObject.get("chat").getAsJsonObject();
        }

        assert fromObj != null;
        String from_id = fromObj.get("id").getAsString();
        String from_topic_id;
        if(jsonObject.has("is_topic_message")) {
            from_topic_id = jsonObject.get("message_thread_id").getAsString();
            if (!Objects.equals(messageThreadId, from_topic_id) ) {
                log.writeLog(context, "Topic ID[" + from_id + "] not allow.");
                return;
            }
        }
        if (!Objects.equals(chatId, from_id) ) {
            log.writeLog(context, "Chat ID[" + from_id + "] not allow.");
            return;
        }
        String command = "";
        String currentBotUsername = "";
        String requestMsg = "";
        if (jsonObject.has("text")) {
            requestMsg = jsonObject.get("text").getAsString();
        }
        if (jsonObject.has("reply_to_message")) {
            smsRequestInfo saveItem = Paper.book().read(jsonObject.get("reply_to_message").getAsJsonObject().get("message_id").getAsString(), null);
            if (saveItem != null && !requestMsg.isEmpty()) {
                String phoneNumber = saveItem.phone;
                int cardSlot = saveItem.card;
                sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                Paper.book("send_temp").write("slot", cardSlot);
                Paper.book("send_temp").write("to", phoneNumber);
                Paper.book("send_temp").write("content", requestMsg);
            }
        }
        if (jsonObject.has("entities")) {
            String tempCommand;
            String tempCommandLowercase;
            JsonArray entities = jsonObject.get("entities").getAsJsonArray();
            JsonObject entitiesObjCommand = entities.get(0).getAsJsonObject();
            if (entitiesObjCommand.get("type").getAsString().equals("bot_command")) {
                int commandOffset = entitiesObjCommand.get("offset").getAsInt();
                int commandEndOffset = commandOffset + entitiesObjCommand.get("length").getAsInt();
                tempCommand = requestMsg.substring(commandOffset, commandEndOffset).trim();
                tempCommandLowercase = tempCommand.toLowerCase().replace("_", "");
                command = tempCommandLowercase;
                if (tempCommandLowercase.contains("@")) {
                    int commandAtLocation = tempCommandLowercase.indexOf("@");
                    command = tempCommandLowercase.substring(0, commandAtLocation);
                    currentBotUsername = tempCommand.substring(commandAtLocation + 1);
                }

            }
        }
        if (!isPrivate && privacyMode && !currentBotUsername.equals(botUsername)) {
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
                if (other.getActiveCard(context) == 2) {
                    smsCommand = getString(R.string.sendsms_dual);
                }
                smsCommand += "\n" + getString(R.string.get_spam_sms);

                String ussdCommand = "";
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ussdCommand = "\n" + getString(R.string.send_ussd_command);
                        if (other.getActiveCard(context) == 2) {
                            ussdCommand = "\n" + getString(R.string.send_ussd_dual_command);
                        }
                    }
                }

                if (command.equals("/commandlist")) {
                    requestBody.text = (getString(R.string.available_command) + "\n" + smsCommand + ussdCommand).replace("/", "");
                    break;
                }

                String result = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + "\n" + smsCommand + ussdCommand;

                if (!isPrivate && privacyMode && !botUsername.equals("")) {
                    result = result.replace(" -", "@" + botUsername + " -");
                }
                requestBody.text = result;
                hasCommand = true;
                break;
            case "/ping":
            case "/getinfo":
                String cardInfo = "";
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    cardInfo = "\nSIM: " + other.getSimDisplayName(context, 0);
                    if (other.getActiveCard(context) == 2) {
                        cardInfo = "\nSIM1: " + other.getSimDisplayName(context, 0) + "\nSIM2: " + other.getSimDisplayName(context, 1);
                    }
                }
                String spamCount = "";
                ArrayList<String> spamSmsList = Paper.book().read("spam_sms_list", new ArrayList<>());
                assert spamSmsList != null;
                if (spamSmsList.size() != 0) {
                    spamCount = "\n" + getString(R.string.spam_count_title) + spamSmsList.size();
                }
                requestBody.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo() + "\n" + getString(R.string.current_network_connection_status) + getNetworkType() + spamCount + cardInfo;
                hasCommand = true;
                break;
            case "/log":
                String[] commands = requestMsg.split(" ");
                int line = 10;
                if (commands.length == 2 && isNumeric(commands[1])) {
                    assert commands[1] != null;
                    //noinspection ConstantConditions
                    int getLine = Integer.getInteger(commands[1]);
                    if (getLine > 50) {
                        getLine = 50;
                    }
                    line = getLine;
                }
                requestBody.text = getString(R.string.system_message_head) + log.readLog(context, line);
                hasCommand = true;
                break;
            case "/sendussd":
            case "/sendussd1":
            case "/sendussd2":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        String[] commandList = requestMsg.split(" ");
                        int sub_id = -1;
                        if (other.getActiveCard(context) == 2) {
                            if (command.equals("/sendussd2")) {
                                sub_id = other.getSubId(context, 1);
                            }
                        }
                        if (commandList.length == 2) {
                            ussd.sendUssd(context, commandList[1], sub_id);
                            return;
                        }
                    }
                }
                requestBody.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
            case "/getspamsms":
                ArrayList<String> spamSmsList1 = Paper.book().read("spam_sms_list", new ArrayList<>());
                assert spamSmsList1 != null;
                if (spamSmsList1.size() == 0) {
                    requestBody.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history);
                    break;
                }
                new Thread(() -> {
                    if (network.checkNetworkStatus(context)) {
                        OkHttpClient okhttpObj = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                        for (String item : spamSmsList1) {
                            request_message sendSmsRequestBody = new request_message();
                            sendSmsRequestBody.chat_id = chatId;
                            sendSmsRequestBody.text = item;
                            sendSmsRequestBody.message_thread_id = messageThreadId;
                            String requestUri = network.getUrl(botToken, "sendMessage");
                            String requestBodyJson = new Gson().toJson(sendSmsRequestBody);
                            RequestBody body = RequestBody.create(requestBodyJson, const_value.JSON);
                            Request requestObj = new Request.Builder().url(requestUri).method("POST", body).build();
                            Call call = okhttpObj.newCall(requestObj);
                            call.enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                    Log.d(TAG, "onFailure: " + e.getMessage());
                                    e.printStackTrace();
                                    log.writeLog(context, e.getMessage());
                                }

                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) {
                                    Log.d(TAG, "onResponse: " + response.code());
                                }
                            });
                            ArrayList<String> resendListLocal = Paper.book().read("spam_sms_list", new ArrayList<>());
                            assert resendListLocal != null;
                            resendListLocal.remove(item);
                            Paper.book().write("spam_sms_list", resendListLocal);
                        }
                    }
                    log.writeLog(context, "Send spam message is complete.");
                }).start();
                return;
            case "/sendsms":
            case "/sendsms1":
            case "/sendsms2":
                String[] msgSendList = requestMsg.split("\n");
                Log.i(TAG, "receiveHandle: "+msgSendList.length);
                if (msgSendList.length > 1) {
                    String[] infoList = msgSendList[0].split(" ");
                    if(infoList.length == 2) {
                        String msgSendTo = other.getSendPhoneNumber(infoList[1]);
                        if (other.isPhoneNumber(msgSendTo)) {
                            StringBuilder sendContent = new StringBuilder();
                            for (int i = 1; i < msgSendList.length; ++i) {
                                if (msgSendList.length != 2 && i != 1) {
                                    sendContent.append("\n");
                                }
                                sendContent.append(msgSendList[i]);
                            }
                            if (other.getActiveCard(context) == 1) {
                                sms.sendSms(context, msgSendTo, sendContent.toString(), -1, -1);
                                return;
                            }
                            int sendSlot = -1;
                            if (other.getActiveCard(context) > 1) {
                                sendSlot = 0;
                                if (command.equals("/sendsms2")) {
                                    sendSlot = 1;
                                }
                            }
                            int subId = other.getSubId(context, sendSlot);
                            if (subId != -1) {
                                sms.sendSms(context, msgSendTo, sendContent.toString(), sendSlot, subId);
                                return;
                            }
                        }
                    }
                }else if(messageType.equals("private")) {
                    Log.i(TAG, "receiveHandle: "+messageType);
                    sendSmsNextStatus = SEND_SMS_STATUS.PHONE_INPUT_STATUS;
                    int sendSlot = -1;
                    if (other.getActiveCard(context) > 1) {
                        sendSlot = 0;
                        if (command.equals("/sendsms2")) {
                            sendSlot = 1;
                        }
                    }
                    Paper.book("send_temp").write("slot", sendSlot);
                }


                requestBody.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.failed_to_get_information);
                break;
            default:
                if (!isPrivate && sendSmsNextStatus == -1) {
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
            //noinspection ConstantConditions
            int sendSlotTemp = Paper.book("send_temp").read("slot", -1);
            if (sendSlotTemp != -1) {
                dualSim = "SIM" + (sendSlotTemp + 1) + " ";
            }
            String head = "[" + dualSim + context.getString(R.string.send_sms_head) + "]";
            String resultSend = getString(R.string.failed_to_get_information);
            Log.d(TAG, "Sending mode status: " + sendSmsNextStatus);
            switch (sendSmsNextStatus) {
                case SEND_SMS_STATUS.PHONE_INPUT_STATUS:
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS;
                    resultSend = getString(R.string.enter_number);
                    break;
                case SEND_SMS_STATUS.MESSAGE_INPUT_STATUS:
                    String tempTo = other.getSendPhoneNumber(requestMsg);
                    if (other.isPhoneNumber(tempTo)) {
                        Paper.book("send_temp").write("to", tempTo);
                        resultSend = getString(R.string.enter_content);
                        sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                    } else {
                        setSmsSendStatusStandby();
                        resultSend = getString(R.string.unable_get_phone_number);
                    }
                    break;
                case SEND_SMS_STATUS.WAITING_TO_SEND_STATUS:
                    Paper.book("send_temp").write("content", requestMsg);
                    reply_markup_keyboard.keyboard_markup keyboardMarkup = new reply_markup_keyboard.keyboard_markup();
                    ArrayList<ArrayList<reply_markup_keyboard.InlineKeyboardButton>> inlineKeyboardButtons = new ArrayList<>();
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.send_button), CALLBACK_DATA_VALUE.SEND));
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.cancel_button), CALLBACK_DATA_VALUE.CANCEL));
                    keyboardMarkup.inline_keyboard = inlineKeyboardButtons;
                    requestBody.reply_markup = keyboardMarkup;
                    resultSend = context.getString(R.string.to) + Paper.book("send_temp").read("to") + "\n" + context.getString(R.string.content) + Paper.book("send_temp").read("content", "");
                    sendSmsNextStatus = SEND_SMS_STATUS.SEND_STATUS;
                    break;
            }
            requestBody.text = head + "\n" + resultSend;
        }

        String requestUri = network.getUrl(botToken, "sendMessage");
        RequestBody body = RequestBody.create(new Gson().toJson(requestBody), const_value.JSON);
        Request sendRequest = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okHttpClient.newCall(sendRequest);
        final String errorHead = "Send reply failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, errorHead + e.getMessage());
                resend.addResendLoop(context, requestBody.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String response_string = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    assert response.body() != null;
                    log.writeLog(context, errorHead + response.code() + " " + response_string);
                    resend.addResendLoop(context, requestBody.text);
                }
                if (sendSmsNextStatus == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", other.get_message_id(response_string));
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = other.getNotificationObj(getApplicationContext(), getString(R.string.chat_command_service_name));
        startForeground(notify_id.CHAT_COMMAND, notification);
        return START_STICKY;
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        setSmsSendStatusStandby();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chatId = sharedPreferences.getString("chat_id", "");
        botToken = sharedPreferences.getString("bot_token", "");
        messageThreadId = sharedPreferences.getString("message_thread_id","");
        okHttpClient = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        privacyMode = sharedPreferences.getBoolean("privacy_mode", false);
        wifiLock = ((WifiManager) Objects.requireNonNull(context.getApplicationContext().getSystemService(Context.WIFI_SERVICE))).createWifiLock(WifiManager.WIFI_MODE_FULL, "bot_command_polling_wifi");
        wakelock = ((PowerManager) Objects.requireNonNull(context.getSystemService(Context.POWER_SERVICE))).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling");
        wifiLock.setReferenceCounted(false);
        wakelock.setReferenceCounted(false);

        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
        if (!wakelock.isHeld()) {
            wakelock.acquire();
        }

        threadMain = new Thread(new threadMainRunnable());
        threadMain.start();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(const_value.BROADCAST_STOP_SERVICE);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        stopReceive = new stopReceive();
        registerReceiver(stopReceive, intentFilter);
    }

    private boolean getMe() {
        OkHttpClient okhttpClientNew = okHttpClient;
        String requestUri = network.getUrl(botToken, "getMe");
        Request request = new Request.Builder().url(requestUri).build();
        Call call = okhttpClientNew.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            log.writeLog(context, "Get username failed:" + e.getMessage());
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
            JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
            if (resultObj.get("ok").getAsBoolean()) {
                botUsername = resultObj.get("result").getAsJsonObject().get("username").getAsString();
                Paper.book().write("bot_username", botUsername);
                Log.d(TAG, "bot_username: " + botUsername);
                log.writeLog(context, "Get the bot username: " + botUsername);
            }
            return true;
        }
        return false;
    }

    private void setSmsSendStatusStandby() {
        Log.d(TAG, "set_sms_send_status_standby: ");
        sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS;
        Paper.book("send_temp").destroy();
    }

    @Override
    public void onDestroy() {
        wifiLock.release();
        wakelock.release();
        unregisterReceiver(stopReceive);
        stopForeground(true);
        super.onDestroy();
    }

    @SuppressWarnings("BusyWait")
    private class threadMainRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: thread main start");
            if (other.parseStringToLong(chatId) < 0) {
                botUsername = Paper.book().read("bot_username", null);
                if (botUsername == null) {
                    while (!getMe()) {
                        log.writeLog(context, "Failed to get bot Username, Wait 5 seconds and try again.");
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
                int timeout = 60;
                int http_timeout = 65;
                OkHttpClient okhttp_client_new = okHttpClient.newBuilder()
                        .readTimeout(http_timeout, TimeUnit.SECONDS)
                        .writeTimeout(http_timeout, TimeUnit.SECONDS)
                        .build();
                String requestUri = network.getUrl(botToken, "getUpdates");
                polling_json requestBody = new polling_json();
                requestBody.offset = offset;
                requestBody.timeout = timeout;
                if (firstRequest) {
                    requestBody.timeout = 0;
                    Log.d(TAG, "run: first_request");
                }
                RequestBody body = RequestBody.create(new Gson().toJson(requestBody), const_value.JSON);
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okhttp_client_new.newCall(request);
                Response response;
                try {
                    response = call.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!network.checkNetworkStatus(context)) {
                        log.writeLog(context, "No network connections available, Wait for the network to recover.");
                        Log.d(TAG, "run: break loop.");
                        break;
                    }
                    int sleep_time = 5;
                    log.writeLog(context, "Connection to the Telegram API service failed, try again after " + sleep_time + " seconds.");
                    try {
                        Thread.sleep(sleep_time * 1000L);
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
                    JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                    if (result_obj.get("ok").getAsBoolean()) {
                        JsonArray result_array = result_obj.get("result").getAsJsonArray();
                        for (JsonElement item : result_array) {
                            receiveHandle(item.getAsJsonObject(), firstRequest);
                        }
                        firstRequest = false;
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
                        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                        String result_message = getString(R.string.system_message_head) + "\n" + getString(R.string.error_stop_message) + "\n" + getString(R.string.error_message_head) + result_obj.get("description").getAsString() + "\n" + "Code: " + response.code();
                        sms.send_fallback_sms(context, result_message, -1);
                        service.stopAllService(context);
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
        int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (battery_level > 100) {
            Log.i(TAG, "The previous battery is over 100%, and the correction is 100%.");
            battery_level = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder battery_string_builder = new StringBuilder().append(battery_level).append("%");
        switch (charge_status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                battery_string_builder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        battery_string_builder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return battery_string_builder.toString();
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
            NetworkInfo activeNetworkInfo = connectManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null) {
                return netType;
            }
            switch (activeNetworkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    netType = "WIFI";
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    netType = checkCellularNetworkType(activeNetworkInfo.getSubtype());
                    break;
            }
        }

        return netType;
    }

    private String checkCellularNetworkType(int type) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "NR";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
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
                net_type = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                net_type = "2G";
                break;
        }
        return net_type;
    }

    private class stopReceive extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @NotNull Intent intent) {
            Log.d(TAG, "onReceive: " + intent.getAction());
            assert intent.getAction() != null;
            switch (intent.getAction()) {
                case const_value.BROADCAST_STOP_SERVICE:
                    Log.i(TAG, "Received stop signal, quitting now...");
                    stopSelf();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (network.checkNetworkStatus(context)) {
                        if (!threadMain.isAlive()) {
                            log.writeLog(context, "Network connections has been restored.");
                            threadMain = new Thread(new threadMainRunnable());
                            threadMain.start();
                        }
                    }
                    break;
            }
        }
    }

}

