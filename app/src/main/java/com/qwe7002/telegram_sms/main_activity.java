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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.GitHubRelease;
import com.qwe7002.telegram_sms.data_structure.ScannerJson;
import com.qwe7002.telegram_sms.data_structure.pollingBody;
import com.qwe7002.telegram_sms.data_structure.RequestMessage;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.service;
import com.qwe7002.telegram_sms.value.constValue;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("deprecation")
public class main_activity extends AppCompatActivity {
    private static boolean setPermissionBack = false;
    private final String TAG = "main_activity";
    private SharedPreferences sharedPreferences;
    private String privacyPolice;
    private Context context;

    private void checkVersionUpgrade(boolean reset_log) {
        //noinspection ConstantConditions
        int version_code = Paper.book("system_config").read("version_code", 0);
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        int currentVersionCode;
        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            currentVersionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "checkVersionUpgrade: " + e);
            return;
        }
        if (version_code != currentVersionCode) {
            if (reset_log) {
                log.resetLogFile(context);
            }
            Paper.book("system_config").write("version_code", currentVersionCode);
        }
    }


    @SuppressWarnings("ConstantConditions")
    @SuppressLint({"BatteryLife"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();
        //load config
        Paper.init(context);
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        privacyPolice = "/guide/" + context.getString(R.string.Lang) + "/privacy-policy";

        final EditText chatIdEditView = findViewById(R.id.chat_id_editview);
        final EditText botTokenEditView = findViewById(R.id.bot_token_editview);
        final EditText messageThreadIdEditView = findViewById(R.id.message_thread_id_editview);
        final TextInputLayout messageThreadIdView = findViewById(R.id.message_thread_id_view);
        final EditText trustedPhoneNumberEditView = findViewById(R.id.trusted_phone_number_editview);
        final SwitchMaterial chatCommandSwitch = findViewById(R.id.chat_command_switch);
        final SwitchMaterial fallbackSmsSwitch = findViewById(R.id.fallback_sms_switch);
        final SwitchMaterial batteryMonitoringSwitch = findViewById(R.id.battery_monitoring_switch);
        final SwitchMaterial chargerStatusSwitch = findViewById(R.id.charger_status_switch);
        final SwitchMaterial dohSwitch = findViewById(R.id.doh_switch);
        final SwitchMaterial verificationCodeSwitch = findViewById(R.id.verification_code_switch);
        final SwitchMaterial privacyModeSwitch = findViewById(R.id.privacy_switch);
        final SwitchMaterial DualSimDisplayNameSwitch = findViewById(R.id.display_dual_sim_switch);
        final Button saveButton = findViewById(R.id.save_button);
        final Button getIdButton = findViewById(R.id.get_id_button);


        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog();
        }

        String botTokenSave = sharedPreferences.getString("bot_token", "");
        String chatIdSave = sharedPreferences.getString("chat_id", "");
        String messageThreadIdSave = sharedPreferences.getString("message_thread_id", "");

        if (other.parseStringToLong(chatIdSave) < 0) {
            privacyModeSwitch.setVisibility(View.VISIBLE);
            messageThreadIdView.setVisibility(View.VISIBLE);
        } else {
            privacyModeSwitch.setVisibility(View.GONE);
            messageThreadIdView.setVisibility(View.GONE);
        }

        if (sharedPreferences.getBoolean("initialized", false)) {
            checkVersionUpgrade(true);
            service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            ReSendJob.Companion.startJob(context);
            KeepAliveJob.Companion.startJob(context);

        }
        boolean DualSimDisplayNameConfig = sharedPreferences.getBoolean("display_dual_sim_display_name", false);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (other.getActiveCard(context) < 2) {
                DualSimDisplayNameSwitch.setEnabled(false);
                DualSimDisplayNameConfig = false;
            }
            DualSimDisplayNameSwitch.setChecked(DualSimDisplayNameConfig);
        }

        botTokenEditView.setText(botTokenSave);
        chatIdEditView.setText(chatIdSave);
        messageThreadIdEditView.setText(messageThreadIdSave);
        trustedPhoneNumberEditView.setText(sharedPreferences.getString("trusted_phone_number", ""));
        batteryMonitoringSwitch.setChecked(sharedPreferences.getBoolean("battery_monitoring_switch", false));
        chargerStatusSwitch.setChecked(sharedPreferences.getBoolean("charger_status", false));

        if (!batteryMonitoringSwitch.isChecked()) {
            chargerStatusSwitch.setChecked(false);
            chargerStatusSwitch.setVisibility(View.GONE);
        }

        batteryMonitoringSwitch.setOnClickListener(v -> {
            if (batteryMonitoringSwitch.isChecked()) {
                chargerStatusSwitch.setVisibility(View.VISIBLE);
            } else {
                chargerStatusSwitch.setVisibility(View.GONE);
                chargerStatusSwitch.setChecked(false);
            }
        });

        fallbackSmsSwitch.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        if (trustedPhoneNumberEditView.length() == 0) {
            fallbackSmsSwitch.setVisibility(View.GONE);
            fallbackSmsSwitch.setChecked(false);
        }
        trustedPhoneNumberEditView.addTextChangedListener(new TextWatcher() {
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
                if (trustedPhoneNumberEditView.length() != 0) {
                    fallbackSmsSwitch.setVisibility(View.VISIBLE);
                    fallbackSmsSwitch.setEnabled(true);
                } else {
                    fallbackSmsSwitch.setEnabled(false);
                    fallbackSmsSwitch.setChecked(false);
                }
            }
        });

        chatCommandSwitch.setChecked(sharedPreferences.getBoolean("chat_command", false));
        chatCommandSwitch.setOnClickListener(v -> privacyModeCheckbox(chatIdEditView.getText().toString(), chatCommandSwitch, privacyModeSwitch, messageThreadIdView));
        verificationCodeSwitch.setChecked(sharedPreferences.getBoolean("verification_code", false));

        dohSwitch.setChecked(sharedPreferences.getBoolean("doh_switch", true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dohSwitch.setEnabled(!Paper.book("system_config").read("proxy_config", new proxy()).enable);
        }

        privacyModeSwitch.setChecked(sharedPreferences.getBoolean("privacy_mode", false));

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                assert tm != null;
                if (tm.getPhoneCount() <= 1) {
                    DualSimDisplayNameSwitch.setVisibility(View.GONE);
                }
            }
        }
        DualSimDisplayNameSwitch.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                DualSimDisplayNameSwitch.setChecked(false);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            } else {
                if (other.getActiveCard(context) < 2) {
                    DualSimDisplayNameSwitch.setEnabled(false);
                    DualSimDisplayNameSwitch.setChecked(false);
                }
            }
        });


        chatIdEditView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                privacyModeCheckbox(chatIdEditView.getText().toString(), chatCommandSwitch, privacyModeSwitch, messageThreadIdView);
            }

            @Override
            public void afterTextChanged(Editable s) {
                //ignore
            }
        });


        getIdButton.setOnClickListener(v -> {
            if (botTokenEditView.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show();
                return;
            }
            new Thread(() -> service.stopAllService(context)).start();
            final ProgressDialog progressDialog = new ProgressDialog(main_activity.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle(getString(R.string.get_recent_chat_title));
            progressDialog.setMessage(getString(R.string.get_recent_chat_message));
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
            String request_uri = network.getUrl(botTokenEditView.getText().toString().trim(), "getUpdates");
            OkHttpClient okhttpClient = network.getOkhttpObj(dohSwitch.isChecked(), Paper.book("system_config").read("proxy_config", new proxy()));
            okhttpClient = okhttpClient.newBuilder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
            pollingBody request_body = new pollingBody();
            request_body.timeout = 60;
            RequestBody body = RequestBody.create(new Gson().toJson(request_body), constValue.JSON);
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttpClient.newCall(request);
            progressDialog.setOnKeyListener((dialogInterface, i, keyEvent) -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    call.cancel();
                }
                return false;
            });
            final String errorHead = "Get chat ID failed: ";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d(TAG, "onFailure: " + e);
                    progressDialog.cancel();
                    String error_message = errorHead + e.getMessage();
                    log.writeLog(context, error_message);
                    Looper.prepare();
                    Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    progressDialog.cancel();
                    assert response.body() != null;
                    if (response.code() != 200) {
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                        String errorMessage = errorHead + resultObj.get("description").getAsString();
                        log.writeLog(context, errorMessage);

                        Looper.prepare();
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }

                    String result = Objects.requireNonNull(response.body()).string();
                    Log.d(TAG, "onResponse: " + result);
                    JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                    JsonArray chatList = resultObj.getAsJsonArray("result");
                    if (chatList.isEmpty()) {
                        Looper.prepare();
                        Snackbar.make(v, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    final ArrayList<String> chatNameList = new ArrayList<>();
                    final ArrayList<String> chatIdList = new ArrayList<>();
                    final ArrayList<String> chatTopicIdList = new ArrayList<>();
                    for (JsonElement item : chatList) {
                        JsonObject itemObj = item.getAsJsonObject();
                        if (itemObj.has("message")) {
                            JsonObject messageObj = itemObj.get("message").getAsJsonObject();
                            JsonObject chatObj = messageObj.get("chat").getAsJsonObject();
                            if (!chatIdList.contains(chatObj.get("id").getAsString())) {
                                String username = "";
                                if (chatObj.has("username")) {
                                    username = chatObj.get("username").getAsString();
                                }
                                if (chatObj.has("title")) {
                                    username = chatObj.get("title").getAsString();
                                }
                                if (username.isEmpty() && !chatObj.has("username")) {
                                    if (chatObj.has("first_name")) {
                                        username = chatObj.get("first_name").getAsString();
                                    }
                                    if (chatObj.has("last_name")) {
                                        username += " " + chatObj.get("last_name").getAsString();
                                    }
                                }
                                String type = chatObj.get("type").getAsString();
                                chatNameList.add(username + "(" + type + ")");
                                chatIdList.add(chatObj.get("id").getAsString());
                                String threadId = "";
                                if (type.equals("supergroup") && messageObj.has("is_topic_message")) {
                                    threadId = messageObj.get("message_thread_id").getAsString();
                                }
                                chatTopicIdList.add(threadId);
                            }
                        }
                        if (itemObj.has("channel_post")) {
                            JsonObject messageObj = itemObj.get("channel_post").getAsJsonObject();
                            JsonObject chatObj = messageObj.get("chat").getAsJsonObject();
                            if (!chatIdList.contains(chatObj.get("id").getAsString())) {
                                chatNameList.add(chatObj.get("title").getAsString() + "(Channel)");
                                chatIdList.add(chatObj.get("id").getAsString());
                            }
                        }
                    }
                    main_activity.this.runOnUiThread(() -> new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chatNameList.toArray(new String[0]), (dialogInterface, i) -> {
                        chatIdEditView.setText(chatIdList.get(i));
                        messageThreadIdEditView.setText(chatTopicIdList.get(i));
                    }).setPositiveButton(context.getString(R.string.cancel_button), null).show());
                }
            });
        });

        saveButton.setOnClickListener(v -> {
            if (botTokenEditView.getText().toString().isEmpty() || chatIdEditView.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fallbackSmsSwitch.isChecked() && trustedPhoneNumberEditView.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String[] permissionList = new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ArrayList<String> permissionArrayList = new ArrayList<>(Arrays.asList(permissionList));
                    permissionArrayList.add(Manifest.permission.POST_NOTIFICATIONS);
                    permissionList = permissionArrayList.toArray(new String[0]);
                }
                ActivityCompat.requestPermissions(main_activity.this, permissionList, 1);

                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                assert powerManager != null;
                boolean hasIgnored = powerManager.isIgnoringBatteryOptimizations(getPackageName());
                if (!hasIgnored) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName()));
                    if (intent.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        startActivity(intent);
                    }
                }
            }

            final ProgressDialog progressDialog = new ProgressDialog(main_activity.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle(getString(R.string.connect_wait_title));
            progressDialog.setMessage(getString(R.string.connect_wait_message));
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();

            String requestUri = network.getUrl(botTokenEditView.getText().toString().trim(), "sendMessage");
            RequestMessage requestBody = new RequestMessage();
            requestBody.chatId = chatIdEditView.getText().toString().trim();
            requestBody.messageThreadId = messageThreadIdEditView.getText().toString().trim();
            requestBody.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
            Gson gson = new Gson();
            String requestBodyRaw = gson.toJson(requestBody);
            RequestBody body = RequestBody.create(requestBodyRaw, constValue.JSON);
            OkHttpClient okhttpObj = network.getOkhttpObj(dohSwitch.isChecked(), Paper.book("system_config").read("proxy_config", new proxy()));
            Request request = new Request.Builder().url(requestUri).method("POST", body).build();
            Call call = okhttpObj.newCall(request);
            final String errorHead = "Send message failed: ";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d(TAG, "onFailure: " + e);
                    progressDialog.cancel();
                    String errorMessage = errorHead + e.getMessage();
                    log.writeLog(context, errorMessage);
                    Looper.prepare();
                    Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG)
                            .show();
                    Looper.loop();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    progressDialog.cancel();
                    String newBotToken = botTokenEditView.getText().toString().trim();
                    if (response.code() != 200) {
                        assert response.body() != null;
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                        String errorMessage = errorHead + resultObj.get("description");
                        log.writeLog(context, errorMessage);
                        Looper.prepare();
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    if (!newBotToken.equals(botTokenSave)) {
                        Log.i(TAG, "onResponse: The current bot token does not match the saved bot token, clearing the message database.");
                        Paper.book().destroy();
                    }
                    Paper.book("system_config").write("version", constValue.SYSTEM_CONFIG_VERSION);
                    checkVersionUpgrade(false);
                    SharedPreferences.Editor editor = sharedPreferences.edit().clear();
                    editor.putString("bot_token", newBotToken);
                    editor.putString("chat_id", chatIdEditView.getText().toString().trim());
                    editor.putString("message_thread_id", messageThreadIdEditView.getText().toString().trim());
                    if (!trustedPhoneNumberEditView.getText().toString().trim().isEmpty()) {
                        editor.putString("trusted_phone_number", trustedPhoneNumberEditView.getText().toString().trim());
                    }
                    editor.putBoolean("fallback_sms", fallbackSmsSwitch.isChecked());
                    editor.putBoolean("chat_command", chatCommandSwitch.isChecked());
                    editor.putBoolean("battery_monitoring_switch", batteryMonitoringSwitch.isChecked());
                    editor.putBoolean("charger_status", chargerStatusSwitch.isChecked());
                    editor.putBoolean("display_dual_sim_display_name", DualSimDisplayNameSwitch.isChecked());
                    editor.putBoolean("verification_code", verificationCodeSwitch.isChecked());
                    editor.putBoolean("doh_switch", dohSwitch.isChecked());
                    editor.putBoolean("privacy_mode", privacyModeSwitch.isChecked());
                    editor.putBoolean("initialized", true);
                    editor.putBoolean("privacy_dialog_agree", true);
                    editor.apply();
                    new Thread(() -> {
                        ReSendJob.Companion.stopJob(context);
                        KeepAliveJob.Companion.stopJob(context);
                        service.stopAllService(context);
                        service.startService(context, batteryMonitoringSwitch.isChecked(), chatCommandSwitch.isChecked());
                        ReSendJob.Companion.startJob(context);
                        KeepAliveJob.Companion.startJob(context);
                    }).start();
                    Looper.prepare();
                    Snackbar.make(v, R.string.success, Snackbar.LENGTH_LONG)
                            .show();
                    Looper.loop();
                }
            });
        });
    }

    private void privacyModeCheckbox(String chatId, @NotNull SwitchMaterial chatCommand, SwitchMaterial privacyModeSwitch, TextInputLayout messageTopicIdView) {
        if (!chatCommand.isChecked()) {
            messageTopicIdView.setVisibility(View.GONE);
            privacyModeSwitch.setVisibility(View.GONE);
            privacyModeSwitch.setChecked(false);
            return;
        }
        if (other.parseStringToLong(chatId) < 0) {
            messageTopicIdView.setVisibility(View.VISIBLE);
            privacyModeSwitch.setVisibility(View.VISIBLE);
        } else {
            messageTopicIdView.setVisibility(View.GONE);
            privacyModeSwitch.setVisibility(View.GONE);
            privacyModeSwitch.setChecked(false);
        }
    }

    private void showPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.privacy_reminder_title);
        builder.setMessage(R.string.privacy_reminder_information);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.agree, (dialog, which) -> sharedPreferences.edit().putBoolean("privacy_dialog_agree", true).apply());
        builder.setNegativeButton(R.string.decline, null);
        builder.setNeutralButton(R.string.visit_page, (dialog, which) -> {
            Uri uri = Uri.parse("https://get.telegram-sms.com" + privacyPolice);
            CustomTabsIntent.Builder privacy_builder = new CustomTabsIntent.Builder();
            //noinspection deprecation
            privacy_builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary));
            CustomTabsIntent customTabsIntent = privacy_builder.build();
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                customTabsIntent.launchUrl(context, uri);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "showPrivacyDialog: " + e);
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
        boolean back_status = setPermissionBack;
        setPermissionBack = false;
        if (back_status) {
            if (service.isNotifyListener(context)) {
                startActivity(new Intent(main_activity.this, NotifyActivity.class));
            }
        }
        Long lastCheck = Paper.book("update").read("last_check", 0L);
        assert lastCheck != null;
        if (lastCheck == 0L) {
            Paper.book("update").write("last_check", System.currentTimeMillis());
        }
        if (lastCheck + TimeUnit.DAYS.toMillis(15) < System.currentTimeMillis()) {
            checkUpdate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 0:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.");
                    Snackbar.make(findViewById(R.id.bot_token_editview), R.string.no_camera_permission, Snackbar.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(context, ScannerActivity.class);
                //noinspection deprecation
                startActivityForResult(intent, 1);
                break;
            case 1:
                SwitchMaterial dualSimDisplayName = findViewById(R.id.display_dual_sim_switch);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        assert tm != null;
                        if (tm.getPhoneCount() <= 1 || other.getActiveCard(context) < 2) {
                            dualSimDisplayName.setEnabled(false);
                            dualSimDisplayName.setChecked(false);
                        }
                    }
                }
                break;
        }
    }

    void checkUpdate() {
        Paper.book("update").write("last_check",System.currentTimeMillis());
        final ProgressDialog progressDialog = new ProgressDialog(main_activity.this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setTitle(getString(R.string.connect_wait_title));
        progressDialog.setMessage(getString(R.string.connect_wait_message));
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
        OkHttpClient okhttpObj = network.getOkhttpObj(false, new proxy());
        String requestUri = String.format("https://api.github.com/repos/telegram-sms/%s/releases/latest",context.getString(R.string.app_identifier));
        Request request = new Request.Builder().url(requestUri).build();
        Call call = okhttpObj.newCall(request);
        final String errorHead = "Send message failed: ";
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                progressDialog.cancel();
                if(!response.isSuccessful()){
                    String errorMessage = errorHead + response.code();
                    log.writeLog(context, errorMessage);
                }
                String jsonString = response.body().string();
                Log.d(TAG, "onResponse: "+jsonString);
                Gson gson = new Gson();
                GitHubRelease release = gson.fromJson(jsonString, GitHubRelease.class);
                String versionName = "unknown";
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo;
                try {
                    packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                    versionName = packageInfo.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(TAG, "onOptionsItemSelected: " + e);
                }
                if(!release.getTagName().equals(versionName)){
                    runOnUiThread(() -> showUpdateDialog(release.getTagName(), release.getBody(),release.getAssets().get(0).getBrowserDownloadUrl()));

                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "onFailure: " + e);
                progressDialog.cancel();
                String errorMessage = errorHead + e.getMessage();
                log.writeLog(context, errorMessage);
                Looper.prepare();
                Snackbar.make(findViewById(R.id.content), errorMessage, Snackbar.LENGTH_LONG)
                        .show();
                Looper.loop();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            MenuItem setProxyItem = menu.findItem(R.id.set_proxy_menu_item);
            setProxyItem.setVisible(false);
        }
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        LayoutInflater inflater = this.getLayoutInflater();
        String fileName = null;
        switch (item.getItemId()) {
            case R.id.check_update_menu_item:
                checkUpdate();
                return true;
            case R.id.about_menu_item:
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo;
                String versionName = "unknown";
                try {
                    packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                    versionName = packageInfo.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(TAG, "onOptionsItemSelected: " + e);
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
                Intent logcat_intent = new Intent(this, LogActivity.class);
                startActivity(logcat_intent);
                return true;
            case R.id.config_qrcode_menu_item:
                if (sharedPreferences.getBoolean("initialized", false)) {
                    startActivity(new Intent(this, QrcodeActivity.class));
                } else {
                    Snackbar.make(findViewById(R.id.bot_token_editview), "Uninitialized.", Snackbar.LENGTH_LONG).show();
                }
                return true;
            case R.id.set_notify_menu_item:
                if (!service.isNotifyListener(context)) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    setPermissionBack = true;
                    return false;
                }
                startActivity(new Intent(this, NotifyActivity.class));
                return true;
            case R.id.spam_sms_keyword_menu_item:
                startActivity(new Intent(this, SpamActivity.class));
                return true;
            case R.id.set_proxy_menu_item:
                View view = inflater.inflate(R.layout.set_proxy_layout, null);
                final SwitchMaterial dohSwitch = findViewById(R.id.doh_switch);
                final SwitchMaterial proxyEnable = view.findViewById(R.id.proxy_enable_switch);
                final SwitchMaterial proxyDohSocks5 = view.findViewById(R.id.doh_over_socks5_switch);
                final EditText proxyHost = view.findViewById(R.id.proxy_host_editview);
                final EditText proxyPort = view.findViewById(R.id.proxy_port_editview);
                final EditText proxyUsername = view.findViewById(R.id.proxy_username_editview);
                final EditText proxyPassword = view.findViewById(R.id.proxy_password_editview);
                proxy proxyItem = Paper.book("system_config").read("proxy_config", new proxy());
                assert proxyItem != null;
                proxyEnable.setChecked(proxyItem.enable);
                proxyDohSocks5.setChecked(proxyItem.dns_over_socks5);
                proxyHost.setText(proxyItem.host);
                proxyPort.setText(String.valueOf(proxyItem.port));
                proxyUsername.setText(proxyItem.username);
                proxyPassword.setText(proxyItem.password);
                new AlertDialog.Builder(this).setTitle(R.string.proxy_dialog_title)
                        .setView(view)
                        .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                            if (!dohSwitch.isChecked()) {
                                dohSwitch.setChecked(true);
                            }
                            dohSwitch.setEnabled(!proxyEnable.isChecked());
                            proxyItem.enable = proxyEnable.isChecked();
                            proxyItem.dns_over_socks5 = proxyDohSocks5.isChecked();
                            proxyItem.host = proxyHost.getText().toString();
                            proxyItem.port = Integer.parseInt(proxyPort.getText().toString());
                            proxyItem.username = proxyUsername.getText().toString();
                            proxyItem.password = proxyPassword.getText().toString();
                            Paper.book("system_config").write("proxy_config", proxyItem);
                            new Thread(() -> {
                                service.stopAllService(context);
                                if (sharedPreferences.getBoolean("initialized", false)) {
                                    service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                                }
                            }).start();
                        })
                        .show();
                return true;
            case R.id.user_manual_menu_item:
                fileName = "/guide/" + context.getString(R.string.Lang) + "/user-manual";
                break;
            case R.id.privacy_policy_menu_item:
                fileName = privacyPolice;
                break;
            case R.id.question_and_answer_menu_item:
                fileName = "/guide/" + context.getString(R.string.Lang) + "/Q&A";
                break;
            case R.id.donate_menu_item:
                fileName = "/donate";
                break;
        }
        assert fileName != null;
        Uri uri = Uri.parse("https://get.telegram-sms.com" + fileName);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabColorSchemeParams params = new CustomTabColorSchemeParams.Builder().setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary)).build();
        builder.setDefaultColorSchemeParams(params);
        CustomTabsIntent customTabsIntent = builder.build();
        try {
            customTabsIntent.launchUrl(this, uri);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "onOptionsItemSelected: " + e);
            Snackbar.make(findViewById(R.id.bot_token_editview), "Browser not found.", Snackbar.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == constValue.RESULT_CONFIG_JSON) {
                //JsonObject jsonConfig = JsonParser.parseString(Objects.requireNonNull(data.getStringExtra("config_json"))).getAsJsonObject();
                Gson gson = new Gson();
                ScannerJson jsonConfig = gson.fromJson(Objects.requireNonNull(data.getStringExtra("config_json")), ScannerJson.class);
                ((EditText) findViewById(R.id.bot_token_editview)).setText(jsonConfig.getBotToken());
                ((EditText) findViewById(R.id.chat_id_editview)).setText(jsonConfig.getChatId());
                ((SwitchMaterial) findViewById(R.id.battery_monitoring_switch)).setChecked(jsonConfig.getBatteryMonitoringSwitch());
                ((SwitchMaterial) findViewById(R.id.verification_code_switch)).setChecked(jsonConfig.getVerificationCode());

                SwitchMaterial chargerStatus = findViewById(R.id.charger_status_switch);
                if (jsonConfig.getBatteryMonitoringSwitch()) {
                    chargerStatus.setChecked(jsonConfig.getChargerStatus());
                    chargerStatus.setVisibility(View.VISIBLE);
                } else {
                    chargerStatus.setChecked(false);
                    chargerStatus.setVisibility(View.GONE);
                }

                SwitchMaterial chatCommand = findViewById(R.id.chat_command_switch);
                chatCommand.setChecked(jsonConfig.getChatCommand());
                SwitchMaterial privacyModeSwitch = findViewById(R.id.privacy_switch);
                privacyModeSwitch.setChecked(jsonConfig.getPrivacyMode());
                final com.google.android.material.textfield.TextInputLayout messageThreadIdView = findViewById(R.id.message_thread_id_view);

                privacyModeCheckbox(jsonConfig.getChatId(), chatCommand, privacyModeSwitch, messageThreadIdView);

                EditText trustedPhoneNumber = findViewById(R.id.trusted_phone_number_editview);
                trustedPhoneNumber.setText(jsonConfig.getTrustedPhoneNumber());
                SwitchMaterial fallbackSms = findViewById(R.id.fallback_sms_switch);
                fallbackSms.setChecked(jsonConfig.getFallbackSms());
                if (trustedPhoneNumber.length() != 0) {
                    fallbackSms.setVisibility(View.VISIBLE);
                } else {
                    fallbackSms.setVisibility(View.GONE);
                    fallbackSms.setChecked(false);
                }
                EditText topicIdView = findViewById(R.id.message_thread_id_editview);
                topicIdView.setText(jsonConfig.getTopicID());
            }
        }
    }
    private void showUpdateDialog(String newVersion, String updateContent,String fileURL) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.update_dialog_title);
        String message = String.format(getString(R.string.update_dialog_body),
                newVersion,
                updateContent);

        builder.setMessage(message)
                .setPositiveButton(R.string.update_dialog_ok, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fileURL));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.update_dialog_no, (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }


}

