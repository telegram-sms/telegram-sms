package com.qwe7002.telegram_sms

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import com.qwe7002.telegram_sms.static_class.AES
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
        val qrCodeImageview = findViewById<ImageView>(R.id.qr_imageview)
        qrCodeImageview.setImageBitmap(
            AwesomeQrRenderer().genQRcodeBitmap(
                getConfigJson(),
                ErrorCorrectionLevel.H,
                1024,
                1024
            )
        )
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
                val requestObj = Request.Builder().url("https://").method("PUT", requestBody)
                val call = okhttpObject.newCall(requestObj.build())
                try {
                    val response = call.execute()
                    if (response.code == 200) {
                        Log.d("networkProgressHandle", "sendConfig: " + response.body.string())
                    } else {
                        Logs.writeLog(
                            applicationContext,
                            "Send message failed: " + response.code
                        )
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
        showGetDialog(this, "Please enter your password") { id, password ->
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle("Getting configuration")
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            Thread {
                val httpUrlBuilder: HttpUrl.Builder = "https://".toHttpUrlOrNull()!!.newBuilder()
                httpUrlBuilder.addQueryParameter("key", id)
                val httpUrl = httpUrlBuilder.build()
                val requestObj = Request.Builder().url(httpUrl).method("GET", null)
                val call = okhttpObject.newCall(requestObj.build())
                try {
                    val response = call.execute()
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        val decryptConfig =
                            AES.decrypt(responseBody, AES.getKeyFromString(password))
                        val intent = Intent().putExtra("config_json", decryptConfig)
                        setResult(constValue.RESULT_CONFIG_JSON, intent)
                        finish()
                    } else {
                        Logs.writeLog(
                            applicationContext,
                            "Send message failed: " + response.code
                        )
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
        // Set up the input
        val input = EditText(context)
        builder.setView(input)
        // Set up the buttons
        builder.setPositiveButton("OK") { dialog, _ ->
            val userInput = input.text.toString()
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
        // Set up the input fields
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        val idInput = EditText(context)
        idInput.hint = "ID"
        layout.addView(idInput)

        val passwordInput = EditText(context)
        passwordInput.hint = "Password"
        passwordInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(passwordInput)
        builder.setView(layout)

        // Set up the buttons
        builder.setPositiveButton("OK") { dialog, _ ->
            val id = idInput.text.toString()
            val password = passwordInput.text.toString()
            callback(id, password)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
}
   
