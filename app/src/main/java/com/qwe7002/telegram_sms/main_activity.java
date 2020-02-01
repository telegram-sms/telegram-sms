package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class main_activity extends AppCompatActivity {
    private Context context = null;
    private static boolean set_permission_back = false;
    private final String TAG = "main_activity";
    private SharedPreferences sharedPreferences;
    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        Log.i(TAG, "Current API address: " + public_func.get_url("", ""));

        final EditText chat_id = findViewById(R.id.chat_id);
        final EditText bot_token = findViewById(R.id.bot_token);
        final EditText trusted_phone_number = findViewById(R.id.trusted_phone_number);
        final Switch chat_command = findViewById(R.id.chat_command);
        final Switch fallback_sms = findViewById(R.id.fallback_sms);
        final Switch battery_monitoring_switch = findViewById(R.id.battery_monitoring);
        final Switch charger_status = findViewById(R.id.charger_status);
        final Switch doh_switch = findViewById(R.id.doh_switch);
        final Switch verification_code = findViewById(R.id.verification_code_switch);
        final Switch privacy_mode_switch = findViewById(R.id.privacy_switch);
        final Button save_button = findViewById(R.id.save);
        final Button get_id = findViewById(R.id.get_id);
        final Switch display_dual_sim_display_name = findViewById(R.id.display_dual_sim);
        final Button notify_app_set = findViewById(R.id.notify_app_set);

        //load config
        Paper.init(context);
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);

        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            show_privacy_dialog();
        }


        String bot_token_save = sharedPreferences.getString("bot_token", "");
        String chat_id_save = sharedPreferences.getString("chat_id", "");

        if (public_func.parse_long(chat_id_save) < 0) {
            privacy_mode_switch.setVisibility(View.VISIBLE);
        } else {
            privacy_mode_switch.setVisibility(View.GONE);
        }

        if (sharedPreferences.getBoolean("initialized", false)) {
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            if (!sharedPreferences.getBoolean("conversion_data_structure", false)) {
                new Thread(this::convert_data).start();
            }
        }
        boolean display_dual_sim_display_name_config = sharedPreferences.getBoolean("display_dual_sim_display_name", false);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (public_func.get_active_card(context) < 2) {
                display_dual_sim_display_name.setEnabled(false);
                display_dual_sim_display_name_config = false;
            }
            display_dual_sim_display_name.setChecked(display_dual_sim_display_name_config);
        }

        bot_token.setText(bot_token_save);
        chat_id.setText(chat_id_save);
        trusted_phone_number.setText(sharedPreferences.getString("trusted_phone_number", ""));
        battery_monitoring_switch.setChecked(sharedPreferences.getBoolean("battery_monitoring_switch", false));
        charger_status.setChecked(sharedPreferences.getBoolean("charger_status", false));

        if (!battery_monitoring_switch.isChecked()) {
            charger_status.setChecked(false);
            charger_status.setVisibility(View.GONE);
        }

        battery_monitoring_switch.setOnClickListener(v -> {
            if (battery_monitoring_switch.isChecked()) {
                charger_status.setVisibility(View.VISIBLE);
            } else {
                charger_status.setVisibility(View.GONE);
                charger_status.setChecked(false);
            }
        });

        fallback_sms.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        if (trusted_phone_number.length() == 0) {
            fallback_sms.setVisibility(View.GONE);
            fallback_sms.setChecked(false);
        }
        trusted_phone_number.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (trusted_phone_number.length() != 0) {
                    fallback_sms.setVisibility(View.VISIBLE);
                } else {
                    fallback_sms.setVisibility(View.GONE);
                    fallback_sms.setChecked(false);
                }
            }
        });

        chat_command.setChecked(sharedPreferences.getBoolean("chat_command", false));
        chat_command.setOnClickListener(v -> set_privacy_mode_checkbox(chat_id, chat_command, privacy_mode_switch));
        verification_code.setChecked(sharedPreferences.getBoolean("verification_code", false));
        doh_switch.setChecked(sharedPreferences.getBoolean("doh_switch", true));
        privacy_mode_switch.setChecked(sharedPreferences.getBoolean("privacy_mode", false));

        int check_phone_state_permission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE);
        if (check_phone_state_permission == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                assert tm != null;
                if (tm.getPhoneCount() <= 1) {
                    display_dual_sim_display_name.setVisibility(View.GONE);
                }
            }
        }
        display_dual_sim_display_name.setOnClickListener(v -> {
            if (check_phone_state_permission != PackageManager.PERMISSION_GRANTED) {
                display_dual_sim_display_name.setChecked(false);
                ActivityCompat.requestPermissions(main_activity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            } else {
                if (public_func.get_active_card(context) < 2) {
                    display_dual_sim_display_name.setEnabled(false);
                    display_dual_sim_display_name.setChecked(false);
                }
            }
        });


        chat_id.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                set_privacy_mode_checkbox(chat_id, chat_command, privacy_mode_switch);

            }

            @Override
            public void afterTextChanged(Editable s) {
                //ignore
            }
        });


        get_id.setOnClickListener(v -> {
            if (bot_token.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show();
                return;
            }
            new Thread(() -> public_func.stop_all_service(context)).start();
            final ProgressDialog progress_dialog = new ProgressDialog(main_activity.this);
            progress_dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progress_dialog.setTitle(getString(R.string.get_recent_chat_title));
            progress_dialog.setMessage(getString(R.string.get_recent_chat_message));
            progress_dialog.setIndeterminate(false);
            progress_dialog.setCancelable(false);
            progress_dialog.show();
            String request_uri = public_func.get_url(bot_token.getText().toString().trim(), "getUpdates");
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch.isChecked());
            okhttp_client = okhttp_client.newBuilder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
            polling_json request_body = new polling_json();
            request_body.timeout = 60;
            RequestBody body = RequestBody.create(new Gson().toJson(request_body), public_func.JSON);
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            progress_dialog.setOnKeyListener((dialogInterface, i, keyEvent) -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    call.cancel();
                }
                return false;
            });
            final String error_head = "Get chat ID failed:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    progress_dialog.cancel();
                    String error_message = error_head + e.getMessage();
                    public_func.write_log(context, error_message);
                    Looper.prepare();
                    Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    progress_dialog.cancel();
                    assert response.body() != null;
                    if (response.code() != 200) {
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                        String error_message = error_head + result_obj.get("description").getAsString();
                        public_func.write_log(context, error_message);

                        Looper.prepare();
                        Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    String result = Objects.requireNonNull(response.body()).string();
                    JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                    JsonArray chat_list = result_obj.getAsJsonArray("result");
                    if (chat_list.size() == 0) {
                        Looper.prepare();
                        Snackbar.make(v, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    final ArrayList<String> chat_name_list = new ArrayList<>();
                    final ArrayList<String> chat_id_list = new ArrayList<>();
                    for (JsonElement item : chat_list) {
                        JsonObject item_obj = item.getAsJsonObject();
                        if (item_obj.has("message")) {
                            JsonObject message_obj = item_obj.get("message").getAsJsonObject();
                            JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                            if (!chat_id_list.contains(chat_obj.get("id").getAsString())) {
                                String username = "";
                                if (chat_obj.has("username")) {
                                    username = chat_obj.get("username").getAsString();
                                }
                                if (chat_obj.has("title")) {
                                    username = chat_obj.get("title").getAsString();
                                }
                                if (username.equals("") && !chat_obj.has("username")) {
                                    if (chat_obj.has("first_name")) {
                                        username = chat_obj.get("first_name").getAsString();
                                    }
                                    if (chat_obj.has("last_name")) {
                                        username += " " + chat_obj.get("last_name").getAsString();
                                    }
                                }
                                chat_name_list.add(username + "(" + chat_obj.get("type").getAsString() + ")");
                                chat_id_list.add(chat_obj.get("id").getAsString());
                            }
                        }
                        if (item_obj.has("channel_post")) {
                            JsonObject message_obj = item_obj.get("channel_post").getAsJsonObject();
                            JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                            if (!chat_id_list.contains(chat_obj.get("id").getAsString())) {
                                chat_name_list.add(chat_obj.get("title").getAsString() + "(Channel)");
                                chat_id_list.add(chat_obj.get("id").getAsString());
                            }
                        }
                    }
                    main_activity.this.runOnUiThread(() -> new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chat_name_list.toArray(new String[0]), (dialogInterface, i) -> chat_id.setText(chat_id_list.get(i))).setPositiveButton("Cancel", null).show());
                }
            });
        });

        save_button.setOnClickListener(v -> {
            if (bot_token.getText().toString().isEmpty() || chat_id.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fallback_sms.isChecked() && trusted_phone_number.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
                show_privacy_dialog();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(main_activity.this, new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG}, 1);

                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                assert powerManager != null;
                boolean has_ignored = powerManager.isIgnoringBatteryOptimizations(getPackageName());
                if (!has_ignored) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    if (intent.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        startActivity(intent);
                    }
                }
            }

            final ProgressDialog progress_dialog = new ProgressDialog(main_activity.this);
            progress_dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progress_dialog.setTitle(getString(R.string.connect_wait_title));
            progress_dialog.setMessage(getString(R.string.connect_wait_message));
            progress_dialog.setIndeterminate(false);
            progress_dialog.setCancelable(false);
            progress_dialog.show();

            String request_uri = public_func.get_url(bot_token.getText().toString().trim(), "sendMessage");
            message_json request_body = new message_json();
            request_body.chat_id = chat_id.getText().toString().trim();
            request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
            Gson gson = new Gson();
            String request_body_raw = gson.toJson(request_body);
            RequestBody body = RequestBody.create(request_body_raw, public_func.JSON);
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch.isChecked());
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            final String error_head = "Send message failed:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    progress_dialog.cancel();
                    String error_message = error_head + e.getMessage();
                    public_func.write_log(context, error_message);
                    Looper.prepare();
                    Snackbar.make(v, error_message, Snackbar.LENGTH_LONG)
                            .show();
                    Looper.loop();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    progress_dialog.cancel();
                    String new_bot_token = bot_token.getText().toString().trim();
                    if (response.code() != 200) {
                        assert response.body() != null;
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                        String error_message = error_head + result_obj.get("description");
                        public_func.write_log(context, error_message);
                        Looper.prepare();
                        Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    if (!new_bot_token.equals(bot_token_save)) {
                        Log.i(TAG, "onResponse: The current bot token does not match the saved bot token, clearing the message database.");
                        List<String> notify_listen_list = Paper.book().read("notify_listen_list", new ArrayList<>());
                        Paper.book().destroy();
                        Paper.book().write("notify_listen_list", notify_listen_list);
                    }
                    SharedPreferences.Editor editor = sharedPreferences.edit().clear();
                    editor.putString("bot_token", new_bot_token);
                    editor.putString("chat_id", chat_id.getText().toString().trim());
                    if (trusted_phone_number.getText().toString().trim().length() != 0) {
                        editor.putString("trusted_phone_number", trusted_phone_number.getText().toString().trim());
                        editor.putBoolean("fallback_sms", fallback_sms.isChecked());
                    }
                    editor.putBoolean("fallback_sms", fallback_sms.isChecked());
                    editor.putBoolean("chat_command", chat_command.isChecked());
                    editor.putBoolean("battery_monitoring_switch", battery_monitoring_switch.isChecked());
                    editor.putBoolean("charger_status", charger_status.isChecked());
                    editor.putBoolean("display_dual_sim_display_name", display_dual_sim_display_name.isChecked());
                    editor.putBoolean("verification_code", verification_code.isChecked());
                    editor.putBoolean("doh_switch", doh_switch.isChecked());
                    editor.putBoolean("privacy_mode", privacy_mode_switch.isChecked());
                    editor.putBoolean("initialized", true);
                    editor.putBoolean("conversion_data_structure", true);
                    editor.putBoolean("privacy_dialog_agree", true);
                    editor.apply();
                    new Thread(() -> {
                        public_func.stop_all_service(context);
                        public_func.start_service(context, battery_monitoring_switch.isChecked(), chat_command.isChecked());
                    }).start();
                    Looper.prepare();
                    Snackbar.make(v, R.string.success, Snackbar.LENGTH_LONG)
                            .show();
                    Looper.loop();
                }
            });
        });

        notify_app_set.setOnClickListener(v -> {
            if (!public_func.is_notify_listener(context)) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                set_permission_back = true;
                return;
            }
            startActivity(new Intent(main_activity.this, notify_apps_list_activity.class));
        });

    }

    private void set_privacy_mode_checkbox(TextView chat_id, Switch chat_command, Switch privacy_mode_switch) {
        if (!chat_command.isChecked()) {
            privacy_mode_switch.setVisibility(View.GONE);
            privacy_mode_switch.setChecked(false);
            return;
        }
        if (public_func.parse_long(chat_id.getText().toString()) < 0) {
            privacy_mode_switch.setVisibility(View.VISIBLE);
        } else {
            privacy_mode_switch.setVisibility(View.GONE);
            privacy_mode_switch.setChecked(false);
        }

    }
    private void show_privacy_dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.privacy_reminder_title);
        builder.setMessage(R.string.privacy_reminder_information);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.agree, (dialog, which) -> sharedPreferences.edit().putBoolean("privacy_dialog_agree", true).apply());
        builder.setNegativeButton(R.string.decline, null);
        builder.setNeutralButton(R.string.visit_page, (dialog, which) -> {
            Uri uri = Uri.parse("https://get.telegram-sms.com/wiki/" + context.getString(R.string.privacy_policy_url));
            CustomTabsIntent.Builder privacy_builder = new CustomTabsIntent.Builder();
            privacy_builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary));
            CustomTabsIntent customTabsIntent = privacy_builder.build();
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                customTabsIntent.launchUrl(context, uri);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Snackbar.make(findViewById(R.id.bot_token), "Browser not found.", Snackbar.LENGTH_LONG).show();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setAllCaps(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean back_status = set_permission_back;
        set_permission_back = false;
        if (back_status) {
            if (public_func.is_notify_listener(context)) {
                startActivity(new Intent(main_activity.this, notify_apps_list_activity.class));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.");
                    Snackbar.make(findViewById(R.id.bot_token), R.string.no_camera_permission, Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(context, scanner_activity.class);
                startActivityForResult(intent, 1);
                break;
            case 1:
                Switch display_dual_sim_display_name = findViewById(R.id.display_dual_sim);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        assert tm != null;
                        if (tm.getPhoneCount() >= 1) {
                            display_dual_sim_display_name.setVisibility(View.GONE);
                        }
                        if (public_func.get_active_card(context) < 2) {
                            display_dual_sim_display_name.setEnabled(false);
                            display_dual_sim_display_name.setChecked(false);
                        }
                    }
                }
                break;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        String file_name = null;
        switch (item.getItemId()) {
            case R.id.user_manual:
                file_name = "/wiki/" + context.getString(R.string.user_manual_url);
                break;
            case R.id.privacy_policy:
                file_name = "/wiki/" + context.getString(R.string.privacy_policy_url);
                break;
            case R.id.donate:
                file_name = "/donate";
                break;
            case R.id.scan:
                ActivityCompat.requestPermissions(main_activity.this, new String[]{Manifest.permission.CAMERA}, 0);
                return true;
            case R.id.logcat:
                Intent logcat_intent = new Intent(this, logcat_activity.class);
                startActivity(logcat_intent);
                return true;
        }
        assert file_name != null;
        Uri uri = Uri.parse("https://get-telegram-sms.reall.uk" + file_name);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            customTabsIntent.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Snackbar.make(findViewById(R.id.bot_token), "Browser not found.", Snackbar.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                ((EditText) findViewById(R.id.bot_token)).setText(data.getStringExtra("bot_token"));
            }
        }
    }

    private void convert_data() {
        String message_list_raw = null;
        FileInputStream file_stream = null;
        try {
            file_stream = context.openFileInput("message.json");
            int length = file_stream.available();
            byte[] buffer = new byte[length];
            //noinspection ResultOfMethodCallIgnored
            file_stream.read(buffer);
            message_list_raw = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (message_list_raw != null) {
            JsonObject message_list = JsonParser.parseString(message_list_raw).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry_set : message_list.entrySet()) {
                JsonObject json_item = entry_set.getValue().getAsJsonObject();
                message_item item = new message_item();
                item.phone = json_item.get("phone").getAsString();
                item.card = json_item.get("card").getAsInt();
                item.sub_id = json_item.get("sub_id").getAsInt();
                Paper.book().write(entry_set.getKey(), item);
                Log.d(TAG, "add_message_list: " + entry_set.getKey());
            }
            Log.d(TAG, "The conversion is complete.");
            public_func.write_file(context, "message.json", "", Context.MODE_PRIVATE);
        }
        sharedPreferences.edit().putBoolean("conversion_data_structure", true).apply();
    }
}

