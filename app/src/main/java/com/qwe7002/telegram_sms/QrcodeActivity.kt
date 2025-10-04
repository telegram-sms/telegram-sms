@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.static_class.AES
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.value.Const
import io.paperdb.Paper
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


class QrcodeActivity : AppCompatActivity() {
    lateinit var okhttpObject: okhttp3.OkHttpClient
    lateinit var sharedPreferences: android.content.SharedPreferences
    val url = "https://api.telegram-sms.com/config"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        sharedPreferences =
            applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        okhttpObject = Network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        if (sharedPreferences.getBoolean("initialized", false)) {
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
        val serviceListJson =
            Paper.book("system_config").read("CC_service_list", "[]").toString()
        val gson = Gson()
        val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
        val sendList: ArrayList<CcSendService> = gson.fromJson(serviceListJson, type)
        val config = ScannerJson(
            sharedPreferences.getString("bot_token", "")!!,
            sharedPreferences.getString("chat_id", "")!!,
            sharedPreferences.getString("trusted_phone_number", "")!!,
            sharedPreferences.getBoolean("battery_monitoring_switch", false),
            sharedPreferences.getBoolean("charger_status", false),
            sharedPreferences.getBoolean("chat_command", false),
            sharedPreferences.getBoolean("fallback_sms", false),
            sharedPreferences.getBoolean("privacy_mode", false),
            sharedPreferences.getBoolean("call_notify", false),
            sharedPreferences.getBoolean("display_dual_sim_display_name", false),
            sharedPreferences.getBoolean("verification_code", false),
            sharedPreferences.getString("message_thread_id", "")!!,
            sendList
        )
        return Gson().toJson(config)
    }

    @Suppress("DEPRECATION")
    private fun sendConfig() {
        showSendDialog(this, getString(R.string.please_enter_your_password)) { userInput ->
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.sending_configuration))
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            Thread {
                val encryptConfig = AES.encrypt(getConfigJson(), AES.getKeyFromString(userInput))
                val requestBody = Gson().toJson(mapOf("encrypt" to encryptConfig)).toRequestBody()
                val requestObj =
                    Request.Builder().url(url)
                        .method("PUT", requestBody)
                val call = okhttpObject.newCall(requestObj.build())
                try {
                    val response = call.execute()
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        Log.d("networkProgressHandle", "sendConfig: $responseBody")
                        val jsonObject = JsonParser.parseString(responseBody)
                            .asJsonObject
                        val key = jsonObject.get(
                            "key"
                        ).asString
                        runOnUiThread {
                            copyKeyToClipboard(applicationContext, key)
                            AlertDialog.Builder(this)
                                .setTitle(R.string.success)
                                .setMessage(
                                    getString(R.string.configuration_sent_successfully) + key
                                )
                                .setPositiveButton("OK") { _, _ -> }
                                .show()

                        }
                    } else {
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.error_title)
                                .setMessage(getString(R.string.an_error_occurred_while_getting_the_configuration) + response.code)
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                        }
                    }
                } catch (e: IOException) {
                    Logs.writeLog(
                        applicationContext,
                        "An error occurred while resending: " + e.message
                    )
                    e.printStackTrace()
                } finally {
                    progressDialog.dismiss()
                }
            }.start()
        }
    }

    fun copyKeyToClipboard(context: Context, key: String) {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Key", key)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun getConfig() {
        showGetDialog(this, getString(R.string.please_enter_your_info)) { id, password ->
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.getting_configuration))
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            Thread {
                val httpUrlBuilder: HttpUrl.Builder = url.toHttpUrlOrNull()!!.newBuilder()
                httpUrlBuilder.addQueryParameter("key", id)
                val httpUrl = httpUrlBuilder.build()
                val requestObj = Request.Builder().url(httpUrl).method("GET", null)
                val call = okhttpObject.newCall(requestObj.build())
                try {
                    val response = call.execute()
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        try {
                            val decryptConfig =
                                AES.decrypt(responseBody, AES.getKeyFromString(password))
                            val intent = Intent().putExtra("config_json", decryptConfig)
                            setResult(Const.RESULT_CONFIG_JSON, intent)
                            finish()
                        } catch (e: Exception) {
                            Logs.writeLog(
                                applicationContext,
                                "An error occurred while resending: " + e.message
                            )
                            runOnUiThread {
                                AlertDialog.Builder(this)
                                    .setTitle(R.string.error_title)
                                    .setMessage(getString(R.string.an_error_occurred_while_decrypting_the_configuration))
                                    .setPositiveButton("OK") { _, _ -> getConfig() }
                                    .show()

                            }
                            e.printStackTrace()
                        }
                    } else {
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.error_title)
                                .setMessage(getString(R.string.an_error_occurred_while_getting_the_configuration) + response.code)
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                        }
                    }
                } catch (e: IOException) {
                    Logs.writeLog(
                        applicationContext,
                        "An error occurred while resending: " + e.message
                    )
                    e.printStackTrace()
                } finally {
                    progressDialog.dismiss()
                }
            }.start()
        }
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
        builder.setPositiveButton("OK") { dialog, _ ->
            val userInput = passwordInput.text.toString()
            callback(userInput)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    dialogView.findViewById<TextInputLayout>(R.id.config_password_layout).error = getString(R.string.error_password_cannot_be_empty)
                } else if(password.length < 6){
                    dialogView.findViewById<TextInputLayout>(R.id.config_password_layout).error = getString(R.string.error_password_must_be_6_characters)
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
            if (!sharedPreferences.getBoolean("initialized", false)) {
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
                if (id.isEmpty()) {
                    idView.error = getString(R.string.error_id_cannot_be_empty)
                } else if (password.isEmpty()) {
                    passwordView.error = getString(R.string.error_password_cannot_be_empty)
                } else if (id.length != 9) {
                    idView.error = getString(R.string.error_id_must_be_9_characters)
                } else {
                    callback(id, password)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
   
