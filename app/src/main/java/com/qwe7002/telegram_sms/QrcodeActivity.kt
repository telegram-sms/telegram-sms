@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_sms

import AES
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


class QrcodeActivity : AppCompatActivity() {
    lateinit var okhttpObject: okhttp3.OkHttpClient
    lateinit var sharedPreferences: android.content.SharedPreferences
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
            //finish()
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
            sharedPreferences.getBoolean("verification_code", false),
            sharedPreferences.getString("message_thread_id", "")!!,
            sendList
        )
        return Gson().toJson(config)
    }

    @Suppress("DEPRECATION")
    private fun sendConfig() {
        showInputDialog(this, "Please enter your password") { userInput ->
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle("Sending configuration")
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            Thread {
                val encryptConfig = AES.encrypt(getConfigJson(), AES.getKeyFromString(userInput))
                val requestBody = Gson().toJson(mapOf("encrypt" to encryptConfig)).toRequestBody()
                val requestObj =
                    Request.Builder().url("https://cf-kv-storage.qwe7002-dev.workers.dev/config")
                        .method("PUT", requestBody)
                val call = okhttpObject.newCall(requestObj.build())
                try {
                    val response = call.execute()
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        Log.d("networkProgressHandle", "sendConfig: $responseBody")
                        val jsonObject = JsonParser.parseString(responseBody)
                            .asJsonObject
                        runOnUiThread{
                            AlertDialog.Builder(this)
                                .setTitle("Success")
                                .setMessage(
                                    "Configuration sent successfully, ID: " + jsonObject.get("key").asString
                                )
                                .setPositiveButton("OK") { _, _ -> }
                                .show()
                        }
                    } else {
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle("Error")
                                .setMessage("An error occurred while getting the configuration: " + response.code)
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

    @Suppress("DEPRECATION")
    private fun getConfig() {
        showGetDialog(this, "Please enter your info") { id, password ->
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle("Getting configuration")
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            Thread {
                val httpUrlBuilder: HttpUrl.Builder =
                    "https://cf-kv-storage.qwe7002-dev.workers.dev/config".toHttpUrlOrNull()!!
                        .newBuilder()
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
                            setResult(constValue.RESULT_CONFIG_JSON, intent)
                            finish()
                        } catch (e: Exception) {
                            Logs.writeLog(
                                applicationContext,
                                "An error occurred while resending: " + e.message
                            )
                            runOnUiThread{
                                AlertDialog.Builder(this)
                                    .setTitle("Error")
                                    .setMessage("An error occurred while decrypting the configuration")
                                    .setPositiveButton("OK") { _, _ -> getConfig()}
                                    .show()

                            }
                            e.printStackTrace()
                        }
                    } else {
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle("Error")
                                .setMessage("An error occurred while getting the configuration: " + response.code)
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

    @Suppress("SameParameterValue")
    private fun showInputDialog(context: Context, title: String, callback: (String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        val dialogView = layoutInflater.inflate(R.layout.set_config_layout, null)
        builder.setView(dialogView)
        dialogView.findViewById<View>(R.id.config_id_layout).visibility = View.GONE
        val passwordInput = dialogView.findViewById<EditText>(R.id.config_password_editview)
        // Set up the buttons
        builder.setPositiveButton("OK") { dialog, _ ->
            val userInput = passwordInput.text.toString()
            callback(userInput)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    @Suppress("SameParameterValue")
    private fun showGetDialog(context: Context, title: String, callback: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        val dialogView = layoutInflater.inflate(R.layout.set_config_layout, null)
        builder.setView(dialogView)
        val idInput = dialogView.findViewById<EditText>(R.id.config_id_editview)
        val passwordInput = dialogView.findViewById<EditText>(R.id.config_password_editview)
        // Set up the buttons
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
                val id = idInput.text.toString()
                val password = passwordInput.text.toString()
                if (id.isEmpty()) {
                    idInput.error = "ID cannot be empty"
                } else if (password.isEmpty()) {
                    passwordInput.error = "Password cannot be empty"
                } else if (id.length != 9) {
                    idInput.error = "ID must be 9 characters"

                } else {
                    callback(id, password)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
   
