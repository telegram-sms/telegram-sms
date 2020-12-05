package com.qwe7002.telegram_sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

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
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.polling_json;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.public_func;
import com.qwe7002.telegram_sms.static_class.public_value;

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

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class main_activity extends AppCompatActivity {
    private static boolean set_permission_back = false;
    private final String TAG = "main_activity";
    private SharedPreferences sharedPreferences;
    private String privacy_police;
    private Context context;

    private void check_version_upgrade(boolean reset_log) {
        int version_code = Paper.book("system_config").read("version_code", 0);
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        int current_version_code;
        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            current_version_code = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return;
        }
        if (version_code != current_version_code) {
            if (reset_log) {
                public_func.reset_log_file(context);
            }
            Paper.book("system_config").write("version_code", current_version_code);
        }
    }

    private void update_config() {
        int store_version = Paper.book("system_config").read("version", 0);
        if (store_version == public_value.SYSTEM_CONFIG_VERSION) {
            return;
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (store_version) {
            case 0:
                new com.qwe7002.telegram_sms.update_to_version1().update();
                break;
            default:
                Log.i(TAG, "update_config: Can't find a version that can be updated");
        }
    }
    @SuppressLint({"BatteryLife"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        //load config
        Paper.init(context);
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        privacy_police = "/guide/" + context.getString(R.string.Lang) + "/privacy-policy";

        final EditText chat_id_editview = findViewById(R.id.chat_id_editview);
        final EditText bot_token_editview = findViewById(R.id.bot_token_editview);
        final EditText trusted_phone_number_editview = findViewById(R.id.trusted_phone_number_editview);
        final Switch chat_command_switch = findViewById(R.id.chat_command_switch);
        final Switch fallback_sms_switch = findViewById(R.id.fallback_sms_switch);
        final Switch battery_monitoring_switch = findViewById(R.id.battery_monitoring_switch);
        final Switch charger_status_switch = findViewById(R.id.charger_status_switch);
        final Switch doh_switch = findViewById(R.id.doh_switch);
        final Switch verification_code_switch = findViewById(R.id.verification_code_switch);
        final Switch privacy_mode_switch = findViewById(R.id.privacy_switch);
        final Switch display_dual_sim_display_name_switch = findViewById(R.id.display_dual_sim_switch);
        final Button save_button = findViewById(R.id.save_button);
        final Button get_id_button = findViewById(R.id.get_id_button);




        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            show_privacy_dialog();
        }

        String bot_token_save = sharedPreferences.getString("bot_token", "");
        String chat_id_save = sharedPreferences.getString("chat_id", "");

        if (public_func.parse_string_to_long(chat_id_save) < 0) {
            privacy_mode_switch.setVisibility(View.VISIBLE);
        } else {
            privacy_mode_switch.setVisibility(View.GONE);
        }

        if (sharedPreferences.getBoolean("initialized", false)) {
            update_config();
            check_version_upgrade(true);
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));

        }
        boolean display_dual_sim_display_name_config = sharedPreferences.getBoolean("display_dual_sim_display_name", false);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (public_func.get_active_card(context) < 2) {
                display_dual_sim_display_name_switch.setEnabled(false);
                display_dual_sim_display_name_config = false;
            }
            display_dual_sim_display_name_switch.setChecked(display_dual_sim_display_name_config);
        }

        bot_token_editview.setText(bot_token_save);
        chat_id_editview.setText(chat_id_save);
        trusted_phone_number_editview.setText(sharedPreferences.getString("trusted_phone_number", ""));
        battery_monitoring_switch.setChecked(sharedPreferences.getBoolean("battery_monitoring_switch", false));
        charger_status_switch.setChecked(sharedPreferences.getBoolean("charger_status", false));

        if (!battery_monitoring_switch.isChecked()) {
            charger_status_switch.setChecked(false);
            charger_status_switch.setVisibility(View.GONE);
        }

        battery_monitoring_switch.setOnClickListener(v -> {
            if (battery_monitoring_switch.isChecked()) {
                charger_status_switch.setVisibility(View.VISIBLE);
            } else {
                charger_status_switch.setVisibility(View.GONE);
                charger_status_switch.setChecked(false);
            }
        });

        fallback_sms_switch.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        if (trusted_phone_number_editview.length() == 0) {
            fallback_sms_switch.setVisibility(View.GONE);
            fallback_sms_switch.setChecked(false);
        }
        trusted_phone_number_editview.addTextChangedListener(new TextWatcher() {
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
                if (trusted_phone_number_editview.length() != 0) {
                    fallback_sms_switch.setVisibility(View.VISIBLE);
                } else {
                    fallback_sms_switch.setVisibility(View.GONE);
                    fallback_sms_switch.setChecked(false);
                }
            }
        });

        chat_command_switch.setChecked(sharedPreferences.getBoolean("chat_command", false));
        chat_command_switch.setOnClickListener(v -> set_privacy_mode_checkbox(chat_id_editview.getText().toString(), chat_command_switch, privacy_mode_switch));
        verification_code_switch.setChecked(sharedPreferences.getBoolean("verification_code", false));

        doh_switch.setChecked(sharedPreferences.getBoolean("doh_switch", true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            doh_switch.setEnabled(!Paper.book("system_config").read("proxy_config", new proxy()).enable);
        }

        privacy_mode_switch.setChecked(sharedPreferences.getBoolean("privacy_mode", false));

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                assert tm != null;
                if (tm.getPhoneCount() <= 1) {
                    display_dual_sim_display_name_switch.setVisibility(View.GONE);
                }
            }
        }
        display_dual_sim_display_name_switch.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                display_dual_sim_display_name_switch.setChecked(false);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            } else {
                if (public_func.get_active_card(context) < 2) {
                    display_dual_sim_display_name_switch.setEnabled(false);
                    display_dual_sim_display_name_switch.setChecked(false);
                }
            }
        });


        chat_id_editview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                set_privacy_mode_checkbox(chat_id_editview.getText().toString(), chat_command_switch, privacy_mode_switch);
            }

            @Override
            public void afterTextChanged(Editable s) {
                //ignore
            }
        });


        get_id_button.setOnClickListener(v -> {
            if (bot_token_editview.getText().toString().isEmpty()) {
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
            String request_uri = public_func.get_url(bot_token_editview.getText().toString().trim(), "getUpdates");
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch.isChecked(), Paper.book("system_config").read("proxy_config", new proxy()));
            okhttp_client = okhttp_client.newBuilder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
            polling_json request_body = new polling_json();
            request_body.timeout = 60;
            RequestBody body = RequestBody.create(new Gson().toJson(request_body), public_value.JSON);
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
                    main_activity.this.runOnUiThread(() -> new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chat_name_list.toArray(new String[0]), (dialogInterface, i) -> chat_id_editview.setText(chat_id_list.get(i))).setPositiveButton("Cancel", null).show());
                }
            });
        });

        save_button.setOnClickListener(v -> {
            if (bot_token_editview.getText().toString().isEmpty() || chat_id_editview.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fallback_sms_switch.isChecked() && trusted_phone_number_editview.getText().toString().isEmpty()) {
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
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName()));
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

            String request_uri = public_func.get_url(bot_token_editview.getText().toString().trim(), "sendMessage");
            request_message request_body = new request_message();
            request_body.chat_id = chat_id_editview.getText().toString().trim();
            request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
            Gson gson = new Gson();
            String request_body_raw = gson.toJson(request_body);
            RequestBody body = RequestBody.create(request_body_raw, public_value.JSON);
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch.isChecked(), Paper.book("system_config").read("proxy_config", new proxy()));
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
                    String new_bot_token = bot_token_editview.getText().toString().trim();
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
                        Paper.book().destroy();
                    }
                    Paper.book("system_config").write("version", public_value.SYSTEM_CONFIG_VERSION);
                    check_version_upgrade(false);
                    SharedPreferences.Editor editor = sharedPreferences.edit().clear();
                    editor.putString("bot_token", new_bot_token);
                    editor.putString("chat_id", chat_id_editview.getText().toString().trim());
                    if (trusted_phone_number_editview.getText().toString().trim().length() != 0) {
                        editor.putString("trusted_phone_number", trusted_phone_number_editview.getText().toString().trim());
                    }
                    editor.putBoolean("fallback_sms", fallback_sms_switch.isChecked());
                    editor.putBoolean("chat_command", chat_command_switch.isChecked());
                    editor.putBoolean("battery_monitoring_switch", battery_monitoring_switch.isChecked());
                    editor.putBoolean("charger_status", charger_status_switch.isChecked());
                    editor.putBoolean("display_dual_sim_display_name", display_dual_sim_display_name_switch.isChecked());
                    editor.putBoolean("verification_code", verification_code_switch.isChecked());
                    editor.putBoolean("doh_switch", doh_switch.isChecked());
                    editor.putBoolean("privacy_mode", privacy_mode_switch.isChecked());
                    editor.putBoolean("initialized", true);
                    editor.putBoolean("privacy_dialog_agree", true);
                    editor.apply();
                    new Thread(() -> {
                        public_func.stop_all_service(context);
                        public_func.start_service(context, battery_monitoring_switch.isChecked(), chat_command_switch.isChecked());
                    }).start();
                    Looper.prepare();
                    Snackbar.make(v, R.string.success, Snackbar.LENGTH_LONG)
                            .show();
                    Looper.loop();
                }
            });
        });
    }

    private void set_privacy_mode_checkbox(String chat_id, @NotNull Switch chat_command, Switch privacy_mode_switch) {
        if (!chat_command.isChecked()) {
            privacy_mode_switch.setVisibility(View.GONE);
            privacy_mode_switch.setChecked(false);
            return;
        }
        if (public_func.parse_string_to_long(chat_id) < 0) {
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
            Uri uri = Uri.parse("https://get.telegram-sms.com" + privacy_police);
            CustomTabsIntent.Builder privacy_builder = new CustomTabsIntent.Builder();
            privacy_builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary));
            CustomTabsIntent customTabsIntent = privacy_builder.build();
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                customTabsIntent.launchUrl(context, uri);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Snackbar.make(findViewById(R.id.bot_token_editview), "Browser not found.", Snackbar.LENGTH_LONG).show();
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
                    Snackbar.make(findViewById(R.id.bot_token_editview), R.string.no_camera_permission, Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(context, scanner_activity.class);
                startActivityForResult(intent, 1);
                break;
            case 1:
                Switch display_dual_sim_display_name = findViewById(R.id.display_dual_sim_switch);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        assert tm != null;
                        if (tm.getPhoneCount() <= 1 || public_func.get_active_card(context) < 2) {
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            MenuItem set_proxy_item = menu.findItem(R.id.set_proxy_menu_item);
            set_proxy_item.setVisible(false);
        }
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        LayoutInflater inflater = this.getLayoutInflater();
        String file_name = null;
        switch (item.getItemId()) {
            case R.id.about_menu_item:
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo;
                String versionName = "";
                try {
                    packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                    versionName = packageInfo.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.about_title);
                builder.setMessage(getString(R.string.about_content) + versionName);
                builder.setCancelable(false);
                builder.setPositiveButton(R.string.ok_button, null);
                builder.show();
                return true;
            case R.id.scan_menu_item:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
                return true;
            case R.id.logcat_menu_item:
                Intent logcat_intent = new Intent(this, logcat_activity.class);
                startActivity(logcat_intent);
                return true;
            case R.id.config_qrcode_menu_item:
                if (sharedPreferences.getBoolean("initialized", false)) {
                    startActivity(new Intent(this, qrcode_show_activity.class));
                } else {
                    Snackbar.make(findViewById(R.id.bot_token_editview), "Uninitialized.", Snackbar.LENGTH_LONG).show();
                }
                return true;
            case R.id.set_notify_menu_item:
                if (!public_func.is_notify_listener(context)) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    set_permission_back = true;
                    return false;
                }
                startActivity(new Intent(this, notify_apps_list_activity.class));
                return true;
            case R.id.spam_sms_keyword_menu_item:
                startActivity(new Intent(this, spam_list_activity.class));
                return true;
            case R.id.set_proxy_menu_item:
                View proxy_dialog_view = inflater.inflate(R.layout.set_proxy_layout, null);
                final Switch doh_switch = findViewById(R.id.doh_switch);
                final Switch proxy_enable = proxy_dialog_view.findViewById(R.id.proxy_enable_switch);
                final Switch proxy_doh_socks5 = proxy_dialog_view.findViewById(R.id.doh_over_socks5_switch);
                final EditText proxy_host = proxy_dialog_view.findViewById(R.id.proxy_host_editview);
                final EditText proxy_port = proxy_dialog_view.findViewById(R.id.proxy_port_editview);
                final EditText proxy_username = proxy_dialog_view.findViewById(R.id.proxy_username_editview);
                final EditText proxy_password = proxy_dialog_view.findViewById(R.id.proxy_password_editview);
                proxy proxy_item = Paper.book("system_config").read("proxy_config", new proxy());
                proxy_enable.setChecked(proxy_item.enable);
                proxy_doh_socks5.setChecked(proxy_item.dns_over_socks5);
                proxy_host.setText(proxy_item.host);
                proxy_port.setText(String.valueOf(proxy_item.port));
                proxy_username.setText(proxy_item.username);
                proxy_password.setText(proxy_item.password);
                new AlertDialog.Builder(this).setTitle(R.string.proxy_dialog_title)
                        .setView(proxy_dialog_view)
                        .setPositiveButton("OK", (dialog, which) -> {
                            if (!doh_switch.isChecked()) {
                                doh_switch.setChecked(true);
                            }
                            doh_switch.setEnabled(!proxy_enable.isChecked());
                            proxy_item.enable = proxy_enable.isChecked();
                            proxy_item.dns_over_socks5 = proxy_doh_socks5.isChecked();
                            proxy_item.host = proxy_host.getText().toString();
                            proxy_item.port = Integer.parseInt(proxy_port.getText().toString());
                            proxy_item.username = proxy_username.getText().toString();
                            proxy_item.password = proxy_password.getText().toString();
                            Paper.book("system_config").write("proxy_config", proxy_item);
                            new Thread(() -> {
                                public_func.stop_all_service(context);
                                if (sharedPreferences.getBoolean("initialized", false)) {
                                    public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                                }
                            }).start();
                        })
                        .show();
                return true;
            case R.id.user_manual_menu_item:
                file_name = "/guide/" + context.getString(R.string.Lang) + "/user-manual";
                break;
            case R.id.privacy_policy_menu_item:
                file_name = privacy_police;
                break;
            case R.id.question_and_answer_menu_item:
                file_name = "/guide/" + context.getString(R.string.Lang) + "/Q&A";
                break;
            case R.id.donate_menu_item:
                file_name = "/donate";
                break;
        }
        assert file_name != null;
        Uri uri = Uri.parse("https://get.telegram-sms.com" + file_name);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            customTabsIntent.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Snackbar.make(findViewById(R.id.bot_token_editview), "Browser not found.", Snackbar.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            switch (resultCode) {
                case public_value.RESULT_CONFIG_JSON:
                    JsonObject json_config = JsonParser.parseString(Objects.requireNonNull(data.getStringExtra("config_json"))).getAsJsonObject();
                    ((EditText) findViewById(R.id.bot_token_editview)).setText(json_config.get("bot_token").getAsString());
                    ((EditText) findViewById(R.id.chat_id_editview)).setText(json_config.get("chat_id").getAsString());
                    ((Switch) findViewById(R.id.battery_monitoring_switch)).setChecked(json_config.get("battery_monitoring_switch").getAsBoolean());
                    ((Switch) findViewById(R.id.verification_code_switch)).setChecked(json_config.get("verification_code").getAsBoolean());

                    Switch charger_status = findViewById(R.id.charger_status_switch);
                    if (json_config.get("battery_monitoring_switch").getAsBoolean()) {
                        charger_status.setChecked(json_config.get("charger_status").getAsBoolean());
                        charger_status.setVisibility(View.VISIBLE);
                    } else {
                        charger_status.setChecked(false);
                        charger_status.setVisibility(View.GONE);
                    }

                    Switch chat_command = findViewById(R.id.chat_command_switch);
                    chat_command.setChecked(json_config.get("chat_command").getAsBoolean());
                    Switch privacy_mode_switch = findViewById(R.id.privacy_switch);
                    privacy_mode_switch.setChecked(json_config.get("privacy_mode").getAsBoolean());

                    set_privacy_mode_checkbox(json_config.get("chat_id").getAsString(), chat_command, privacy_mode_switch);

                    EditText trusted_phone_number = findViewById(R.id.trusted_phone_number_editview);
                    trusted_phone_number.setText(json_config.get("trusted_phone_number").getAsString());
                    Switch fallback_sms = findViewById(R.id.fallback_sms_switch);
                    fallback_sms.setChecked(json_config.get("fallback_sms").getAsBoolean());
                    if (trusted_phone_number.length() != 0) {
                        fallback_sms.setVisibility(View.VISIBLE);
                    } else {
                        fallback_sms.setVisibility(View.GONE);
                        fallback_sms.setChecked(false);
                    }
                    break;
                case public_value.RESULT_BOT_TOKEN:
                    ((EditText) findViewById(R.id.bot_token_editview)).setText(data.getStringExtra("bot_token"));
                    break;
            }
        }
    }
}

