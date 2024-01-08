package com.airfreshener.telegram_sms.mainScreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.airfreshener.telegram_sms.LogcatActivity
import com.airfreshener.telegram_sms.QrCodeShowActivity
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.ScannerActivity
import com.airfreshener.telegram_sms.SpamListActivity
import com.airfreshener.telegram_sms.common.PrefsRepository
import com.airfreshener.telegram_sms.common.SharedPrefsRepository
import com.airfreshener.telegram_sms.databinding.ActivityMainBinding
import com.airfreshener.telegram_sms.migration.UpdateVersion1
import com.airfreshener.telegram_sms.model.PollingJson
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.model.Settings
import com.airfreshener.telegram_sms.notification_screen.NotifyAppsListActivity
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils.getActiveCard
import com.airfreshener.telegram_sms.utils.OtherUtils.isReadPhoneStatePermissionGranted
import com.airfreshener.telegram_sms.utils.OtherUtils.parseStringToLong
import com.airfreshener.telegram_sms.utils.OtherUtils.requestReadPhoneStatePermission
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
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    @Suppress("unused") // TODO
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(applicationContext) }

    private val binding by viewBinding(ActivityMainBinding::bind)
    private val prefsRepository: PrefsRepository by lazy {
        SharedPrefsRepository(getSharedPreferences("data", MODE_PRIVATE))
    }
    private val qaUrl: String
        get() = "$WEB_VIEW_PAGES_URL/${applicationContext.getString(R.string.Lang)}/Q&A"
    private val manualUrl: String
        get() = "$WEB_VIEW_PAGES_URL/${applicationContext.getString(R.string.Lang)}/user-manual"
    private val privacyPolice: String
        get() = "$WEB_VIEW_PAGES_URL/${applicationContext.getString(R.string.Lang)}/privacy-policy"

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        PaperUtils.init(appContext)
        lifecycleScope.launch { viewModel.settings.collect { settings -> showSettings(settings) } }
        if (!prefsRepository.getPrivacyDialogAgree()) showPrivacyDialog()
        val settings = prefsRepository.getSettings()
        if (prefsRepository.getInitialized()) {
            updateConfig()
            checkVersionUpgrade(appContext, true)
            startService(appContext, settings.isBatteryMonitoring, settings.isChatCommand)
        }
        setListeners()
    }

    private fun setListeners() {
        val appContext = applicationContext
        binding.dohSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.dnsOverHttpChecked(isChecked)
        }
        binding.privacySwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.privacyModeChanged(isChecked)
        }
        binding.chatCommandSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.chatCommandChanged(isChecked)
        }
        binding.fallbackSmsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.fallbackSmsChanged(isChecked)
        }
        binding.chargerStatusSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.chargerStatusChanged(isChecked)
        }
        binding.batteryMonitoringSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.batteryMonitoringChecked(isChecked)
        }
        binding.displayDualSimSwitch.setOnCheckedChangeListener { _, isChecked ->

            // TODO move to viewModel
            if (isChecked) {
                if (appContext.isReadPhoneStatePermissionGranted().not()) {
                    binding.displayDualSimSwitch.isChecked = false
                    requestReadPhoneStatePermission(PHONE_STATE_PERMISSION_CODE)
                } else {
                    if (getActiveCard(appContext) < 2) {
                        binding.displayDualSimSwitch.isEnabled = false
                        binding.displayDualSimSwitch.isChecked = false
                    }
                }
            }
            viewModel.displayDualSimChanged(binding.displayDualSimSwitch.isChecked)

        }
        binding.verificationCodeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.verificationCodeChecked(isChecked)
        }
        binding.chatIdEditview.doAfterTextChanged { text ->
            viewModel.chatIdChanged(text?.toString().orEmpty())
        }
        binding.botTokenEditview.doAfterTextChanged { text ->
            viewModel.botTokenChanged(text?.toString().orEmpty())
        }
        binding.trustedPhoneNumberEditview.doAfterTextChanged { text: Editable? ->
            viewModel.trustedPhoneNumberChanged(text?.toString().orEmpty())
        }
        binding.getIdButton.setOnClickListener { v: View -> onGetIdClicked(v) }
        binding.saveButton.setOnClickListener { v: View -> onSaveClicked(v) }
    }

    private fun showSettings(settings: Settings) {
        val appContext = applicationContext
        binding.botTokenEditview.setTextKeepState(settings.botToken)
        binding.chatIdEditview.setTextKeepState(settings.chatId)
        binding.trustedPhoneNumberEditview.setTextKeepState(settings.trustedPhoneNumber)
        binding.batteryMonitoringSwitch.isChecked = settings.isBatteryMonitoring
        binding.chargerStatusSwitch.isEnabled = settings.isChargerStatusEnabled
        binding.chargerStatusSwitch.isChecked = settings.isChargerStatusEnabled && settings.isChargerStatus
        binding.fallbackSmsSwitch.isEnabled = settings.isFallbackEnabled
        binding.fallbackSmsSwitch.isChecked = settings.isFallbackEnabled&& settings.isFallbackSms
        binding.chatCommandSwitch.isChecked = settings.isChatCommand
        binding.privacySwitch.isEnabled = settings.isPrivacyModeEnabled
        binding.privacySwitch.isChecked = settings.isPrivacyModeEnabled && settings.isPrivacyMode
        binding.verificationCodeSwitch.isChecked = settings.isVerificationCode
        val isDohEnbled = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || PaperUtils.getProxyConfig().enable.not() // TODO
        binding.dohSwitch.isEnabled = isDohEnbled
        binding.dohSwitch.isChecked = isDohEnbled && settings.isDnsOverHttp
        val isDualCards = appContext.isReadPhoneStatePermissionGranted() && getActiveCard(appContext) > 1 // TODO
        binding.displayDualSimSwitch.isEnabled = isDualCards
        binding.displayDualSimSwitch.isChecked = settings.isDisplayDualSim && isDualCards
    }

    private fun onSaveClicked(view: View) {
        val appContext = view.context.applicationContext
        val botTokenSaved = prefsRepository.getSettings().botToken

        val newSettings = Settings(
            isDnsOverHttp = binding.dohSwitch.isChecked,
            isPrivacyMode = binding.privacySwitch.isChecked,
            isChatCommand = binding.chatCommandSwitch.isChecked,
            isFallbackSms = binding.fallbackSmsSwitch.isChecked,
            isChargerStatus = binding.chargerStatusSwitch.isChecked,
            isBatteryMonitoring = binding.batteryMonitoringSwitch.isChecked,
            isDisplayDualSim = binding.displayDualSimSwitch.isChecked,
            isVerificationCode = binding.verificationCodeSwitch.isChecked,
            chatId = binding.chatIdEditview.text.toString().trim { it <= ' ' },
            botToken = binding.botTokenEditview.text.toString().trim { it <= ' ' },
            trustedPhoneNumber = binding.trustedPhoneNumberEditview.text.toString().trim { it <= ' ' },
        )

        if (newSettings.botToken.isEmpty() || newSettings.chatId.isEmpty()) {
            Snackbar.make(view, R.string.chat_id_or_token_not_config, Snackbar.LENGTH_LONG).show()
            return
        }
        if (newSettings.isFallbackSms && newSettings.trustedPhoneNumber.isEmpty()) {
            Snackbar.make(view, R.string.trusted_phone_number_empty, Snackbar.LENGTH_LONG).show()
            return
        }
        if (!prefsRepository.getPrivacyDialogAgree()) {
            showPrivacyDialog()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, requistingPermissions, COMMON_PERMISSIONS_CODE)
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!hasIgnored) {
                val action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                val intent = Intent(action).setData(Uri.parse("package:$packageName"))
                if (intent.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    startActivity(intent)
                }
            }
        }
        val progressDialog = ProgressDialog(this@MainActivity)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.setTitle(appContext.getString(R.string.connect_wait_title))
        progressDialog.setMessage(appContext.getString(R.string.connect_wait_message))
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.show()


        val requestUri = getUrl(newSettings.botToken, "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chat_id = newSettings.chatId
        requestBody.text = """
                ${appContext.getString(R.string.system_message_head)}
                ${appContext.getString(R.string.success_connect)}
                """.trimIndent()
        val body = requestBody.toRequestBody()
        val okhttpClient = getOkhttpObj(newSettings.isDnsOverHttp)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send message failed: "
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                progressDialog.cancel()
                val errorMessage = errorHead + e.message
                LogUtils.writeLog(appContext, errorMessage)
                Snackbar.make(view, errorMessage, Snackbar.LENGTH_LONG).show()
            }

            override fun onResponse(call: Call, response: Response) {
                progressDialog.cancel()
                if (response.code != 200) {
                    val responseBody = response.body ?: return
                    val result = responseBody.string()
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    val errorMessage = errorHead + resultObj["description"]
                    LogUtils.writeLog(appContext, errorMessage)
                    Snackbar.make(view, errorMessage, Snackbar.LENGTH_LONG).show()
                    return
                }
                if (newSettings.botToken != botTokenSaved) {
                    Log.i(TAG, "onResponse: The current bot token does not match the " +
                            "saved bot token, clearing the message database."
                    )
                    DEFAULT_BOOK.destroy()
                }
                SYSTEM_BOOK.write("version", Consts.SYSTEM_CONFIG_VERSION)
                checkVersionUpgrade(appContext, false)

                prefsRepository.setSettings(newSettings)

                Thread {
                    stopAllService(appContext)
                    startService(appContext, newSettings.isBatteryMonitoring, newSettings.isChatCommand)
                }.start()
                Snackbar.make(view, R.string.success, Snackbar.LENGTH_LONG).show()
            }
        })
    }

    private fun onGetIdClicked(view: View) {
        val appContext = applicationContext
        val botToken = binding.botTokenEditview.text.toString().trim { it <= ' ' }
        val isDnsOverHttp = binding.dohSwitch.isChecked
        if (botToken.isEmpty()) {
            Snackbar.make(view, R.string.token_not_configure, Snackbar.LENGTH_LONG).show()
            return
        }
        Thread { stopAllService(appContext) }.start()
        val progressDialog = ProgressDialog(this@MainActivity)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progressDialog.setTitle(appContext.getString(R.string.get_recent_chat_title))
        progressDialog.setMessage(appContext.getString(R.string.get_recent_chat_message))
        progressDialog.isIndeterminate = false
        progressDialog.setCancelable(false)
        progressDialog.show()
        val requestUri = getUrl(botToken, "getUpdates")
        val okhttpClient =
            getOkhttpObj(isDnsOverHttp)
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
                Snackbar.make(view, errorMessage, Snackbar.LENGTH_LONG).show()
            }

            override fun onResponse(call: Call, response: Response) {
                progressDialog.cancel()
                val responseBody = response.body
                if (response.code != 200 || responseBody == null) {
                    val description = responseBody
                        ?.string()?.let { JsonParser.parseString(it).asJsonObject }
                        ?.get("description")?.asString
                    val errorMessage = errorHead + description
                    LogUtils.writeLog(appContext, errorMessage)
                    Snackbar.make(view, errorMessage, Snackbar.LENGTH_LONG).show()
                    return
                }
                val result = responseBody.string()
                val resultObj = JsonParser.parseString(result).asJsonObject
                val chatList = resultObj.getAsJsonArray("result")
                if (chatList.size() == 0) {
                    Snackbar.make(view, R.string.unable_get_recent, Snackbar.LENGTH_LONG).show()
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
                            chatObj["username"]?.asString?.let { username = it }
                            chatObj["title"]?.asString?.let { username = it }
                            if (username == "" && !chatObj.has("username")) {
                                chatObj["first_name"]?.asString?.let { username = it }
                                chatObj["last_name"]?.asString?.let { username += " $it" }
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
                    AlertDialog.Builder(view.context)
                        .setTitle(R.string.select_chat)
                        .setItems(
                            chatNameList.toTypedArray<String>()
                        ) { _: DialogInterface?, i: Int ->
                            binding.chatIdEditview.setText(chatIdList[i])
                        }
                        .setPositiveButton(appContext.getString(R.string.cancel_button), null)
                        .show()
                }
            }
        })
    }

    private fun showPrivacyDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.privacy_reminder_title)
        builder.setMessage(R.string.privacy_reminder_information)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.agree) { _: DialogInterface?, _: Int ->
            prefsRepository.setPrivacyDialogAgree(true)
        }
        builder.setNeutralButton(R.string.visit_page) { _: DialogInterface?, _: Int ->
            val privacyBuilder = CustomTabsIntent.Builder()
            privacyBuilder.setToolbarColor(
                ContextCompat.getColor(applicationContext, R.color.colorPrimary)
            )
            val customTabsIntent = privacyBuilder.build()
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                customTabsIntent.launchUrl(applicationContext, Uri.parse(privacyPolice))
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                Snackbar.make(
                    binding.botTokenEditview,
                    "Browser not found.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        builder.create().apply {
            getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false
            getButton(AlertDialog.BUTTON_NEUTRAL).isAllCaps = false
        }.show()
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
                        binding.botTokenEditview,
                        R.string.no_camera_permission,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(applicationContext, ScannerActivity::class.java)
                startActivityForResult(intent, 1)
            }

            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        val tm = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                        if (tm.phoneCount <= 1 || getActiveCard(applicationContext) < 2) {
                            binding.displayDualSimSwitch.isEnabled = false
                            binding.displayDualSimSwitch.isChecked = false
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
        var fileName: String? = null
        when (item.itemId) {
            R.id.about_menu_item -> {
                showAboutScreen()
                return true
            }

            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                return true
            }

            R.id.logcat_menu_item -> {
                val logcatIntent = Intent(this, LogcatActivity::class.java)
                startActivity(logcatIntent)
                return true
            }

            R.id.config_qrcode_menu_item -> {
                if (prefsRepository.getInitialized()) {
                    startActivity(Intent(this, QrCodeShowActivity::class.java))
                } else {
                    Snackbar.make(
                        binding.botTokenEditview,
                        "Uninitialized.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return true
            }

            R.id.set_notify_menu_item -> {
                if (!isNotifyListener(appContext)) {
                    val action = android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    val intent = Intent(action)
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
                showProxySettingsDialog(
                    inflater = layoutInflater,
                    activity = this,
                    context = appContext,
                    prefsRepository = prefsRepository,
                    onOkCallback = { isChecked: Boolean ->
                        if (!binding.dohSwitch.isChecked) {
                            binding.dohSwitch.isChecked = true
                        }
                        binding.dohSwitch.isEnabled = !isChecked
                    }
                )
                return true
            }

            R.id.user_manual_menu_item -> fileName = manualUrl
            R.id.privacy_policy_menu_item -> fileName = privacyPolice
            R.id.question_and_answer_menu_item -> fileName = qaUrl
        }
        if (fileName == null) return false

        val builder = CustomTabsIntent.Builder()
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(appContext, R.color.colorPrimary))
            .build()
        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, Uri.parse(fileName))
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Snackbar.make(
                binding.botTokenEditview,
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
        if (data == null) {
            Log.d(TAG, "onActivityResult: data is null")
            return
        }
        if (requestCode == COMMON_PERMISSIONS_CODE) {
            if (resultCode == Consts.RESULT_CONFIG_JSON) {
                val jsonConfig = JsonParser.parseString(
                    data.getStringExtra("config_json")!! // TODO
                ).asJsonObject
                binding.botTokenEditview.setText(jsonConfig["bot_token"].asString)
                binding.chatIdEditview.setText(jsonConfig["chat_id"].asString)
                binding.batteryMonitoringSwitch.isChecked =
                    jsonConfig["battery_monitoring_switch"].asBoolean
                binding.verificationCodeSwitch.isChecked = jsonConfig["verification_code"].asBoolean
                if (jsonConfig["battery_monitoring_switch"].asBoolean) {
                    binding.chargerStatusSwitch.isChecked = jsonConfig["charger_status"].asBoolean
                    binding.chargerStatusSwitch.isEnabled = true
                } else {
                    binding.chargerStatusSwitch.isChecked = false
                    binding.chargerStatusSwitch.isEnabled = false
                }
                binding.chatCommandSwitch.isChecked = jsonConfig["chat_command"].asBoolean
                binding.privacySwitch.isChecked = jsonConfig["privacy_mode"].asBoolean
                setPrivacyModeCheckbox(
                    jsonConfig["chat_id"].asString,
                    binding.chatCommandSwitch.isChecked,
                    binding.privacySwitch
                )
                binding.trustedPhoneNumberEditview.setText(jsonConfig["trusted_phone_number"].asString)
                binding.fallbackSmsSwitch.isChecked = jsonConfig["fallback_sms"].asBoolean
                if (binding.trustedPhoneNumberEditview.length() != 0) {
                    binding.fallbackSmsSwitch.isEnabled = true
                } else {
                    binding.fallbackSmsSwitch.isEnabled = false
                    binding.fallbackSmsSwitch.isChecked = false
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val WEB_VIEW_PAGES_URL = "https://get.telegram-sms.com/guide"
        private const val COMMON_PERMISSIONS_CODE = 1 // ?? TODO
        private const val PHONE_STATE_PERMISSION_CODE = 1 // ?? TODO
        private const val CAMERA_PERMISSION_CODE = 0

        private val requistingPermissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        private var setPermissionBack = false

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
            isChatCommand: Boolean,
            privacyModeSwitch: SwitchMaterial
        ) {
            val isChatValidAndEnabled = isChatCommand && parseStringToLong(chatId) < 0
            privacyModeSwitch.isEnabled = isChatValidAndEnabled
            if (isChatValidAndEnabled.not()) privacyModeSwitch.isChecked = false
        }

    }
}
