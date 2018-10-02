package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    SharedPreferences sharedPreferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText chat_id = (EditText) findViewById(R.id.chat_id);
        final EditText bot_token = (EditText) findViewById(R.id.bot_token);
        Button save_button = (Button) findViewById(R.id.save);
        Button get_id = (Button) findViewById(R.id.get_id);

        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        bot_token.setText(sharedPreferences.getString("bot_token",""));
        chat_id.setText(sharedPreferences.getString("chat_id",""));

        get_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                String request_uri = "https://api.telegram.org/bot"+bot_token.getText().toString().trim()+"/getUpdates";
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url(request_uri).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.d("Failure", e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        JsonObject updates = new JsonParser().parse("").getAsJsonObject();
                        JsonArray chat_list = updates.getAsJsonArray("result");
                        ArrayList<String> chat_name_list = new ArrayList<>();
                        final ArrayList<String> chat_id_list = new ArrayList<>();
                        for (JsonElement item :chat_list) {
                            JsonObject item_obj = item.getAsJsonObject();
                            JsonObject message_obj = item_obj.get("message").getAsJsonObject();
                            JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                            chat_name_list.add(chat_obj.get("username").getAsString());
                            chat_id_list.add(chat_obj.get("id").getAsString());
                        }
                        new AlertDialog.Builder(v.getContext()).setTitle("Select Chat").setItems(chat_name_list.toArray(new String[0]), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                chat_id.setText(chat_id_list.get(i));

                            }
                        }).setPositiveButton("Cancel", null).show();
                    }
                });
            }
        });

        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS}, 1);
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("bot_token",bot_token.getText().toString().trim());
                editor.putString("chat_id", chat_id.getText().toString().trim());
                editor.apply();
                Snackbar.make(v, "Success",Snackbar.LENGTH_LONG)
                        .show();

            }
        });
    }

}

