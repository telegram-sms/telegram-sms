@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.data_structure.GitHubRelease
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.data_structure.telegram.PollingBody
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_sms.static_class.Network.getUrl
import com.qwe7002.telegram_sms.static_class.Other.parseStringToLong
import com.qwe7002.telegram_sms.static_class.Service.isNotifyListener
import com.qwe7002.telegram_sms.static_class.Service.startService
import com.qwe7002.telegram_sms.static_class.Service.stopAllService
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

@Suppress("deprecation")
class MainActivity : AppCompatActivity() {
    private lateinit var preferences: MMKV
    private lateinit var privacyPolice: String
    private val gson = Gson()
    private var setPermissionBack = false

    @SuppressLint("BatteryLife", "UseKtx", "GestureBackNavigation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle window insets for edge-to-edge on ScrollView
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_scroll_view)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<ImageView>(R.id.character_set)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)


        MMKV.initialize(this)
        preferences = MMKV.defaultMMKV()
        val oldSharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
        if (oldSharedPreferences.getBoolean("initialized", false)) {
            preferences.importFromSharedPreferences(oldSharedPreferences)
            oldSharedPreferences.edit().clear().apply()
        }
        privacyPolice = "/privacy-policy"

        val chatIdEditView = findViewById<EditText>(R.id.chat_id_editview)
        val botTokenEditView = findViewById<EditText>(R.id.bot_token_editview)
        val messageThreadIdEditView = findViewById<EditText>(R.id.message_thread_id_editview)
        val messageThreadIdView = findViewById<TextInputLayout>(R.id.message_thread_id_view)
        val trustedPhoneNumberEditView = findViewById<EditText>(R.id.trusted_phone_number_editview)
        val chatCommandSwitch = findViewById<SwitchMaterial>(R.id.chat_command_switch)
        val callNotifySwitch = findViewById<SwitchMaterial>(R.id.call_notify_switch)
        val hidePhoneNumberSwitch = findViewById<SwitchMaterial>(R.id.hide_phone_number_switch)
        val fallbackSmsSwitch = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
        val batteryMonitoringSwitch = findViewById<SwitchMaterial>(R.id.battery_monitoring_switch)
        val chargerStatusSwitch = findViewById<SwitchMaterial>(R.id.charger_status_switch)
        val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
        val verificationCodeSwitch = findViewById<SwitchMaterial>(R.id.verification_code_switch)
        val saveButton = findViewById<Button>(R.id.save_button)
        val getIdButton = findViewById<Button>(R.id.get_id_button)


        if (!preferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog()
        }

        val botTokenSave = preferences.getString("bot_token", "")!!
        val chatIdSave = preferences.getString("chat_id", "")!!
        val messageThreadIdSave = preferences.getString("message_thread_id", "")!!

        if (parseStringToLong(chatIdSave) < 0) {
            messageThreadIdView.visibility = View.VISIBLE
        } else {
            messageThreadIdView.visibility = View.GONE
        }

        if (preferences.getBoolean("initialized", false)) {
            checkVersionUpgrade()
            startService(
                applicationContext,
                preferences.getBoolean("battery_monitoring_switch", false),
                preferences.getBoolean("chat_command", false)
            )
            ReSendJob.startJob(applicationContext)
            KeepAliveJob.startJob(applicationContext)
        }
        botTokenEditView.setText(botTokenSave)
        chatIdEditView.setText(chatIdSave)
        messageThreadIdEditView.setText(messageThreadIdSave)
        trustedPhoneNumberEditView.setText(preferences.getString("trusted_phone_number", ""))
        batteryMonitoringSwitch.isChecked =
            preferences.getBoolean("battery_monitoring_switch", false)
        chargerStatusSwitch.isChecked = preferences.getBoolean("charger_status", false)

        if (!batteryMonitoringSwitch.isChecked) {
            chargerStatusSwitch.isChecked = false
            chargerStatusSwitch.visibility = View.GONE
        }

        batteryMonitoringSwitch.setOnClickListener {
            if (batteryMonitoringSwitch.isChecked) {
                chargerStatusSwitch.visibility = View.VISIBLE
            } else {
                chargerStatusSwitch.visibility = View.GONE
                chargerStatusSwitch.isChecked = false
            }
        }

        callNotifySwitch.isChecked = preferences.getBoolean("call_notify", false)

        hidePhoneNumberSwitch.isChecked = preferences.getBoolean("hide_phone_number", false)

        fallbackSmsSwitch.isChecked = preferences.getBoolean("fallback_sms", false)
        if (trustedPhoneNumberEditView.length() == 0) {
            fallbackSmsSwitch.visibility = View.GONE
            fallbackSmsSwitch.isChecked = false
        }
        trustedPhoneNumberEditView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                //ignore
            }

            override fun afterTextChanged(s: Editable) {
                if (trustedPhoneNumberEditView.length() != 0) {
                    fallbackSmsSwitch.visibility = View.VISIBLE
                    fallbackSmsSwitch.isEnabled = true
                } else {
                    fallbackSmsSwitch.isEnabled = false
                    fallbackSmsSwitch.isChecked = false
                }
            }
        })

        chatCommandSwitch.isChecked = preferences.getBoolean("chat_command", false)
        chatCommandSwitch.setOnClickListener {
            privacyModeCheckbox(
                chatIdEditView.text.toString(),
                messageThreadIdView
            )
        }
        verificationCodeSwitch.isChecked = preferences.getBoolean("verification_code", false)

        dohSwitch.isChecked = preferences.getBoolean("doh_switch", true)


        chatIdEditView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                privacyModeCheckbox(
                    chatIdEditView.text.toString(),
                    messageThreadIdView
                )
            }

            override fun afterTextChanged(s: Editable) {
                //ignore
            }
        })


        getIdButton.setOnClickListener { v: View ->
            if (botTokenEditView.text.toString().isEmpty()) {
                showErrorDialog(getString(R.string.token_not_configure))
                return@setOnClickListener
            }
            Thread { stopAllService(applicationContext) }
                .start()
            val progressDialog = ProgressDialog(this@MainActivity)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.get_recent_chat_title))
            progressDialog.setMessage(getString(R.string.get_recent_chat_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            val requestUri = getUrl(
                botTokenEditView.text.toString().trim { it <= ' ' }, "getUpdates"
            )
            var okhttpClient = getOkhttpObj(
                dohSwitch.isChecked
            )
            okhttpClient = okhttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val requestBody = PollingBody()
            requestBody.timeout = 60
            val body: RequestBody = RequestBody.create(Const.JSON, gson.toJson(requestBody))
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
                    Log.d(this::class.simpleName, "onFailure: $e")
                    progressDialog.cancel()
                    val message = errorHead + e.message
                    Log.e("MainActivity", message)
                    runOnUiThread {
                        showErrorDialog(message)
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    if (response.code != 200) {
                        val result = response.body.string()
                        try {
                            val resultObj = JsonParser.parseString(result).asJsonObject
                            val errorMessage = errorHead + resultObj["description"].asString
                            Log.e("MainActivity", errorMessage)
                            runOnUiThread { showErrorDialog(errorMessage) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val errorMessage = errorHead + "Failed to parse error response"
                            Log.e("MainActivity", errorMessage)
                            runOnUiThread { showErrorDialog(errorMessage) }
                        }
                        return
                    }

                    val result = response.body.string()
                    Log.d(this::class.simpleName, "onResponse: $result")
                    try {
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val chatList = resultObj.getAsJsonArray("result")
                        if (chatList.isEmpty) {
                            runOnUiThread { showErrorDialog(getString(R.string.unable_get_recent)) }
                            return
                        }
                        val chatNameList = ArrayList<String>()
                        val chatIdList = ArrayList<String>()
                        val chatTopicIdList = ArrayList<String>()
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
                                    if (username.isEmpty() && !chatObj.has("username")) {
                                        if (chatObj.has("first_name")) {
                                            username = chatObj["first_name"].asString
                                        }
                                        if (chatObj.has("last_name")) {
                                            username += " " + chatObj["last_name"].asString
                                        }
                                    }
                                    val type = chatObj["type"].asString
                                    chatNameList.add("$username($type)")
                                    chatIdList.add(chatObj["id"].asString)
                                    var threadId = ""
                                    if (type == "supergroup" && messageObj.has("is_topic_message")) {
                                        threadId = messageObj["message_thread_id"].asString
                                    }
                                    chatTopicIdList.add(threadId)
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
                                .setTitle(R.string.select_chat).setItems(
                                    chatNameList.toTypedArray<String>()
                                ) { _: DialogInterface?, i: Int ->
                                    chatIdEditView.setText(
                                        chatIdList[i]
                                    )
                                    messageThreadIdEditView.setText(chatTopicIdList[i])
                                }.setPositiveButton(
                                    applicationContext.getString(R.string.cancel_button),
                                    null
                                )
                                .show()
                        }
                    } catch (e: Exception) {
                        val errorMessage = errorHead + "Failed to parse response: ${e.message}"
                        Log.e("MainActivity", errorMessage)
                        runOnUiThread { showErrorDialog(errorMessage) }
                    }
                }
            })
        }
        saveButton.setOnClickListener { v: View? ->
            if (botTokenEditView.text.toString().isEmpty() || chatIdEditView.text.toString()
                    .isEmpty()
            ) {
                showErrorDialog(getString(R.string.chat_id_or_token_not_config))
                return@setOnClickListener
            }
            if (fallbackSmsSwitch.isChecked && trustedPhoneNumberEditView.text.toString()
                    .isEmpty()
            ) {
                showErrorDialog(getString(R.string.trusted_phone_number_empty))
                return@setOnClickListener
            }
            if (!preferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog()
                return@setOnClickListener
            }
            var permissionList = arrayOf<String?>(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionArrayList = ArrayList(
                    listOf(*permissionList)
                )
                permissionArrayList.add(Manifest.permission.POST_NOTIFICATIONS)
                permissionList = permissionArrayList.toTypedArray<String?>()
            }
            ActivityCompat.requestPermissions(
                this@MainActivity,
                permissionList,
                1
            )

            val powerManager =
                checkNotNull(getSystemService(POWER_SERVICE) as PowerManager)
            val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!hasIgnored) {
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
                        "package:$packageName".toUri()
                    )
                if (intent.resolveActivityInfo(
                        packageManager,
                        PackageManager.MATCH_DEFAULT_ONLY
                    ) != null
                ) {
                    startActivity(intent)
                }
            }

            val progressDialog = ProgressDialog(this@MainActivity)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.connect_wait_title))
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()

            if (preferences.contains("initialized") && preferences.getString(
                    "api_address",
                    "api.telegram.org"
                ) != "api.telegram.org"
            ) {
                logout(preferences.getString("bot_token", "").toString())
            }

            val requestUri = getUrl(
                botTokenEditView.text.toString().trim { it <= ' ' },
                "sendMessage"
            )
            val requestBody = RequestMessage()
            requestBody.chatId = chatIdEditView.text.toString().trim { it <= ' ' }
            requestBody.messageThreadId = messageThreadIdEditView.text.toString().trim { it <= ' ' }
            requestBody.text = Template.render(
                applicationContext,
                "TPL_system_message",
                mapOf("Message" to getString(R.string.success_connect))
            )
            val requestBodyRaw = gson.toJson(requestBody)
            val body: RequestBody = RequestBody.create(Const.JSON, requestBodyRaw)
            val okhttpObj = getOkhttpObj(
                dohSwitch.isChecked
            )
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpObj.newCall(request)
            val errorHead = "Send message failed: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(this::class.simpleName, "onFailure: $e")
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    Log.e("MainActivity", errorMessage)
                    runOnUiThread {
                        showErrorDialog(errorMessage)
                    }
                }

                @SuppressLint("UseKtx")
                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    val newBotToken = botTokenEditView.text.toString().trim { it <= ' ' }
                    if (response.code != 200) {
                        val result = response.body.string()
                        try {
                            val resultObj = JsonParser.parseString(result).asJsonObject
                            val errorMessage = errorHead + resultObj["description"]
                            Log.e("MainActivity", errorMessage)
                            runOnUiThread {
                                showErrorDialog(errorMessage)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val errorMessage = errorHead + "Failed to parse error response"
                            Log.e("MainActivity", errorMessage)
                            runOnUiThread { showErrorDialog(errorMessage) }
                        }
                        return
                    }
                    if (newBotToken != botTokenSave) {
                        Log.i(
                            this::class.simpleName,
                            "onResponse: The current bot token does not match the saved bot token, clearing the message database."
                        )
                        MMKV.mmkvWithID(MMKVConst.CHAT_ID).clearAll()
                    }
                    MMKV.mmkvWithID(MMKVConst.RESEND_ID).clearAll()
                    checkVersionUpgrade()
                    preferences.clearAll()
                    preferences.putString("bot_token", newBotToken)
                    preferences.putString(
                        "chat_id",
                        chatIdEditView.text.toString().trim { it <= ' ' })
                    preferences.putString(
                        "message_thread_id",
                        messageThreadIdEditView.text.toString().trim { it <= ' ' })
                    if (trustedPhoneNumberEditView.text.toString().trim { it <= ' ' }
                            .isNotEmpty()) {
                        preferences.putString(
                            "trusted_phone_number",
                            trustedPhoneNumberEditView.text.toString().trim { it <= ' ' })
                    }
                    preferences.putBoolean("fallback_sms", fallbackSmsSwitch.isChecked)
                    preferences.putBoolean("chat_command", chatCommandSwitch.isChecked)
                    preferences.putBoolean(
                        "battery_monitoring_switch",
                        batteryMonitoringSwitch.isChecked
                    )
                    preferences.putBoolean("charger_status", chargerStatusSwitch.isChecked)
                    preferences.putBoolean("verification_code", verificationCodeSwitch.isChecked)
                    preferences.putBoolean("doh_switch", dohSwitch.isChecked)
                    preferences.putBoolean("initialized", true)
                    preferences.putBoolean("privacy_dialog_agree", true)
                    preferences.putBoolean("call_notify", callNotifySwitch.isChecked)
                    preferences.putBoolean("hide_phone_number", hidePhoneNumberSwitch.isChecked)
                    Thread {
                        ReSendJob.stopJob(applicationContext)
                        KeepAliveJob.stopJob(applicationContext)
                        stopAllService(applicationContext)
                        startService(
                            applicationContext,
                            batteryMonitoringSwitch.isChecked,
                            chatCommandSwitch.isChecked
                        )
                        ReSendJob.startJob(applicationContext)
                        KeepAliveJob.startJob(applicationContext)
                    }.start()
                    runOnUiThread {
                        Snackbar.make(v!!, R.string.success, Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            })
        }
    }

    private fun checkVersionUpgrade() {
        val versionCode = preferences.getInt("version_code", 0)
        val packageManager = applicationContext.packageManager
        val packageInfo: PackageInfo
        val currentVersionCode: Int
        try {
            packageInfo = packageManager.getPackageInfo(applicationContext.packageName, 0)
            currentVersionCode = packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(this::class.simpleName, "checkVersionUpgrade: $e")
            return
        }
        if (versionCode != currentVersionCode) {
            preferences.putInt("version_code", currentVersionCode)
        }
    }


    private fun privacyModeCheckbox(
        chatId: String,
        messageTopicIdView: TextInputLayout
    ) {
        if (parseStringToLong(chatId) < 0) {
            messageTopicIdView.visibility = View.VISIBLE
        } else {
            messageTopicIdView.visibility = View.GONE
        }
    }

    private fun showPrivacyDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.privacy_reminder_title)
        builder.setMessage(R.string.privacy_reminder_information)
        builder.setCancelable(false)
        builder.setPositiveButton(
            R.string.agree
        ) { _: DialogInterface?, _: Int ->
            preferences.putBoolean("privacy_dialog_agree", true)
        }
        builder.setNegativeButton(R.string.decline, null)
        builder.setNeutralButton(R.string.visit_page) { _: DialogInterface?, _: Int ->
            val uri = "https://telegram-sms.com$privacyPolice".toUri()
            val privacyBuilder = CustomTabsIntent.Builder()
            privacyBuilder.setToolbarColor(
                ContextCompat.getColor(
                    applicationContext,
                    R.color.colorPrimary
                )
            )
            val customTabsIntent = privacyBuilder.build()
            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                customTabsIntent.launchUrl(applicationContext, uri)
            } catch (e: ActivityNotFoundException) {
                Log.d(this::class.simpleName, "showPrivacyDialog: $e")
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
                startActivity(Intent(this@MainActivity, NotifyActivity::class.java))
            }
        }
        val updateMMKV = MMKV.mmkvWithID(MMKVConst.UPDATE_ID)
        val lastCheck = updateMMKV.getLong("last_check", 0)
        if (lastCheck == 0L) {
            updateMMKV.putLong("last_check", System.currentTimeMillis())
        }
        if (lastCheck + TimeUnit.DAYS.toMillis(15) < System.currentTimeMillis()) {
            checkUpdate()
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
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(
                        this::class.simpleName,
                        "onRequestPermissionsResult: No camera permissions."
                    )
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
        }
    }

    private fun checkUpdate() {
        var versionName = "unknown"
        val packageManager = applicationContext.packageManager
        val packageInfo: PackageInfo
        try {
            packageInfo = packageManager.getPackageInfo(applicationContext.packageName, 0)
            versionName = packageInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(this::class.simpleName, "onOptionsItemSelected: $e")
        }
        if (versionName == "unknown" || versionName == "Debug" || versionName.startsWith("nightly")) {
            showErrorDialog("Debug version can not check update.")
            return
        }
        /*Paper.book("update").write("last_check", System.currentTimeMillis())*/
        val updateMMKV = MMKV.mmkvWithID(MMKVConst.UPDATE_ID)
        updateMMKV.putLong("last_check", System.currentTimeMillis())

        val progressDialog = ProgressDialog(this@MainActivity)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.setTitle(R.string.check_update)
        progressDialog.setMessage(getString(R.string.connect_wait_message))
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.show()
        val okhttpObj = getOkhttpObj(false)
        val requestUri = String.format(
            "https://api.github.com/repos/telegram-sms/%s/releases/latest",
            applicationContext.getString(R.string.app_identifier)
        )
        val request: Request = Request.Builder().url(requestUri).build()
        val call = okhttpObj.newCall(request)
        val errorHead = "Send message failed: "
        call.enqueue(object : Callback {
            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                progressDialog.cancel()
                if (!response.isSuccessful) {
                    val errorMessage = errorHead + response.code
                    Log.e("MainActivity", errorMessage)
                }
                val jsonString = response.body.string()
                Log.d(this::class.simpleName, "onResponse: $jsonString")
                val gson = Gson()
                val release = gson.fromJson(jsonString, GitHubRelease::class.java)
                if (release.tagName != versionName) {
                    runOnUiThread {
                        showUpdateDialog(
                            release.tagName,
                            release.assets[0].browserDownloadUrl
                        )
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d(this::class.simpleName, "onFailure: $e")
                progressDialog.cancel()
                val errorMessage = errorHead + e.message
                Log.e("MainActivity", errorMessage)
                runOnUiThread {
                    showErrorDialog(errorMessage)
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val setProxyItem = menu.findItem(R.id.set_proxy_menu_item)
            setProxyItem.isVisible = false
        }
        return true
    }

    @SuppressLint("NonConstantResourceId", "SetTextI18n", "UseKtx")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val inflater = this.layoutInflater
        var fileName: String? = null
        when (item.itemId) {
            R.id.check_update_menu_item -> {
                checkUpdate()
                return true
            }

            R.id.about_menu_item -> {
                val packageManager = applicationContext.packageManager
                val packageInfo: PackageInfo
                var versionName: String? = "unknown"
                try {
                    packageInfo = packageManager.getPackageInfo(applicationContext.packageName, 0)
                    versionName = packageInfo.versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d(this::class.simpleName, "onOptionsItemSelected: $e")
                }
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.about_title)
                builder.setMessage(getString(R.string.about_content) + versionName)
                builder.setCancelable(false)
                builder.setPositiveButton(R.string.ok_button, null)
                builder.show()
                return true
            }

            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
                return true
            }

            R.id.logcat_menu_item -> {
                val logcatIntent = Intent(this, LogActivity::class.java)
                startActivity(logcatIntent)
                return true
            }

            R.id.config_qrcode_menu_item -> {
                val intent = Intent(this, QrcodeActivity::class.java)
                startActivityForResult(intent, 1)
                return true
            }

            R.id.set_notify_menu_item -> {
                if (!isNotifyListener(applicationContext)) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    setPermissionBack = true
                    return false
                }
                startActivity(Intent(this, NotifyActivity::class.java))
                return true
            }

            R.id.spam_sms_keyword_menu_item -> {
                startActivity(Intent(this, SpamActivity::class.java))
                return true
            }

            R.id.cc_menu_item -> {
                startActivity(Intent(this, CcActivity::class.java))
                return true
            }

            R.id.set_proxy_menu_item -> {
                val view = inflater.inflate(R.layout.set_proxy_layout, null)
                val proxyEnable = view.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
                val proxyHost = view.findViewById<EditText>(R.id.proxy_host_editview)
                val proxyPort = view.findViewById<EditText>(R.id.proxy_port_editview)
                val proxyUsername = view.findViewById<EditText>(R.id.proxy_username_editview)
                val proxyPassword = view.findViewById<EditText>(R.id.proxy_password_editview)
                val proxyMMKV = MMKV.mmkvWithID(MMKVConst.PROXY_ID)
                proxyEnable.isChecked = proxyMMKV.getBoolean("enable", false)
                proxyHost.setText(proxyMMKV.getString("host", ""))
                proxyPort.setText(proxyMMKV.getInt("port", 100).toString())
                proxyUsername.setText(proxyMMKV.getString("username", ""))
                proxyPassword.setText(proxyMMKV.getString("password", ""))
                AlertDialog.Builder(this).setTitle(R.string.proxy_dialog_title)
                    .setView(view)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        proxyMMKV.putBoolean("enable", proxyEnable.isChecked)
                        proxyMMKV.putString("host", proxyHost.text.toString())
                        proxyMMKV.putInt("port", proxyPort.text.toString().toInt())
                        proxyMMKV.putString("username", proxyUsername.text.toString())
                        proxyMMKV.putString("password", proxyPassword.text.toString())
                        Thread {
                            stopAllService(applicationContext)
                            if (preferences.getBoolean("initialized", false)) {
                                startService(
                                    applicationContext,
                                    preferences.getBoolean(
                                        "battery_monitoring_switch",
                                        false
                                    ),
                                    preferences.getBoolean("chat_command", false)
                                )
                            }
                        }.start()
                    }
                    .show()
                return true
            }

            R.id.set_api_menu_item -> {
                val apiDialogView = inflater.inflate(R.layout.set_api_layout, null)
                val apiAddress = apiDialogView.findViewById<EditText>(R.id.api_address_editview)
                apiAddress.setText(preferences.getString("api_address", "api.telegram.org"))
                val apiDialog = AlertDialog.Builder(this)
                apiDialog.setTitle("Set API Address")
                apiDialog.setView(apiDialogView)
                apiDialog.setPositiveButton("OK") { _, _ ->
                    val apiAddressText = apiAddress.text.toString()
                    if (preferences.getString(
                            "api_address",
                            "api.telegram.org"
                        ) == apiAddressText
                    ) {
                        return@setPositiveButton
                    }
                    if (apiAddressText.isEmpty()) {
                        showErrorDialog("API address cannot be empty.")
                        return@setPositiveButton
                    }
                    preferences.putString("api_address", apiAddressText)
                    if (preferences.contains("initialized") && apiAddressText != "api.telegram.org") {
                        logout(preferences.getString("bot_token", "").toString())
                    }
                }
                apiDialog.setNegativeButton("Cancel", null)
                apiDialog.show()
                return true
            }

            R.id.template_menu_item -> {
                startActivity(Intent(this, TemplateActivity::class.java))
                return true
            }

            R.id.user_manual_menu_item -> fileName =
                "/user-manual"

            R.id.privacy_policy_menu_item -> fileName = privacyPolice
            R.id.question_and_answer_menu_item -> fileName =
                "/Q&A"

            R.id.donate_menu_item -> fileName = "/donate"
        }
        checkNotNull(fileName)
        val uri = "https://telegram-sms.com$fileName".toUri()
        val builder = CustomTabsIntent.Builder()
        val params = CustomTabColorSchemeParams.Builder().setToolbarColor(
            ContextCompat.getColor(
                applicationContext, R.color.colorPrimary
            )
        ).build()
        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            Log.d(this::class.simpleName, "onOptionsItemSelected: $e")
            showErrorDialog(getString(R.string.browser_not_found))
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == Const.RESULT_CONFIG_JSON) {
                val gson = Gson()
                val jsonConfig = gson.fromJson(
                    data!!.getStringExtra("config_json"),
                    ScannerJson::class.java
                )
                (findViewById<View>(R.id.bot_token_editview) as EditText).setText(jsonConfig.botToken)
                (findViewById<View>(R.id.chat_id_editview) as EditText).setText(jsonConfig.chatId)
                (findViewById<View>(R.id.battery_monitoring_switch) as SwitchMaterial).isChecked =
                    jsonConfig.batteryMonitoringSwitch
                (findViewById<View>(R.id.verification_code_switch) as SwitchMaterial).isChecked =
                    jsonConfig.verificationCode

                val chargerStatus = findViewById<SwitchMaterial>(R.id.charger_status_switch)
                if (jsonConfig.batteryMonitoringSwitch) {
                    chargerStatus.isChecked = jsonConfig.chargerStatus
                    chargerStatus.visibility = View.VISIBLE
                } else {
                    chargerStatus.isChecked = false
                    chargerStatus.visibility = View.GONE
                }

                val chatCommand = findViewById<SwitchMaterial>(R.id.chat_command_switch)
                chatCommand.isChecked = jsonConfig.chatCommand
                val messageThreadIdView = findViewById<TextInputLayout>(R.id.message_thread_id_view)

                privacyModeCheckbox(
                    jsonConfig.chatId,
                    messageThreadIdView
                )

                val callNotifySwitch = findViewById<SwitchMaterial>(R.id.call_notify_switch)
                callNotifySwitch.isChecked = jsonConfig.callNotifySwitch

                val hidePhoneNumberSwitch = findViewById<SwitchMaterial>(R.id.hide_phone_number_switch)
                hidePhoneNumberSwitch.isChecked = jsonConfig.hidePhoneNumber

                if (jsonConfig.apiAddress.isNotEmpty()) {
                    preferences.putString("api_address", jsonConfig.apiAddress)
                }
                (findViewById<View>(R.id.bot_token_editview) as EditText).setText(jsonConfig.botToken)

                val trustedPhoneNumber = findViewById<EditText>(R.id.trusted_phone_number_editview)
                trustedPhoneNumber.setText(jsonConfig.trustedPhoneNumber)
                val fallbackSms = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
                fallbackSms.isChecked = jsonConfig.fallbackSms
                if (trustedPhoneNumber.length() != 0) {
                    fallbackSms.visibility = View.VISIBLE
                } else {
                    fallbackSms.visibility = View.GONE
                    fallbackSms.isChecked = false
                }
                val topicIdView = findViewById<EditText>(R.id.message_thread_id_editview)
                topicIdView.setText(jsonConfig.topicID)
                if (jsonConfig.ccService != null) {
                    val carbonCopyMMKV = MMKV.mmkvWithID(MMKVConst.CARBON_COPY_ID)
                    carbonCopyMMKV.putString("service", gson.toJson(jsonConfig.ccService))
                }
            }
        }
    }

    private fun showUpdateDialog(newVersion: String, fileURL: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.update_dialog_title)
        val message = String.format(
            getString(R.string.update_dialog_body),
            newVersion
        )

        builder.setMessage(message)
            .setPositiveButton(R.string.update_dialog_ok) { _: DialogInterface?, _: Int ->
                val intent = Intent(Intent.ACTION_VIEW, fileURL.toUri())
                startActivity(intent)
            }
            .setNegativeButton(
                R.string.update_dialog_no
            ) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun logout(chatId: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.setTitle(getString(R.string.get_recent_chat_title))
        progressDialog.setMessage(getString(R.string.get_recent_chat_message))
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.show()
        val requestUri = "https://api.telegram.org/bot$chatId/logout"
        var okhttpClient = getOkhttpObj(
            preferences.getBoolean("doh_switch", false)
        )
        okhttpClient = okhttpClient.newBuilder().build()
        val requestBody = PollingBody()
        val body: RequestBody =
            RequestBody.create(Const.JSON, Gson().toJson(requestBody))
        val request: Request =
            Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                progressDialog.cancel()
                Log.e(this::class.simpleName, "onFailure: ", e)
            }

            override fun onResponse(call: Call, response: Response) {
                progressDialog.cancel()
                if (!response.isSuccessful) {
                    showErrorDialog("Logout failed.")
                } else {
                    val body = response.body.string()
                    val jsonObj = JsonParser.parseString(body).asJsonObject
                    if (jsonObj.get("ok").asBoolean) {
                        Snackbar.make(
                            findViewById(R.id.doh_switch),
                            "Set API address successful.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        showErrorDialog("Set API address failed.")
                    }

                }

            }
        })
    }

}

