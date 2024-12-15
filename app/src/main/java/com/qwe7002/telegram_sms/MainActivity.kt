@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.GitHubRelease
import com.qwe7002.telegram_sms.data_structure.PollingBody
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_sms.static_class.Network.getUrl
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard
import com.qwe7002.telegram_sms.static_class.Other.parseStringToLong
import com.qwe7002.telegram_sms.static_class.Service.isNotifyListener
import com.qwe7002.telegram_sms.static_class.Service.startService
import com.qwe7002.telegram_sms.static_class.Service.stopAllService
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects
import java.util.concurrent.TimeUnit

@Suppress("deprecation")
class MainActivity : AppCompatActivity() {
    private val TAG = "main_activity"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var privacyPolice: String
    private lateinit var context: Context

    private fun checkVersionUpgrade(writeLog: Boolean) {
        val versionCode =
            Paper.book("system_config").read("version_code", 0)!!
        val packageManager = context.packageManager
        val packageInfo: PackageInfo
        val currentVersionCode: Int
        try {
            packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            currentVersionCode = packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "checkVersionUpgrade: $e")
            return
        }
        if (versionCode != currentVersionCode) {
            if (writeLog) {
                Logs.resetLogFile(context)
            }
            Paper.book("system_config").write("version_code", currentVersionCode)
        }
    }


    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: " + R.string.TPL_received_sms)
        setContentView(R.layout.activity_main)
        context = applicationContext
        //load config
        Paper.init(context)
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE)
        privacyPolice = "/guide/" + context.getString(R.string.Lang) + "/privacy-policy"

        val chatIdEditView = findViewById<EditText>(R.id.chat_id_editview)
        val botTokenEditView = findViewById<EditText>(R.id.bot_token_editview)
        val messageThreadIdEditView = findViewById<EditText>(R.id.message_thread_id_editview)
        val messageThreadIdView = findViewById<TextInputLayout>(R.id.message_thread_id_view)
        val trustedPhoneNumberEditView = findViewById<EditText>(R.id.trusted_phone_number_editview)
        val chatCommandSwitch = findViewById<SwitchMaterial>(R.id.chat_command_switch)
        val fallbackSmsSwitch = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
        val batteryMonitoringSwitch = findViewById<SwitchMaterial>(R.id.battery_monitoring_switch)
        val chargerStatusSwitch = findViewById<SwitchMaterial>(R.id.charger_status_switch)
        val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
        val verificationCodeSwitch = findViewById<SwitchMaterial>(R.id.verification_code_switch)
        val privacyModeSwitch = findViewById<SwitchMaterial>(R.id.privacy_switch)
        val dualSimDisplayNameSwitch = findViewById<SwitchMaterial>(R.id.display_dual_sim_switch)
        val saveButton = findViewById<Button>(R.id.save_button)
        val getIdButton = findViewById<Button>(R.id.get_id_button)


        if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog()
        }

        val botTokenSave = sharedPreferences.getString("bot_token", "")!!
        val chatIdSave = sharedPreferences.getString("chat_id", "")!!
        val messageThreadIdSave = sharedPreferences.getString("message_thread_id", "")!!

        if (parseStringToLong(chatIdSave) < 0) {
            privacyModeSwitch.visibility = View.VISIBLE
            messageThreadIdView.visibility = View.VISIBLE
        } else {
            privacyModeSwitch.visibility = View.GONE
            messageThreadIdView.visibility = View.GONE
        }

        if (sharedPreferences.getBoolean("initialized", false)) {
            checkVersionUpgrade(true)
            startService(
                context,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
            ReSendJob.startJob(context)
            KeepAliveJob.startJob(context)
        }
        var dualSimDisplayNameConfig =
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (getActiveCard(context) < 2) {
                dualSimDisplayNameSwitch.isEnabled = false
                dualSimDisplayNameConfig = false
            }
            dualSimDisplayNameSwitch.isChecked = dualSimDisplayNameConfig
        }

        botTokenEditView.setText(botTokenSave)
        chatIdEditView.setText(chatIdSave)
        messageThreadIdEditView.setText(messageThreadIdSave)
        trustedPhoneNumberEditView.setText(sharedPreferences.getString("trusted_phone_number", ""))
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
            } else {
                chargerStatusSwitch.visibility = View.GONE
                chargerStatusSwitch.isChecked = false
            }
        }

        fallbackSmsSwitch.isChecked = sharedPreferences.getBoolean("fallback_sms", false)
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

        chatCommandSwitch.isChecked = sharedPreferences.getBoolean("chat_command", false)
        chatCommandSwitch.setOnClickListener {
            privacyModeCheckbox(
                chatIdEditView.text.toString(),
                chatCommandSwitch,
                privacyModeSwitch,
                messageThreadIdView
            )
        }
        verificationCodeSwitch.isChecked = sharedPreferences.getBoolean("verification_code", false)

        dohSwitch.isChecked = sharedPreferences.getBoolean("doh_switch", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dohSwitch.isEnabled =
                !Paper.book("system_config").read("proxy_config", proxy())!!.enable
        }

        privacyModeSwitch.isChecked = sharedPreferences.getBoolean("privacy_mode", false)

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val tm = checkNotNull(getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                if (tm.phoneCount <= 1) {
                    dualSimDisplayNameSwitch.visibility = View.GONE
                }
            }
        }
        dualSimDisplayNameSwitch.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                dualSimDisplayNameSwitch.isChecked = false
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    1
                )
            } else {
                if (getActiveCard(context) < 2) {
                    dualSimDisplayNameSwitch.isEnabled = false
                    dualSimDisplayNameSwitch.isChecked = false
                }
            }
        }


        chatIdEditView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //ignore
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                privacyModeCheckbox(
                    chatIdEditView.text.toString(),
                    chatCommandSwitch,
                    privacyModeSwitch,
                    messageThreadIdView
                )
            }

            override fun afterTextChanged(s: Editable) {
                //ignore
            }
        })


        getIdButton.setOnClickListener { v: View ->
            if (botTokenEditView.text.toString().isEmpty()) {
                Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Thread { stopAllService(context) }
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
                dohSwitch.isChecked,
                Paper.book("system_config").read("proxy_config", proxy())
            )
            okhttpClient = okhttpClient.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val requestBody = PollingBody()
            requestBody.timeout = 60
            val body: RequestBody = RequestBody.create(constValue.JSON, Gson().toJson(requestBody))
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
                    Log.d(TAG, "onFailure: $e")
                    progressDialog.cancel()
                    val message = errorHead + e.message
                    Logs.writeLog(context, message)
                    Looper.prepare()
                    Snackbar.make(v, message, Snackbar.LENGTH_LONG).show()
                    Looper.loop()
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    checkNotNull(response.body)
                    if (response.code != 200) {
                        val result =
                            Objects.requireNonNull(response.body).string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"].asString
                        Logs.writeLog(context, errorMessage)

                        Looper.prepare()
                        Snackbar.make(v, errorMessage, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
                        return
                    }

                    val result =
                        Objects.requireNonNull(response.body).string()
                    Log.d(TAG, "onResponse: $result")
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    val chatList = resultObj.getAsJsonArray("result")
                    if (chatList.isEmpty) {
                        Looper.prepare()
                        Snackbar.make(v, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
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
                    this@MainActivity.runOnUiThread {
                        AlertDialog.Builder(v.context)
                            .setTitle(R.string.select_chat).setItems(
                                chatNameList.toTypedArray<String>()
                            ) { _: DialogInterface?, i: Int ->
                                chatIdEditView.setText(
                                    chatIdList[i]
                                )
                                messageThreadIdEditView.setText(chatTopicIdList[i])
                            }.setPositiveButton(context.getString(R.string.cancel_button), null)
                            .show()
                    }
                }
            })
        }
        saveButton.setOnClickListener { v: View? ->
            if (botTokenEditView.text.toString().isEmpty() || chatIdEditView.text.toString()
                    .isEmpty()
            ) {
                Snackbar.make(v!!, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            if (fallbackSmsSwitch.isChecked && trustedPhoneNumberEditView.text.toString()
                    .isEmpty()
            ) {
                Snackbar.make(v!!, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!sharedPreferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                            Uri.parse("package:$packageName")
                        )
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

            val requestUri = getUrl(
                botTokenEditView.text.toString().trim { it <= ' ' }, "sendMessage"
            )
            val requestBody = RequestMessage()
            requestBody.chatId = chatIdEditView.text.toString().trim { it <= ' ' }
            requestBody.messageThreadId = messageThreadIdEditView.text.toString().trim { it <= ' ' }
            requestBody.text = """
                ${getString(R.string.system_message_head)}
                ${getString(R.string.success_connect)}
                """.trimIndent()
            val gson = Gson()
            val requestBodyRaw = gson.toJson(requestBody)
            val body: RequestBody = RequestBody.create(constValue.JSON, requestBodyRaw)
            val okhttpObj = getOkhttpObj(
                dohSwitch.isChecked,
                Paper.book("system_config").read("proxy_config", proxy())
            )
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpObj.newCall(request)
            val errorHead = "Send message failed: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "onFailure: $e")
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    Logs.writeLog(context, errorMessage)
                    Looper.prepare()
                    Snackbar.make(v!!, errorMessage, Snackbar.LENGTH_LONG)
                        .show()
                    Looper.loop()
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    val newBotToken = botTokenEditView.text.toString().trim { it <= ' ' }
                    if (response.code != 200) {
                        checkNotNull(response.body)
                        val result =
                            Objects.requireNonNull(response.body).string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"]
                        Logs.writeLog(context, errorMessage)
                        Looper.prepare()
                        Snackbar.make(v!!, errorMessage, Snackbar.LENGTH_LONG).show()
                        Looper.loop()
                        return
                    }
                    if (newBotToken != botTokenSave) {
                        Log.i(
                            TAG,
                            "onResponse: The current bot token does not match the saved bot token, clearing the message database."
                        )
                        Paper.book().destroy()
                    }
                    Paper.book("system_config")
                        .write("version", constValue.SYSTEM_CONFIG_VERSION)
                    checkVersionUpgrade(false)
                    val editor = sharedPreferences.edit().clear()
                    editor.putString("bot_token", newBotToken)
                    editor.putString("chat_id", chatIdEditView.text.toString().trim { it <= ' ' })
                    editor.putString(
                        "message_thread_id",
                        messageThreadIdEditView.text.toString().trim { it <= ' ' })
                    if (trustedPhoneNumberEditView.text.toString().trim { it <= ' ' }
                            .isNotEmpty()) {
                        editor.putString(
                            "trusted_phone_number",
                            trustedPhoneNumberEditView.text.toString().trim { it <= ' ' })
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
                        dualSimDisplayNameSwitch.isChecked
                    )
                    editor.putBoolean("verification_code", verificationCodeSwitch.isChecked)
                    editor.putBoolean("doh_switch", dohSwitch.isChecked)
                    editor.putBoolean("privacy_mode", privacyModeSwitch.isChecked)
                    editor.putBoolean("initialized", true)
                    editor.putBoolean("privacy_dialog_agree", true)
                    editor.apply()
                    Thread {
                        ReSendJob.stopJob(context)
                        KeepAliveJob.stopJob(context)
                        stopAllService(context)
                        startService(
                            context,
                            batteryMonitoringSwitch.isChecked,
                            chatCommandSwitch.isChecked
                        )
                        ReSendJob.startJob(context)
                        KeepAliveJob.startJob(context)
                    }.start()
                    Looper.prepare()
                    Snackbar.make(v!!, R.string.success, Snackbar.LENGTH_LONG)
                        .show()
                    Looper.loop()
                }
            })
        }
    }

    private fun privacyModeCheckbox(
        chatId: String,
        chatCommand: SwitchMaterial,
        privacyModeSwitch: SwitchMaterial,
        messageTopicIdView: TextInputLayout
    ) {
        if (!chatCommand.isChecked) {
            messageTopicIdView.visibility = View.GONE
            privacyModeSwitch.visibility = View.GONE
            privacyModeSwitch.isChecked = false
            return
        }
        if (parseStringToLong(chatId) < 0) {
            messageTopicIdView.visibility = View.VISIBLE
            privacyModeSwitch.visibility = View.VISIBLE
        } else {
            messageTopicIdView.visibility = View.GONE
            privacyModeSwitch.visibility = View.GONE
            privacyModeSwitch.isChecked = false
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
            sharedPreferences.edit().putBoolean("privacy_dialog_agree", true).apply()
        }
        builder.setNegativeButton(R.string.decline, null)
        builder.setNeutralButton(R.string.visit_page) { _: DialogInterface?, _: Int ->
            val uri = Uri.parse(
                "https://get.telegram-sms.com$privacyPolice"
            )
            val privacyBuilder = CustomTabsIntent.Builder()
            privacyBuilder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
            val customTabsIntent = privacyBuilder.build()
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                customTabsIntent.launchUrl(context, uri)
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "showPrivacyDialog: $e")
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
            if (isNotifyListener(context)) {
                startActivity(Intent(this@MainActivity, NotifyActivity::class.java))
            }
        }
        val lastCheck = checkNotNull(Paper.book("update").read("last_check", 0L))
        if (lastCheck == 0L) {
            Paper.book("update").write("last_check", System.currentTimeMillis())
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.")
                    Snackbar.make(
                        findViewById(R.id.bot_token_editview),
                        R.string.no_camera_permission,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(context, ScannerActivity::class.java)
                startActivityForResult(intent, 1)
            }

            1 -> {
                val dualSimDisplayName = findViewById<SwitchMaterial>(R.id.display_dual_sim_switch)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        val tm =
                            checkNotNull(getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                        if (tm.phoneCount <= 1 || getActiveCard(
                                context
                            ) < 2
                        ) {
                            dualSimDisplayName.isEnabled = false
                            dualSimDisplayName.isChecked = false
                        }
                    }
                }
            }
        }
    }

    private fun checkUpdate() {
        var versionName = "unknown"
        val packageManager = context.packageManager
        val packageInfo: PackageInfo
        try {
            packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "onOptionsItemSelected: $e")
        }
        if (versionName == "unknown" || versionName == "Debug" || versionName.startsWith("nightly")) {
            return
        }
        Paper.book("update").write("last_check", System.currentTimeMillis())
        val progressDialog = ProgressDialog(this@MainActivity)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.setTitle(getString(R.string.connect_wait_title))
        progressDialog.setMessage(getString(R.string.connect_wait_message))
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.show()
        val okhttpObj = getOkhttpObj(false, proxy())
        val requestUri = String.format(
            "https://api.github.com/repos/telegram-sms/%s/releases/latest",
            context.getString(R.string.app_identifier)
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
                    Logs.writeLog(context, errorMessage)
                }
                val jsonString = response.body.string()
                Log.d(TAG, "onResponse: $jsonString")
                val gson = Gson()
                val release = gson.fromJson(jsonString, GitHubRelease::class.java)
                if (release.tagName != versionName) {
                    runOnUiThread {
                        showUpdateDialog(
                            release.tagName,
                            release.body,
                            release.assets[0].browserDownloadUrl
                        )
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "onFailure: $e")
                progressDialog.cancel()
                val errorMessage = errorHead + e.message
                Logs.writeLog(context, errorMessage)
                Looper.prepare()
                Snackbar.make(findViewById(R.id.content), errorMessage, Snackbar.LENGTH_LONG)
                    .show()
                Looper.loop()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            val setProxyItem = menu.findItem(R.id.set_proxy_menu_item)
            setProxyItem.setVisible(false)
        }
        return true
    }

    @SuppressLint("NonConstantResourceId", "SetTextI18n")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val inflater = this.layoutInflater
        var fileName: String? = null
        when (item.itemId) {
            R.id.check_update_menu_item -> {
                checkUpdate()
                return true
            }

            R.id.about_menu_item -> {
                val packageManager = context.packageManager
                val packageInfo: PackageInfo
                var versionName: String? = "unknown"
                try {
                    packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                    versionName = packageInfo.versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d(TAG, "onOptionsItemSelected: $e")
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
                if (sharedPreferences.getBoolean("initialized", false)) {
                    startActivity(Intent(this, QrcodeActivity::class.java))
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
                if (!isNotifyListener(context)) {
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
                val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
                val proxyEnable = view.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
                val proxyDohSocks5 = view.findViewById<SwitchMaterial>(R.id.doh_over_socks5_switch)
                val proxyHost = view.findViewById<EditText>(R.id.proxy_host_editview)
                val proxyPort = view.findViewById<EditText>(R.id.proxy_port_editview)
                val proxyUsername = view.findViewById<EditText>(R.id.proxy_username_editview)
                val proxyPassword = view.findViewById<EditText>(R.id.proxy_password_editview)
                val proxyItem = Paper.book("system_config").read("proxy_config", proxy())
                checkNotNull(proxyItem)
                proxyEnable.isChecked = proxyItem.enable
                proxyDohSocks5.isChecked = proxyItem.dns_over_socks5
                proxyHost.setText(proxyItem.host)
                proxyPort.setText(proxyItem.port.toString())
                proxyUsername.setText(proxyItem.username)
                proxyPassword.setText(proxyItem.password)
                AlertDialog.Builder(this).setTitle(R.string.proxy_dialog_title)
                    .setView(view)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        if (!dohSwitch.isChecked) {
                            dohSwitch.isChecked = true
                        }
                        dohSwitch.isEnabled = !proxyEnable.isChecked
                        proxyItem.enable = proxyEnable.isChecked
                        proxyItem.dns_over_socks5 = proxyDohSocks5.isChecked
                        proxyItem.host = proxyHost.text.toString()
                        proxyItem.port = proxyPort.text.toString().toInt()
                        proxyItem.username = proxyUsername.text.toString()
                        proxyItem.password = proxyPassword.text.toString()
                        Paper.book("system_config").write("proxy_config", proxyItem)
                        Thread {
                            stopAllService(context)
                            if (sharedPreferences.getBoolean("initialized", false)) {
                                startService(
                                    context,
                                    sharedPreferences.getBoolean(
                                        "battery_monitoring_switch",
                                        false
                                    ),
                                    sharedPreferences.getBoolean("chat_command", false)
                                )
                            }
                        }.start()
                    }
                    .show()
                return true
            }

            R.id.template_menu_item -> {
                startActivity(Intent(this, TemplateActivity::class.java))
                return true
            }

            R.id.user_manual_menu_item -> fileName =
                "/guide/" + context.getString(R.string.Lang) + "/user-manual"

            R.id.privacy_policy_menu_item -> fileName = privacyPolice
            R.id.question_and_answer_menu_item -> fileName =
                "/guide/" + context.getString(R.string.Lang) + "/Q&A"

            R.id.donate_menu_item -> fileName = "/donate"
        }
        checkNotNull(fileName)
        val uri = Uri.parse("https://get.telegram-sms.com$fileName")
        val builder = CustomTabsIntent.Builder()
        val params = CustomTabColorSchemeParams.Builder().setToolbarColor(
            ContextCompat.getColor(
                context, R.color.colorPrimary
            )
        ).build()
        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "onOptionsItemSelected: $e")
            Snackbar.make(
                findViewById(R.id.bot_token_editview),
                "Browser not found.",
                Snackbar.LENGTH_LONG
            ).show()
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == constValue.RESULT_CONFIG_JSON) {
                //JsonObject jsonConfig = JsonParser.parseString(Objects.requireNonNull(data.getStringExtra("config_json"))).getAsJsonObject();
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
                val privacyModeSwitch = findViewById<SwitchMaterial>(R.id.privacy_switch)
                privacyModeSwitch.isChecked = jsonConfig.privacyMode
                val messageThreadIdView = findViewById<TextInputLayout>(R.id.message_thread_id_view)

                privacyModeCheckbox(
                    jsonConfig.chatId,
                    chatCommand,
                    privacyModeSwitch,
                    messageThreadIdView
                )

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
            }
        }
    }

    private fun showUpdateDialog(newVersion: String, updateContent: String, fileURL: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.update_dialog_title)
        val message = String.format(
            getString(R.string.update_dialog_body),
            newVersion,
            updateContent
        )

        builder.setMessage(message)
            .setPositiveButton(R.string.update_dialog_ok) { _: DialogInterface?, _: Int ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileURL))
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


    companion object {
        private var setPermissionBack = false
    }
}

