package com.airfreshener.telegram_sms.mainScreen;

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

import com.airfreshener.telegram_sms.LogcatActivity;
import com.airfreshener.telegram_sms.notification_screen.NotifyAppsListActivity;
import com.airfreshener.telegram_sms.QrCodeShowActivity;
import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.ScannerActivity;
import com.airfreshener.telegram_sms.SpamListActivity;
import com.airfreshener.telegram_sms.model.ProxyConfigV2;
import com.airfreshener.telegram_sms.model.PollingJson;
import com.airfreshener.telegram_sms.model.RequestMessage;
import com.airfreshener.telegram_sms.migration.UpdateVersion1;
import com.airfreshener.telegram_sms.utils.Consts;
import com.airfreshener.telegram_sms.utils.LogUtils;
import com.airfreshener.telegram_sms.utils.NetworkUtils;
import com.airfreshener.telegram_sms.utils.OkHttpUtils;
import com.airfreshener.telegram_sms.utils.OtherUtils;
import com.airfreshener.telegram_sms.utils.ServiceUtils;
import com.airfreshener.telegram_sms.utils.ui.MenuUtils;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

public class MainActivity extends AppCompatActivity {
    private static boolean setPermissionBack = false;
    private final String TAG = "main_activity";
    private SharedPreferences sharedPreferences;
    private String privacyPolice;
    private Context context;

    private void checkVersionUpgrade(boolean reset_log) {
        int versionCode = Paper.book("system_config").read("version_code", 0);
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        int currentVersionCode;
        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            currentVersionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return;
        }
        if (versionCode != currentVersionCode) {
            if (reset_log) {
                LogUtils.resetLogFile(context);
            }
            Paper.book("system_config").write("version_code", currentVersionCode);
        }
    }

    private void updateConfig() {
        int storeVersion = Paper.book("system_config").read("version", 0);
        if (storeVersion == Consts.SYSTEM_CONFIG_VERSION) {
            new UpdateVersion1().checkError();
            return;
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (storeVersion) {
            case 0:
                new UpdateVersion1().update();
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
        privacyPolice = "/guide/" + context.getString(R.string.Lang) + "/privacy-policy";

        final EditText chatIdEditview = findViewById(R.id.chat_id_editview);
        final EditText botTokenEditview = findViewById(R.id.bot_token_editview);
        final EditText trustedPhoneNumberEditview = findViewById(R.id.trusted_phone_number_editview);
        final SwitchMaterial chatCommandSwitch = findViewById(R.id.chat_command_switch);
        final SwitchMaterial fallbackSmsSwitch = findViewById(R.id.fallback_sms_switch);
        final SwitchMaterial batteryMonitoringSwitch = findViewById(R.id.battery_monitoring_switch);
        final SwitchMaterial chargerStatusSwitch = findViewById(R.id.charger_status_switch);
        final SwitchMaterial dohSwitch = findViewById(R.id.doh_switch);
        final SwitchMaterial verificationCodeSwitch = findViewById(R.id.verification_code_switch);
        final SwitchMaterial privacyModeSwitch = findViewById(R.id.privacy_switch);
        final SwitchMaterial displayDualSimDisplayNameSwitch = findViewById(R.id.display_dual_sim_switch);
        final Button saveButton = findViewById(R.id.save_button);
        final Button getIdButton = findViewById(R.id.get_id_button);

        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog();
        }

        String botTokenSave = sharedPreferences.getString("bot_token", "");
        String chatIdSave = sharedPreferences.getString("chat_id", "");

        if (OtherUtils.parseStringToLong(chatIdSave) < 0) {
            privacyModeSwitch.setVisibility(View.VISIBLE);
        } else {
            privacyModeSwitch.setVisibility(View.GONE);
        }

        if (sharedPreferences.getBoolean("initialized", false)) {
            updateConfig();
            checkVersionUpgrade(true);
            ServiceUtils.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));

        }
        boolean displayDualSimDisplayNameConfig = sharedPreferences.getBoolean("display_dual_sim_display_name", false);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (OtherUtils.getActiveCard(context) < 2) {
                displayDualSimDisplayNameSwitch.setEnabled(false);
                displayDualSimDisplayNameConfig = false;
            }
            displayDualSimDisplayNameSwitch.setChecked(displayDualSimDisplayNameConfig);
        }

        botTokenEditview.setText(botTokenSave);
        chatIdEditview.setText(chatIdSave);
        trustedPhoneNumberEditview.setText(sharedPreferences.getString("trusted_phone_number", ""));
        batteryMonitoringSwitch.setChecked(sharedPreferences.getBoolean("battery_monitoring_switch", false));
        chargerStatusSwitch.setChecked(sharedPreferences.getBoolean("charger_status", false));

        if (!batteryMonitoringSwitch.isChecked()) {
            chargerStatusSwitch.setChecked(false);
            chargerStatusSwitch.setVisibility(View.GONE);
        }

        batteryMonitoringSwitch.setOnClickListener(v -> {
            if (batteryMonitoringSwitch.isChecked()) {
                chargerStatusSwitch.setVisibility(View.VISIBLE);
                chargerStatusSwitch.setEnabled(true);
            } else {
                chargerStatusSwitch.setEnabled(false);
                chargerStatusSwitch.setChecked(false);
            }
        });

        fallbackSmsSwitch.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        if (trustedPhoneNumberEditview.length() == 0) {
            fallbackSmsSwitch.setVisibility(View.GONE);
            fallbackSmsSwitch.setChecked(false);
        }
        trustedPhoneNumberEditview.addTextChangedListener(new TextWatcher() {
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
                if (trustedPhoneNumberEditview.length() != 0) {
                    fallbackSmsSwitch.setVisibility(View.VISIBLE);
                    fallbackSmsSwitch.setEnabled(true);
                } else {
                    //fallback_sms_switch.setVisibility(View.GONE);
                    fallbackSmsSwitch.setEnabled(false);
                    fallbackSmsSwitch.setChecked(false);
                }
            }
        });

        chatCommandSwitch.setChecked(sharedPreferences.getBoolean("chat_command", false));
        chatCommandSwitch.setOnClickListener(v -> setPrivacyModeCheckbox(chatIdEditview.getText().toString(), chatCommandSwitch, privacyModeSwitch));
        verificationCodeSwitch.setChecked(sharedPreferences.getBoolean("verification_code", false));

        dohSwitch.setChecked(sharedPreferences.getBoolean("doh_switch", true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dohSwitch.setEnabled(!Paper.book("system_config").read("proxy_config", new ProxyConfigV2()).getEnable());
        }

        privacyModeSwitch.setChecked(sharedPreferences.getBoolean("privacy_mode", false));

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                assert tm != null;
                if (tm.getPhoneCount() <= 1) {
                    displayDualSimDisplayNameSwitch.setVisibility(View.GONE);
                }
            }
        }
        displayDualSimDisplayNameSwitch.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                displayDualSimDisplayNameSwitch.setChecked(false);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            } else {
                if (OtherUtils.getActiveCard(context) < 2) {
                    displayDualSimDisplayNameSwitch.setEnabled(false);
                    displayDualSimDisplayNameSwitch.setChecked(false);
                }
            }
        });

        chatIdEditview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setPrivacyModeCheckbox(chatIdEditview.getText().toString(), chatCommandSwitch, privacyModeSwitch);
            }

            @Override
            public void afterTextChanged(Editable s) {
                //ignore
            }
        });

        getIdButton.setOnClickListener(v -> {
            if (botTokenEditview.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show();
                return;
            }
            new Thread(() -> ServiceUtils.stopAllService(context)).start();
            final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle(getString(R.string.get_recent_chat_title));
            progressDialog.setMessage(getString(R.string.get_recent_chat_message));
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
            String requestUri = NetworkUtils.getUrl(botTokenEditview.getText().toString().trim(), "getUpdates");
            OkHttpClient okhttpClient = NetworkUtils.getOkhttpObj(
                    dohSwitch.isChecked(),
                    Paper.book("system_config").read("proxy_config", new ProxyConfigV2())
            )
                    .newBuilder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
            PollingJson requestBody = new PollingJson();
            requestBody.timeout = 60;
            RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
            Request request = new Request.Builder().url(requestUri).method("POST", body).build();
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
                    e.printStackTrace();
                    progressDialog.cancel();
                    String errorMessage = errorHead + e.getMessage();
                    LogUtils.writeLog(context, errorMessage);
                    Looper.prepare();
                    Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
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
                        LogUtils.writeLog(context, errorMessage);

                        Looper.prepare();
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    String result = Objects.requireNonNull(response.body()).string();
                    JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                    JsonArray chatList = resultObj.getAsJsonArray("result");
                    if (chatList.size() == 0) {
                        Looper.prepare();
                        Snackbar.make(v, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    final ArrayList<String> chatNameList = new ArrayList<>();
                    final ArrayList<String> chatIdList = new ArrayList<>();
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
                                if (username.equals("") && !chatObj.has("username")) {
                                    if (chatObj.has("first_name")) {
                                        username = chatObj.get("first_name").getAsString();
                                    }
                                    if (chatObj.has("last_name")) {
                                        username += " " + chatObj.get("last_name").getAsString();
                                    }
                                }
                                chatNameList.add(username + "(" + chatObj.get("type").getAsString() + ")");
                                chatIdList.add(chatObj.get("id").getAsString());
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
                    MainActivity.this.runOnUiThread(() -> new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chatNameList.toArray(new String[0]), (dialogInterface, i) -> chatIdEditview.setText(chatIdList.get(i))).setPositiveButton(context.getString(R.string.cancel_button), null).show());
                }
            });
        });

        saveButton.setOnClickListener(v -> {
            if (botTokenEditview.getText().toString().isEmpty() || chatIdEditview.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (fallbackSmsSwitch.isChecked() && trustedPhoneNumberEditview.getText().toString().isEmpty()) {
                Snackbar.make(v, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CALL_LOG
                        },
                        1
                );

                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                assert powerManager != null;
                boolean hasIgnored = powerManager.isIgnoringBatteryOptimizations(getPackageName());
                if (!hasIgnored) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + getPackageName()));
                    if (intent.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        startActivity(intent);
                    }
                }
            }

            final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setTitle(getString(R.string.connect_wait_title));
            progressDialog.setMessage(getString(R.string.connect_wait_message));
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();

            String requestUri = NetworkUtils.getUrl(botTokenEditview.getText().toString().trim(), "sendMessage");
            RequestMessage requestBody = new RequestMessage();
            requestBody.chat_id = chatIdEditview.getText().toString().trim();
            requestBody.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
            RequestBody body = OkHttpUtils.INSTANCE.toRequestBody(requestBody);
            OkHttpClient okhttpClient = NetworkUtils.getOkhttpObj(
                    dohSwitch.isChecked(),
                    Paper.book("system_config").read("proxy_config", new ProxyConfigV2())
            );
            Request request = new Request.Builder().url(requestUri).method("POST", body).build();
            Call call = okhttpClient.newCall(request);
            final String errorHead = "Send message failed: ";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    progressDialog.cancel();
                    String errorMessage = errorHead + e.getMessage();
                    LogUtils.writeLog(context, errorMessage);
                    Looper.prepare();
                    Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    progressDialog.cancel();
                    String newBotToken = botTokenEditview.getText().toString().trim();
                    if (response.code() != 200) {
                        assert response.body() != null;
                        String result = Objects.requireNonNull(response.body()).string();
                        JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
                        String errorMessage = errorHead + resultObj.get("description");
                        LogUtils.writeLog(context, errorMessage);
                        Looper.prepare();
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show();
                        Looper.loop();
                        return;
                    }
                    if (!newBotToken.equals(botTokenSave)) {
                        Log.i(TAG, "onResponse: The current bot token does not match the saved bot token, clearing the message database.");
                        Paper.book().destroy();
                    }
                    Paper.book("system_config").write("version", Consts.SYSTEM_CONFIG_VERSION);
                    checkVersionUpgrade(false);
                    SharedPreferences.Editor editor = sharedPreferences.edit().clear();
                    editor.putString("bot_token", newBotToken);
                    editor.putString("chat_id", chatIdEditview.getText().toString().trim());
                    if (trustedPhoneNumberEditview.getText().toString().trim().length() != 0) {
                        editor.putString("trusted_phone_number", trustedPhoneNumberEditview.getText().toString().trim());
                    }
                    editor.putBoolean("fallback_sms", fallbackSmsSwitch.isChecked());
                    editor.putBoolean("chat_command", chatCommandSwitch.isChecked());
                    editor.putBoolean("battery_monitoring_switch", batteryMonitoringSwitch.isChecked());
                    editor.putBoolean("charger_status", chargerStatusSwitch.isChecked());
                    editor.putBoolean("display_dual_sim_display_name", displayDualSimDisplayNameSwitch.isChecked());
                    editor.putBoolean("verification_code", verificationCodeSwitch.isChecked());
                    editor.putBoolean("doh_switch", dohSwitch.isChecked());
                    editor.putBoolean("privacy_mode", privacyModeSwitch.isChecked());
                    editor.putBoolean("initialized", true);
                    editor.putBoolean("privacy_dialog_agree", true);
                    editor.apply();
                    new Thread(() -> {
                        ServiceUtils.stopAllService(context);
                        ServiceUtils.startService(context, batteryMonitoringSwitch.isChecked(), chatCommandSwitch.isChecked());
                    }).start();
                    Looper.prepare();
                    Snackbar.make(v, R.string.success, Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                }
            });
        });
    }

    private void setPrivacyModeCheckbox(
            String chat_id,
            @NotNull SwitchMaterial chatCommand,
            SwitchMaterial privacyModeSwitch
    ) {
        if (!chatCommand.isChecked()) {
            privacyModeSwitch.setVisibility(View.GONE);
            privacyModeSwitch.setChecked(false);
            return;
        }
        if (OtherUtils.parseStringToLong(chat_id) < 0) {
            privacyModeSwitch.setVisibility(View.VISIBLE);
        } else {
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
        boolean backStatus = setPermissionBack;
        setPermissionBack = false;
        if (backStatus) {
            if (ServiceUtils.isNotifyListener(context)) {
                startActivity(new Intent(MainActivity.this, NotifyAppsListActivity.class));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 0:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.");
                    Snackbar.make(
                            findViewById(R.id.bot_token_editview),
                            R.string.no_camera_permission,
                            Snackbar.LENGTH_LONG
                    ).show();
                    return;
                }
                Intent intent = new Intent(context, ScannerActivity.class);
                //noinspection deprecation
                startActivityForResult(intent, 1);
                break;
            case 1:
                SwitchMaterial displayDualSimDisplayName = findViewById(R.id.display_dual_sim_switch);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        assert tm != null;
                        if (tm.getPhoneCount() <= 1 || OtherUtils.getActiveCard(context) < 2) {
                            displayDualSimDisplayName.setEnabled(false);
                            displayDualSimDisplayName.setChecked(false);
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
            case R.id.about_menu_item:
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo;
                String versionName= "unknown";
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
                Intent logcat_intent = new Intent(this, LogcatActivity.class);
                startActivity(logcat_intent);
                return true;
            case R.id.config_qrcode_menu_item:
                if (sharedPreferences.getBoolean("initialized", false)) {
                    startActivity(new Intent(this, QrCodeShowActivity.class));
                } else {
                    Snackbar.make(findViewById(R.id.bot_token_editview), "Uninitialized.", Snackbar.LENGTH_LONG).show();
                }
                return true;
            case R.id.set_notify_menu_item:
                if (!ServiceUtils.isNotifyListener(context)) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    setPermissionBack = true;
                    return false;
                }
                startActivity(new Intent(this, NotifyAppsListActivity.class));
                return true;
            case R.id.spam_sms_keyword_menu_item:
                startActivity(new Intent(this, SpamListActivity.class));
                return true;
            case R.id.set_proxy_menu_item:
                final SwitchMaterial dohSwitch = findViewById(R.id.doh_switch);

                MenuUtils.showProxySettingsDialog(
                        inflater,
                        this,
                        context,
                        sharedPreferences,
                        isChecked -> {
                            if (!dohSwitch.isChecked()) {
                                dohSwitch.setChecked(true);
                            }
                            dohSwitch.setEnabled(!isChecked);
                        }
                );
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
        CustomTabColorSchemeParams params = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .build();
        builder.setDefaultColorSchemeParams(params);
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
            if (resultCode == Consts.RESULT_CONFIG_JSON) {
                JsonObject jsonConfig = JsonParser.parseString(Objects.requireNonNull(data.getStringExtra("config_json"))).getAsJsonObject();
                ((EditText) findViewById(R.id.bot_token_editview)).setText(jsonConfig.get("bot_token").getAsString());
                ((EditText) findViewById(R.id.chat_id_editview)).setText(jsonConfig.get("chat_id").getAsString());
                ((SwitchMaterial) findViewById(R.id.battery_monitoring_switch)).setChecked(jsonConfig.get("battery_monitoring_switch").getAsBoolean());
                ((SwitchMaterial) findViewById(R.id.verification_code_switch)).setChecked(jsonConfig.get("verification_code").getAsBoolean());

                SwitchMaterial chargerStatus = findViewById(R.id.charger_status_switch);
                if (jsonConfig.get("battery_monitoring_switch").getAsBoolean()) {
                    chargerStatus.setChecked(jsonConfig.get("charger_status").getAsBoolean());
                    chargerStatus.setVisibility(View.VISIBLE);
                } else {
                    chargerStatus.setChecked(false);
                    chargerStatus.setVisibility(View.GONE);
                }

                SwitchMaterial chatCommand = findViewById(R.id.chat_command_switch);
                chatCommand.setChecked(jsonConfig.get("chat_command").getAsBoolean());
                SwitchMaterial privacyModeSwitch = findViewById(R.id.privacy_switch);
                privacyModeSwitch.setChecked(jsonConfig.get("privacy_mode").getAsBoolean());
                setPrivacyModeCheckbox(jsonConfig.get("chat_id").getAsString(), chatCommand, privacyModeSwitch);
                EditText trusted_phone_number = findViewById(R.id.trusted_phone_number_editview);
                trusted_phone_number.setText(jsonConfig.get("trusted_phone_number").getAsString());
                SwitchMaterial fallbackSms = findViewById(R.id.fallback_sms_switch);
                fallbackSms.setChecked(jsonConfig.get("fallback_sms").getAsBoolean());
                if (trusted_phone_number.length() != 0) {
                    fallbackSms.setVisibility(View.VISIBLE);
                } else {
                    fallbackSms.setVisibility(View.GONE);
                    fallbackSms.setChecked(false);
                }
            }
        }
    }
}

