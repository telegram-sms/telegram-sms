package com.qwe7002.telegram_sms

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.static_class.Crypto
import com.qwe7002.telegram_sms.static_class.Network
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.text.TextWatcher
import android.text.Editable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.qwe7002.telegram_sms.MMKV.CARBON_COPY_ID
import com.qwe7002.telegram_sms.value.JSON
import com.qwe7002.telegram_sms.value.RESULT_CONFIG_JSON
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Thrown when the config relay server replies with a non-200 status, so the
 * coroutine can surface the HTTP code to the user without crashing.
 */
private class HttpStatusException(val code: Int) : Exception("HTTP status $code")

class TransferConfigActivity : AppCompatActivity() {
    private val logTag = "${TAG}.TransferConfigActivity"
    lateinit var okhttpObject: okhttp3.OkHttpClient
    lateinit var preferences: android.content.SharedPreferences
    val url = "https://api.telegram-sms.com/config"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.qrcode_container)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        preferences = MMKV.defaultMMKV()
        okhttpObject = Network.getOkhttpObj(
            preferences.getBoolean("doh_switch", true)
        )
        if (preferences.getBoolean("initialized", false)) {
            val qrCodeImageview = findViewById<ImageView>(R.id.qr_imageview)
            qrCodeImageview.setImageBitmap(
                AwesomeQrRenderer().genQRcodeBitmap(
                    getConfigJson(),
                    ErrorCorrectionLevel.H,
                    1024,
                    1024
                )
            )
        } else {
            findViewById<View>(R.id.qr_layout).visibility = View.GONE
            getConfig()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.qrcode_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.send_config_menu_item -> {
                sendConfig()
                true
            }

            R.id.receive_config_menu_item -> {
                getConfig()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getConfigJson(): String {
        val carbonCopyMMKV = MMKV.mmkvWithID(CARBON_COPY_ID)
        // Must match the key used by CcActivity / CcSendJob ("service"); the old
        // "CC_service_list" key never existed, so CC services were silently dropped.
        val serviceListJson = carbonCopyMMKV.getString("service", "[]")
        val gson = Gson()
        val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
        val sendList: ArrayList<CcSendService> = gson.fromJson(serviceListJson, type)
        val config = ScannerJson(
            preferences.getString("bot_token", "")!!,
            preferences.getString("api_address","api.telegram.org")!!,
            preferences.getString("chat_id", "")!!,
            preferences.getString("trusted_phone_number", "")!!,
            preferences.getBoolean("battery_monitoring_switch", false),
            preferences.getBoolean("charger_status", false),
            preferences.getBoolean("chat_command", false),
            preferences.getBoolean("fallback_sms", false),
            preferences.getBoolean("privacy_mode", false),
            preferences.getBoolean("verification_code", false),
            preferences.getBoolean("call_notify", false),
            preferences.getString("message_thread_id", "")!!,
            sendList,
            preferences.getBoolean("hide_phone_number", false),
            preferences.getBoolean("doh_switch", true)
        )
        return gson.toJson(config)
    }

    private fun sendConfig() {
        showSendDialog(this, getString(R.string.please_enter_your_password)) { password ->
            val progressDialog = buildProgressDialog(
                getString(R.string.sending_configuration),
                getString(R.string.connect_wait_message)
            )
            progressDialog.show()
            lifecycleScope.launch {
                try {
                    val key = withContext(Dispatchers.IO) {
                        val encryptConfig =
                            Crypto.encrypt(getConfigJson(), Crypto.getKeyFromString(password))
                        val requestBody =
                            Gson().toJson(mapOf("encrypt" to encryptConfig)).toRequestBody(JSON)
                        val request = Request.Builder().url(url).put(requestBody).build()
                        okhttpObject.newCall(request).execute().use { response ->
                            if (response.code != 200) {
                                throw HttpStatusException(response.code)
                            }
                            val responseBody = response.body.string()
                            Log.d(logTag, "sendConfig: $responseBody")
                            JsonParser.parseString(responseBody).asJsonObject
                                .get("key").asString
                        }
                    }
                    copyKeyToClipboard(applicationContext, key)
                    if (!isFinishing && !isDestroyed) {
                        AlertDialog.Builder(this@TransferConfigActivity)
                            .setTitle(R.string.success)
                            .setMessage(getString(R.string.configuration_sent_successfully) + key)
                            .setPositiveButton(R.string.ok_button, null)
                            .show()
                    }
                } catch (e: HttpStatusException) {
                    showErrorDialog(getString(R.string.an_error_occurred_while_getting_the_configuration) + e.code)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(logTag, "An error occurred while sending configuration: ${e.message}", e)
                    showErrorDialog(getString(R.string.an_error_occurred_while_getting_the_configuration) + e.message)
                } finally {
                    // Activity may already be destroyed when a cancelled coroutine
                    // unwinds here; dismissing a detached dialog can throw.
                    runCatching { progressDialog.dismiss() }
                }
            }
        }
    }

    fun copyKeyToClipboard(context: Context, key: String) {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Key", key)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.key_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun getConfig() {
        showGetDialog(this, getString(R.string.please_enter_your_info)) { id, password ->
            val progressDialog = buildProgressDialog(
                getString(R.string.getting_configuration),
                getString(R.string.connect_wait_message)
            )
            progressDialog.show()
            lifecycleScope.launch {
                try {
                    val responseBody = withContext(Dispatchers.IO) {
                        val httpUrl: HttpUrl = url.toHttpUrlOrNull()!!.newBuilder()
                            .addQueryParameter("key", id)
                            .build()
                        val request = Request.Builder().url(httpUrl).get().build()
                        okhttpObject.newCall(request).execute().use { response ->
                            if (response.code != 200) {
                                throw HttpStatusException(response.code)
                            }
                            response.body.string()
                        }
                    }
                    val decryptConfig = withContext(Dispatchers.Default) {
                        Crypto.decrypt(responseBody, Crypto.getKeyFromString(password))
                    }
                    setResult(
                        RESULT_CONFIG_JSON,
                        Intent().putExtra("config_json", decryptConfig)
                    )
                    finish()
                } catch (e: HttpStatusException) {
                    showErrorDialog(getString(R.string.an_error_occurred_while_getting_the_configuration) + e.code)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalArgumentException) {
                    // Crypto.decrypt throws this on a wrong password or corrupt payload.
                    Log.e(logTag, "An error occurred while decrypting configuration: ${e.message}", e)
                    showErrorDialog(getString(R.string.an_error_occurred_while_decrypting_the_configuration)) { getConfig() }
                } catch (e: Exception) {
                    Log.e(logTag, "An error occurred while getting configuration: ${e.message}", e)
                    showErrorDialog(getString(R.string.an_error_occurred_while_getting_the_configuration) + e.message)
                } finally {
                    // Activity may already be destroyed when a cancelled coroutine
                    // unwinds here; dismissing a detached dialog can throw.
                    runCatching { progressDialog.dismiss() }
                }
            }
        }
    }

    /**
     * Builds a non-cancelable indeterminate progress dialog using a Material
     * [CircularProgressIndicator] (replaces the deprecated ProgressDialog).
     */
    private fun buildProgressDialog(title: String, message: String): AlertDialog {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, padding, padding, padding)
            addView(
                CircularProgressIndicator(this@TransferConfigActivity).apply {
                    isIndeterminate = true
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
            )
        }
        return AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setCancelable(false)
            .create()
    }

    private fun showErrorDialog(message: String, onPositive: () -> Unit = {}) {
        if (isFinishing || isDestroyed) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok_button) { _, _ -> onPositive() }
            .show()
    }

    @SuppressLint("CutPasteId")
    @Suppress("SameParameterValue")
    private fun showSendDialog(context: Context, title: String, callback: (String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        val dialogView = layoutInflater.inflate(R.layout.set_config_layout, null)
        builder.setView(dialogView)
        dialogView.findViewById<View>(R.id.config_id_layout).visibility = View.GONE
        val passwordInput = dialogView.findViewById<EditText>(R.id.config_password_editview)
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.config_password_layout)
        builder.setPositiveButton(R.string.ok_button) { _, _ ->
            // This will be overridden in setOnShowListener
        }
        builder.setNegativeButton(R.string.cancel_button) { dialog, _ ->
            dialog.cancel()
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    passwordLayout.error = getString(R.string.error_password_cannot_be_empty)
                } else if(password.length < 6){
                    passwordLayout.error = getString(R.string.error_password_must_be_6_characters)
                }else {
                    callback(password)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    @Suppress("SameParameterValue")
    private fun showGetDialog(context: Context, title: String, callback: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        val dialogView = layoutInflater.inflate(R.layout.set_config_layout, null)
        builder.setView(dialogView)
        val idInput = dialogView.findViewById<EditText>(R.id.config_id_editview)
        val passwordInput = dialogView.findViewById<EditText>(R.id.config_password_editview)

        builder.setPositiveButton("OK", null)
        builder.setNegativeButton("Cancel") { dialog, _ ->
            if (!preferences.getBoolean("initialized", false)) {
                finish()
            }
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val idView = dialogView.findViewById<TextInputLayout>(R.id.config_id_layout)
                val passwordView = dialogView.findViewById<TextInputLayout>(R.id.config_password_layout)
                val id = idInput.text.toString()
                val password = passwordInput.text.toString()

                // Clear previous errors
                idView.error = null
                passwordView.error = null

                var isValid = true

                if (id.isEmpty()) {
                    idView.error = getString(R.string.error_id_cannot_be_empty)
                    isValid = false
                } else if (id.length != 9) {
                    idView.error = getString(R.string.error_id_must_be_9_characters)
                    isValid = false
                }

                if (password.isEmpty()) {
                    passwordView.error = getString(R.string.error_password_cannot_be_empty)
                    isValid = false
                }

                if (isValid) {
                    button.isEnabled = false // Prevent multiple clicks
                    callback(id, password)
                    dialog.dismiss()
                }
            }

            // Clear errors when user starts typing
            idInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    dialogView.findViewById<TextInputLayout>(R.id.config_id_layout).error = null
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            passwordInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    dialogView.findViewById<TextInputLayout>(R.id.config_password_layout).error = null
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
        dialog.show()
    }
}
