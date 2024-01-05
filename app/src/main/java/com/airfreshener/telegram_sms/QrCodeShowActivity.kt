package com.airfreshener.telegram_sms

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QrCodeShowActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        val config = ConfigList(
            bot_token = sharedPreferences.getString("bot_token", ""),
            chat_id = sharedPreferences.getString("chat_id", ""),
            trusted_phone_number = sharedPreferences.getString("trusted_phone_number", ""),
            fallback_sms = sharedPreferences.getBoolean("fallback_sms", false),
            chat_command = sharedPreferences.getBoolean("chat_command", false),
            battery_monitoring_switch = sharedPreferences.getBoolean("battery_monitoring_switch", false),
            charger_status = sharedPreferences.getBoolean("charger_status", false),
            verification_code = sharedPreferences.getBoolean("verification_code", false),
            privacy_mode = sharedPreferences.getBoolean("privacy_mode", false),
        )
        val qrImageImageview = findViewById<ImageView>(R.id.qr_imageview)
        qrImageImageview.setImageBitmap(
            AwesomeQrRenderer().genQRcodeBitmap(Gson().toJson(config),
                ErrorCorrectionLevel.H,
                1024,
                1024
            )
        )
    }

    @Suppress("unused")
    private class ConfigList(
        val bot_token: String?,
        val chat_id: String?,
        val trusted_phone_number: String?,
        val fallback_sms: Boolean,
        val chat_command: Boolean,
        val battery_monitoring_switch: Boolean,
        val charger_status: Boolean,
        val verification_code: Boolean,
        val privacy_mode: Boolean,
    )
}
