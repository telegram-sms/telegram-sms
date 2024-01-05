package com.airfreshener.telegram_sms.mainScreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.airfreshener.telegram_sms.LogcatActivity
import com.airfreshener.telegram_sms.QrCodeShowActivity
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.ScannerActivity
import com.airfreshener.telegram_sms.SpamListActivity
import com.airfreshener.telegram_sms.migration.UpdateVersion1
import com.airfreshener.telegram_sms.model.PollingJson
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.notification_screen.NotifyAppsListActivity
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils.getActiveCard
import com.airfreshener.telegram_sms.utils.OtherUtils.parseStringToLong
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.ServiceUtils.isNotifyListener
import com.airfreshener.telegram_sms.utils.ServiceUtils.startService
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopAllService
import com.airfreshener.telegram_sms.utils.ui.MenuUtils.showProxySettingsDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var sharedPreferences: SharedPreferences? = null
    private var privacyPolice: String? = null

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val appContext = applicationContext
        PaperUtils.init(appContext)
        val sharedPreferences = getSharedPreferences("data", MODE_PRIVATE).apply {
            sharedPreferences = this
        }
        privacyPolice = "/guide/" + appContext.getString(R.string.Lang) + "/privacy-policy"
        val chatIdEditview = findViewById<EditText>(R.id.chat_id_editview)
        val botTokenEditview = findViewById<EditText>(R.id.bot_token_editview)
        val trustedPhoneNumberEditview = findViewById<EditText>(R.id.trusted_phone_number_editview)
        val chatCommandSwitch = findViewById<SwitchMaterial>(R.id.chat_command_switch)
        val fallbackSmsSwitch = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
        val batteryMonitoringSwitch = findViewById<SwitchMaterial>(R.id.battery_monitoring_switch)
        val chargerStatusSwitch = findViewById<SwitchMaterial>(R.id.charger_status_switch)
        val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
        val verificationCodeSwitch = findViewById<SwitchMaterial>(R.id.verification_code_switch)
        val privacyModeSwitch = findViewById<SwitchMaterial>(R.id.privacy_switch)
        val displayDualSimDisplayNameSwitch =
            findViewById<SwitchMaterial>(R.id.display_dual_sim_switch)
        val saveButton = findViewById<Button>(R.id.save_button)
        val getIdButton = findViewById<Button>(R.id.get_id_button)
        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog()
        }
        val botTokenSave = sharedPreferences.getString("bot_token", "")
        val chatIdSave = sharedPreferences.getString("chat_id", "")
        if (parseStringToLong(chatIdSave!!) < 0) {
            privacyModeSwitch.visibility = View.VISIBLE
        } else {
            privacyModeSwitch.visibility = View.GONE
        }
        if (sharedPreferences.getBoolean("initialized", false)) {
            updateConfig()
            checkVersionUpgrade(appContext, true)
            startService(
                appContext,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
        }
        var displayDualSimDisplayNameConfig =
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (getActiveCard(appContext) < 2) {
                displayDualSimDisplayNameSwitch.isEnabled = false
                displayDualSimDisplayNameConfig = false
            }
            displayDualSimDisplayNameSwitch.isChecked = displayDualSimDisplayNameConfig
        }
        botTokenEditview.setText(botTokenSave)
        chatIdEditview.setText(chatIdSave)
        trustedPhoneNumberEditview.setText(sharedPreferences.getString("trusted_phone_number", ""))
        batteryMonitoringSwitch.isChecked =
            sharedPreferences.getBoolean("battery_monitoring_switch", false)
        chargerStatusSwitch.isChecked = sharedPreferences.getBoolean("charger_status", false)
        if (!batteryMonitoringSwitch.isChecked) {
            chargerStatusSwitch.isChecked = false
            chargerStatusSwitch.visibility = View.GONE
        }
        batteryMonitoringSwitch.setOnClickListener {
            if (batteryMonitoringSwitch.isChecked) {
                chargerStatusSwitch.visibility = View.VISIBLE
                chargerStatusSwitch.isEnabled = true
            } else {
                chargerStatusSwitch.isEnabled = false
                chargerStatusSwitch.isChecked = false
            }
        }
        fallbackSmsSwitch.isChecked = sharedPreferences.getBoolean("fallback_sms", false)
        if (trustedPhoneNumberEditview.length() == 0) {
            fallbackSmsSwitch.visibility = View.GONE
            fallbackSmsSwitch.isChecked = false
        }
        trustedPhoneNumberEditview.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                //ignore
            }

            override fun afterTextChanged(s: Editable) {
                if (trustedPhoneNumberEditview.length() != 0) {
                    fallbackSmsSwitch.visibility = View.VISIBLE
                    fallbackSmsSwitch.isEnabled = true
                } else {
                    // fallback_sms_switch.setVisibility(View.GONE);
                    fallbackSmsSwitch.isEnabled = false
                    fallbackSmsSwitch.isChecked = false
                }
            }
        })
        chatCommandSwitch.isChecked = sharedPreferences.getBoolean("chat_command", false)
        chatCommandSwitch.setOnClickListener { v: View? ->
            setPrivacyModeCheckbox(
                chatIdEditview.text.toString(),
                chatCommandSwitch,
                privacyModeSwitch
            )
        }
        verificationCodeSwitch.isChecked = sharedPreferences.getBoolean("verification_code", false)
        dohSwitch.isChecked = sharedPreferences.getBoolean("doh_switch", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dohSwitch.isEnabled = !PaperUtils.getProxyConfig().enable
        }
        privacyModeSwitch.isChecked = sharedPreferences.getBoolean("privacy_mode", false)
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val tm = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                if (tm.phoneCount <= 1) {
                    displayDualSimDisplayNameSwitch.visibility = View.GONE
                }
            }
        }
        displayDualSimDisplayNameSwitch.setOnClickListener { v: View? ->
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                displayDualSimDisplayNameSwitch.isChecked = false
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    1
                )
            } else {
                if (getActiveCard(appContext) < 2) {
                    displayDualSimDisplayNameSwitch.isEnabled = false
                    displayDualSimDisplayNameSwitch.isChecked = false
                }
            }
        }
        chatIdEditview.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                setPrivacyModeCheckbox(
                    chatIdEditview.text.toString(),
                    chatCommandSwitch,
                    privacyModeSwitch
                )
            }
        })
        getIdButton.setOnClickListener { v: View ->
            if (botTokenEditview.text.toString().isEmpty()) {
                Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Thread { stopAllService(appContext) }.start()
            val progressDialog = ProgressDialog(this@MainActivity)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.get_recent_chat_title))
            progressDialog.setMessage(getString(R.string.get_recent_chat_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            val requestUri =
                getUrl(botTokenEditview.text.toString().trim { it <= ' ' }, "getUpdates")
            val okhttpClient = getOkhttpObj(dohSwitch.isChecked, PaperUtils.getProxyConfig())
                .newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val requestBody = PollingJson()
            requestBody.timeout = 60
            val body = requestBody.toRequestBody()
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpClient.newCall(request)
            progressDialog.setOnKeyListener { _: DialogInterface?, _: Int, keyEvent: KeyEvent ->
                if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    call.cancel()
                }
                false
            }
            val errorHead = "Get chat ID failed: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    LogUtils.writeLog(appContext, errorMessage)
                    Looper.prepare()
                    Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show()
                    Looper.loop()
                }

                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    val responseBody = response.body ?: return
                    if (response.code != 200) {
                        val result = responseBody.string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"].asString
                        LogUtils.writeLog(appContext, errorMessage)
                        Looper.prepare()
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
                        return
                    }
                    val result = responseBody.string()
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    val chatList = resultObj.getAsJsonArray("result")
                    if (chatList.size() == 0) {
                        Looper.prepare()
                        Snackbar.make(v, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
                        return
                    }
                    val chatNameList = ArrayList<String>()
                    val chatIdList = ArrayList<String>()
                    for (item in chatList) {
                        val itemObj = item.asJsonObject
                        if (itemObj.has("message")) {
                            val messageObj = itemObj["message"].asJsonObject
                            val chatObj = messageObj["chat"].asJsonObject
                            if (!chatIdList.contains(chatObj["id"].asString)) {
                                var username = ""
                                if (chatObj.has("username")) {
                                    username = chatObj["username"].asString
                                }
                                if (chatObj.has("title")) {
                                    username = chatObj["title"].asString
                                }
                                if (username == "" && !chatObj.has("username")) {
                                    if (chatObj.has("first_name")) {
                                        username = chatObj["first_name"].asString
                                    }
                                    if (chatObj.has("last_name")) {
                                        username += " " + chatObj["last_name"].asString
                                    }
                                }
                                chatNameList.add(username + "(" + chatObj["type"].asString + ")")
                                chatIdList.add(chatObj["id"].asString)
                            }
                        }
                        if (itemObj.has("channel_post")) {
                            val messageObj = itemObj["channel_post"].asJsonObject
                            val chatObj = messageObj["chat"].asJsonObject
                            if (!chatIdList.contains(chatObj["id"].asString)) {
                                chatNameList.add(chatObj["title"].asString + "(Channel)")
                                chatIdList.add(chatObj["id"].asString)
                            }
                        }
                    }
                    runOnUiThread {
                        AlertDialog.Builder(v.context)
                            .setTitle(R.string.select_chat)
                            .setItems(
                                chatNameList.toTypedArray<String>()
                            ) { _: DialogInterface?, i: Int ->
                                chatIdEditview.setText(chatIdList[i])
                            }
                            .setPositiveButton(appContext.getString(R.string.cancel_button), null)
                            .show()
                    }
                }
            })
        }
        saveButton.setOnClickListener { v: View ->
            if (botTokenEditview.text.toString().isEmpty() || chatIdEditview.text.toString()
                    .isEmpty()
            ) {
                Snackbar.make(v, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            if (fallbackSmsSwitch.isChecked && trustedPhoneNumberEditview.text.toString()
                    .isEmpty()
            ) {
                Snackbar.make(v, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG
                    ),
                    1
                )
                val powerManager = (getSystemService(POWER_SERVICE) as PowerManager)
                val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
                if (!hasIgnored) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                    if (intent.resolveActivityInfo(
                            packageManager,
                            PackageManager.MATCH_DEFAULT_ONLY
                        ) != null
                    ) {
                        startActivity(intent)
                    }
                }
            }
            val progressDialog = ProgressDialog(this@MainActivity)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.connect_wait_title))
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            val requestUri =
                getUrl(botTokenEditview.text.toString().trim { it <= ' ' }, "sendMessage")
            val requestBody = RequestMessage()
            requestBody.chat_id = chatIdEditview.text.toString().trim { it <= ' ' }
            requestBody.text = """
                ${getString(R.string.system_message_head)}
                ${getString(R.string.success_connect)}
                """.trimIndent()
            val body = requestBody.toRequestBody()
            val okhttpClient = getOkhttpObj(
                dohSwitch.isChecked,
                PaperUtils.getProxyConfig()
            )
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpClient.newCall(request)
            val errorHead = "Send message failed: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    LogUtils.writeLog(appContext, errorMessage)
                    Looper.prepare()
                    Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show()
                    Looper.loop()
                }

                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    val newBotToken = botTokenEditview.text.toString().trim { it <= ' ' }
                    if (response.code != 200) {
                        val responseBody = response.body ?: return
                        val result = responseBody.string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"]
                        LogUtils.writeLog(appContext, errorMessage)
                        Looper.prepare()
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
                        return
                    }
                    if (newBotToken != botTokenSave) {
                        Log.i(
                            TAG,
                            "onResponse: The current bot token does not match the saved bot token, clearing the message database."
                        )
                        DEFAULT_BOOK.destroy()
                    }
                    SYSTEM_BOOK.write("version", Consts.SYSTEM_CONFIG_VERSION)
                    checkVersionUpgrade(appContext, false)
                    val editor = sharedPreferences.edit().clear()
                    editor.putString("bot_token", newBotToken)
                    editor.putString("chat_id", chatIdEditview.text.toString().trim { it <= ' ' })
                    if (trustedPhoneNumberEditview.text.toString().trim { it <= ' ' }.isNotEmpty()) {
                        editor.putString(
                            "trusted_phone_number",
                            trustedPhoneNumberEditview.text.toString().trim { it <= ' ' })
                    }
                    editor.putBoolean("fallback_sms", fallbackSmsSwitch.isChecked)
                    editor.putBoolean("chat_command", chatCommandSwitch.isChecked)
                    editor.putBoolean(
                        "battery_monitoring_switch",
                        batteryMonitoringSwitch.isChecked
                    )
                    editor.putBoolean("charger_status", chargerStatusSwitch.isChecked)
                    editor.putBoolean(
                        "display_dual_sim_display_name",
                        displayDualSimDisplayNameSwitch.isChecked
                    )
                    editor.putBoolean("verification_code", verificationCodeSwitch.isChecked)
                    editor.putBoolean("doh_switch", dohSwitch.isChecked)
                    editor.putBoolean("privacy_mode", privacyModeSwitch.isChecked)
                    editor.putBoolean("initialized", true)
                    editor.putBoolean("privacy_dialog_agree", true)
                    editor.apply()
                    Thread {
                        stopAllService(appContext)
                        startService(
                            appContext,
                            batteryMonitoringSwitch.isChecked,
                            chatCommandSwitch.isChecked
                        )
                    }.start()
                    Looper.prepare()
                    Snackbar.make(v, R.string.success, Snackbar.LENGTH_LONG).show()
                    Looper.loop()
                }
            })
        }
    }

    private fun showPrivacyDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.privacy_reminder_title)
        builder.setMessage(R.string.privacy_reminder_information)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.agree) { dialog: DialogInterface?, which: Int ->
            sharedPreferences!!.edit().putBoolean("privacy_dialog_agree", true).apply()
        }
        builder.setNegativeButton(R.string.decline, null)
        builder.setNeutralButton(R.string.visit_page) { dialog: DialogInterface?, which: Int ->
            val uri = Uri.parse("https://get.telegram-sms.com$privacyPolice")
            val privacy_builder = CustomTabsIntent.Builder()
            privacy_builder.setToolbarColor(
                ContextCompat.getColor(applicationContext, R.color.colorPrimary)
            )
            val customTabsIntent = privacy_builder.build()
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                customTabsIntent.launchUrl(applicationContext, uri)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                Snackbar.make(
                    findViewById(R.id.bot_token_editview),
                    "Browser not found.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isAllCaps = false
    }

    override fun onResume() {
        super.onResume()
        val backStatus = setPermissionBack
        setPermissionBack = false
        if (backStatus) {
            if (isNotifyListener(applicationContext)) {
                startActivity(Intent(this@MainActivity, NotifyAppsListActivity::class.java))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.")
                    Snackbar.make(
                        findViewById(R.id.bot_token_editview),
                        R.string.no_camera_permission,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(applicationContext, ScannerActivity::class.java)
                startActivityForResult(intent, 1)
            }

            1 -> {
                val displayDualSimDisplayName =
                    findViewById<SwitchMaterial>(R.id.display_dual_sim_switch)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        val tm = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                        if (tm.phoneCount <= 1 || getActiveCard(applicationContext) < 2) {
                            displayDualSimDisplayName.isEnabled = false
                            displayDualSimDisplayName.isChecked = false
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val setProxyItem = menu.findItem(R.id.set_proxy_menu_item)
            setProxyItem.setVisible(false)
        }
        return true
    }

    @SuppressLint("NonConstantResourceId")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val appContext = applicationContext
        val inflater = this.layoutInflater
        var fileName: String? = null
        val lang = appContext.getString(R.string.Lang)
        when (item.itemId) {
            R.id.about_menu_item -> {
                showAboutScreen()
                return true
            }

            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
                return true
            }

            R.id.logcat_menu_item -> {
                val logcatIntent = Intent(this, LogcatActivity::class.java)
                startActivity(logcatIntent)
                return true
            }

            R.id.config_qrcode_menu_item -> {
                if (sharedPreferences!!.getBoolean("initialized", false)) {
                    startActivity(Intent(this, QrCodeShowActivity::class.java))
                } else {
                    Snackbar.make(
                        findViewById(R.id.bot_token_editview),
                        "Uninitialized.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return true
            }

            R.id.set_notify_menu_item -> {
                if (!isNotifyListener(appContext)) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    setPermissionBack = true
                    return false
                }
                startActivity(Intent(this, NotifyAppsListActivity::class.java))
                return true
            }

            R.id.spam_sms_keyword_menu_item -> {
                startActivity(Intent(this, SpamListActivity::class.java))
                return true
            }

            R.id.set_proxy_menu_item -> {
                val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
                showProxySettingsDialog(
                    inflater,
                    this,
                    appContext,
                    sharedPreferences!!,
                    onOkCallback = { isChecked: Boolean ->
                        if (!dohSwitch.isChecked) {
                            dohSwitch.isChecked = true
                        }
                        dohSwitch.isEnabled = !isChecked
                    }
                )
                return true
            }

            R.id.user_manual_menu_item -> fileName = "/guide/$lang/user-manual"
            R.id.privacy_policy_menu_item -> fileName = privacyPolice
            R.id.question_and_answer_menu_item -> fileName = "/guide/$lang/Q&A"
        }
        if (fileName == null) return false

        val uri = Uri.parse("https://get.telegram-sms.com$fileName")
        val builder = CustomTabsIntent.Builder()
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(appContext, R.color.colorPrimary))
            .build()
        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Snackbar.make(
                findViewById(R.id.bot_token_editview),
                "Browser not found.",
                Snackbar.LENGTH_LONG
            ).show()
        }
        return true
    }

    private fun showAboutScreen() {
        val appContext = applicationContext
        val packageManager = appContext.packageManager
        val versionName = try {
            packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "unknown"
        }
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.about_title)
        builder.setMessage(getString(R.string.about_content) + versionName)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.ok_button, null)
        builder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == Consts.RESULT_CONFIG_JSON) {
                val jsonConfig = JsonParser.parseString(
                        data!!.getStringExtra("config_json")!! // TODO
                ).asJsonObject
                (findViewById<View>(R.id.bot_token_editview) as EditText).setText(
                    jsonConfig["bot_token"].asString
                )
                (findViewById<View>(R.id.chat_id_editview) as EditText).setText(
                    jsonConfig["chat_id"].asString
                )
                (findViewById<View>(R.id.battery_monitoring_switch) as SwitchMaterial).isChecked =
                    jsonConfig["battery_monitoring_switch"].asBoolean
                (findViewById<View>(R.id.verification_code_switch) as SwitchMaterial).isChecked =
                    jsonConfig["verification_code"].asBoolean
                val chargerStatus = findViewById<SwitchMaterial>(R.id.charger_status_switch)
                if (jsonConfig["battery_monitoring_switch"].asBoolean) {
                    chargerStatus.isChecked = jsonConfig["charger_status"].asBoolean
                    chargerStatus.visibility = View.VISIBLE
                } else {
                    chargerStatus.isChecked = false
                    chargerStatus.visibility = View.GONE
                }
                val chatCommand = findViewById<SwitchMaterial>(R.id.chat_command_switch)
                chatCommand.isChecked = jsonConfig["chat_command"].asBoolean
                val privacyModeSwitch = findViewById<SwitchMaterial>(R.id.privacy_switch)
                privacyModeSwitch.isChecked = jsonConfig["privacy_mode"].asBoolean
                setPrivacyModeCheckbox(
                    jsonConfig["chat_id"].asString,
                    chatCommand,
                    privacyModeSwitch
                )
                val trustedPhoneNumber =
                    findViewById<EditText>(R.id.trusted_phone_number_editview)
                trustedPhoneNumber.setText(jsonConfig["trusted_phone_number"].asString)
                val fallbackSms = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
                fallbackSms.isChecked = jsonConfig["fallback_sms"].asBoolean
                if (trustedPhoneNumber.length() != 0) {
                    fallbackSms.visibility = View.VISIBLE
                } else {
                    fallbackSms.visibility = View.GONE
                    fallbackSms.isChecked = false
                }
            }
        }
    }

    companion object {
        private var setPermissionBack = false
        private const val TAG = "main_activity"
        private fun checkVersionUpgrade(context: Context, resetLog: Boolean) {
            val versionCode = SYSTEM_BOOK.tryRead("version_code", 0)
            val packageManager = context.packageManager
            val packageInfo: PackageInfo
            val currentVersionCode: Int
            try {
                packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                currentVersionCode = packageInfo.versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                return
            }
            if (versionCode != currentVersionCode) {
                if (resetLog) {
                    LogUtils.resetLogFile(context)
                }
                SYSTEM_BOOK.write("version_code", currentVersionCode)
            }
        }

        private fun updateConfig() {
            val storeVersion = SYSTEM_BOOK.tryRead("version", 0)
            if (storeVersion == Consts.SYSTEM_CONFIG_VERSION) {
                UpdateVersion1().checkError()
                return
            }
            when (storeVersion) {
                0 -> UpdateVersion1().update()
                else -> Log.i(TAG, "update_config: Can't find a version that can be updated")
            }
        }

        private fun setPrivacyModeCheckbox(
            chatId: String,
            chatCommand: SwitchMaterial,
            privacyModeSwitch: SwitchMaterial
        ) {
            if (!chatCommand.isChecked) {
                privacyModeSwitch.visibility = View.GONE
                privacyModeSwitch.isChecked = false
                return
            }
            if (parseStringToLong(chatId) < 0) {
                privacyModeSwitch.visibility = View.VISIBLE
            } else {
                privacyModeSwitch.visibility = View.GONE
                privacyModeSwitch.isChecked = false
            }
        }
    }
}
