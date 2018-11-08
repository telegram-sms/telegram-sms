package com.qwe7002.telegram_sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.qwe7002.telegram_sms.sms_receiver.is_numeric;

public class webhook_service extends Service {
    AsyncHttpServer server;

    public webhook_service() {

    }

    @Override
    public void onCreate() {
        final Context context = getApplicationContext();
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("2", public_func.log_tag,
                    NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(getApplicationContext(), "2").build();
            startForeground(2, notification);
        }

        server = new AsyncHttpServer();

        server.post("/webhook_" + sharedPreferences.getString("chat_id", ""), new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                final String chat_id = sharedPreferences.getString("chat_id", "");
                final String bot_token = sharedPreferences.getString("bot_token", "");
                if (bot_token.isEmpty() || chat_id.isEmpty()) {
                    Log.i(public_func.log_tag, "onReceive: token not found");
                    response.send("error");
                    return;
                }
                final request_json request_body = new request_json();
                request_body.chat_id = chat_id;
                JsonObject result_obj = new JsonParser().parse(request.getBody().get().toString()).getAsJsonObject();
                if (!result_obj.has("message") || !result_obj.has("entities")) {
                    response.send("error");
                    return;
                }

                JsonObject message_obj = result_obj.get("message").getAsJsonObject();
                JsonObject from_obj = message_obj.get("from").getAsJsonObject();

                String from_id = from_obj.get("id").getAsString();
                if (!Objects.equals(chat_id, from_id)) {
                    public_func.write_log(context, "Chat ID Error: Chat ID[" + from_id + "] not allow");
                    response.send("error");
                    return;
                }

                String command = "";
                String request_msg = message_obj.get("text").getAsString();
                if (result_obj.has("entities")) {
                    JsonArray entities_arr = result_obj.get("entities").getAsJsonArray();
                    JsonObject entities_obj_command = entities_arr.get(0).getAsJsonObject();
                    if (!entities_obj_command.get("type").getAsString().equals("bot_command")) {
                        int command_offset = entities_obj_command.get("offset").getAsInt();
                        int command_end_offset = command_offset + entities_obj_command.get("length").getAsInt();
                        command = request_msg.substring(command_offset, command_end_offset).trim();
                    }
                }

                Log.d(public_func.log_tag, "request command: " + command);
                switch (command) {
                    case "/ping":
                        request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
                        break;
                    case "/sendsms":
                        String[] msg_send_list = request_msg.replace("/sendsms\n", "").split("\n");
                        request_body.text = context.getString(R.string.send_sms_head) + "\n" + getString(R.string.command_format_error);
                        if (msg_send_list.length > 1 && is_numeric(msg_send_list[0])) {
                            String msg_send_to = msg_send_list[0].trim();
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 1; i < msg_send_list.length; i++) {
                                if (msg_send_list.length != 2 && i != 1) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(msg_send_list[i]);
                            }
                            public_func.send_sms(msg_send_to, msg_send_content.toString(), -1);
                            String display_to_address = msg_send_to;
                            String display_to_name = public_func.get_phone_name(context, display_to_address);
                            if (display_to_name != null) {
                                display_to_address = display_to_name + "(" + msg_send_to + ")";
                            }
                            request_body.text = context.getString(R.string.send_sms_head) + "\n" + context.getString(R.string.to) + display_to_address + "\n" + context.getString(R.string.content) + msg_send_content.toString();
                        }
                        break;
                    default:
                        request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.command_format_error);
                        return;
                }

                String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
                OkHttpClient okHttpClient = new OkHttpClient();
                Request send_request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okHttpClient.newCall(send_request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Looper.prepare();
                        String error_message = "Webhook Send SMS Error:" + e.getMessage();
                        public_func.write_log(context, error_message);
                        Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                        Log.i(public_func.log_tag, error_message);
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.code() != 200) {
                            Looper.prepare();
                            assert response.body() != null;
                            String error_message = "Webhook Send SMS Error:" + response.body().string();
                            public_func.write_log(context, error_message);
                            Toast.makeText(context, error_message, Toast.LENGTH_SHORT).show();
                            Log.i(public_func.log_tag, error_message);
                            Looper.loop();
                        }
                    }
                });
                response.send("Success");
            }
        });
        server.listen(5000);
    }

    @Override
    public void onDestroy() {
        server.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
