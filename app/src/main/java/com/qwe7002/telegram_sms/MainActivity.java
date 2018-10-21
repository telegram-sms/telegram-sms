package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    SharedPreferences sharedPreferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent service = new Intent(getApplicationContext(), battery_listener_service.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.startForegroundService(service);
        } else {
            this.startService(service);
        }
        final EditText chat_id = findViewById(R.id.chat_id);
        final EditText bot_token = findViewById(R.id.bot_token);
        final EditText trusted_phone_number = findViewById(R.id.TPM);

        Button save_button = findViewById(R.id.save);
        Button get_id = findViewById(R.id.get_id);
        final Switch fallback_sms = findViewById(R.id.fallback_sms);
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        bot_token.setText(sharedPreferences.getString("bot_token", ""));
        chat_id.setText(sharedPreferences.getString("chat_id", ""));
        trusted_phone_number.setText(sharedPreferences.getString("trusted_phone_number", ""));
        fallback_sms.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        get_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (bot_token.getText().toString().isEmpty()) {
                    return;
                }
                final ProgressDialog mpDialog = new ProgressDialog(MainActivity.this);
                mpDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mpDialog.setTitle(getString(R.string.connect_wait_title));
                mpDialog.setMessage(getString(R.string.connect_wait_message));
                mpDialog.setIndeterminate(false);
                mpDialog.setCancelable(false);
                mpDialog.show();
                String request_uri = "https://api.telegram.org/bot" + bot_token.getText().toString().trim() + "/getUpdates";
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url(request_uri).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        mpDialog.cancel();
                        Log.d("Failure", e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Looper.prepare();
                        mpDialog.cancel();
                        Log.d(public_func.log_tag, "onResponse: " + response.body());
                        assert response.body() != null;
                        JsonObject updates = new JsonParser().parse(response.body().string()).getAsJsonObject();
                        JsonArray chat_list = updates.getAsJsonArray("result");
                        final ArrayList<String> chat_name_list = new ArrayList<>();
                        final ArrayList<String> chat_id_list = new ArrayList<>();
                        for (JsonElement item : chat_list) {
                            JsonObject item_obj = item.getAsJsonObject();
                            if (item_obj.has("message")) {
                                JsonObject message_obj = item_obj.get("message").getAsJsonObject();
                                JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                                if (!chat_id_list.contains(chat_obj.get("id").getAsString())) {
                                    chat_name_list.add(chat_obj.get("username").getAsString());
                                    chat_id_list.add(chat_obj.get("id").getAsString());
                                }
                            }
                        }
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chat_name_list.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        chat_id.setText(chat_id_list.get(i));

                                    }
                                }).setPositiveButton("Cancel", null).show();
                            }
                        });
                        Looper.loop();
                    }
                });
            }
        });

        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG}, 1);
                final ProgressDialog mpDialog = new ProgressDialog(MainActivity.this);
                mpDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mpDialog.setTitle(getString(R.string.connect_wait_title));
                mpDialog.setMessage(getString(R.string.connect_wait_message));
                mpDialog.setIndeterminate(false);
                mpDialog.setCancelable(false);
                mpDialog.show();
                String request_uri = "https://api.telegram.org/bot" + bot_token.getText().toString().trim() + "/sendMessage";
                request_json request_body = new request_json();
                request_body.chat_id = chat_id.getText().toString().trim();
                request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Looper.prepare();
                        mpDialog.cancel();
                        Snackbar.make(v, "Send Error:" + e.getMessage(), Snackbar.LENGTH_LONG)
                                .show();
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Looper.prepare();
                        mpDialog.cancel();
                        if (response.code() != 200) {
                            assert response.body() != null;
                            Snackbar.make(v, "Send Error:" + response.body().string(), Snackbar.LENGTH_LONG)
                                    .show();
                            return;
                        }

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("bot_token", bot_token.getText().toString().trim());
                        editor.putString("chat_id", chat_id.getText().toString().trim());
                        editor.putString("trusted_phone_number", trusted_phone_number.getText().toString().trim());
                        editor.putBoolean("fallback_sms", fallback_sms.isChecked());
                        editor.apply();
                        Snackbar.make(v, "Success", Snackbar.LENGTH_LONG)
                                .show();
                        Looper.loop();
                    }
                });
            }
        });
    }

}

