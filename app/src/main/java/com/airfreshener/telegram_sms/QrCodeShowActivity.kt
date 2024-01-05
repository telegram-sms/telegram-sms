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
        val config = ConfigList()
        config.bot_token = sharedPreferences.getString("bot_token", "")
        config.chat_id = sharedPreferences.getString("chat_id", "")
        config.trusted_phone_number = sharedPreferences.getString("trusted_phone_number", "")
        config.fallback_sms = sharedPreferences.getBoolean("fallback_sms", false)
        config.chat_command = sharedPreferences.getBoolean("chat_command", false)
        config.battery_monitoring_switch =
            sharedPreferences.getBoolean("battery_monitoring_switch", false)
        config.charger_status = sharedPreferences.getBoolean("charger_status", false)
        config.verification_code = sharedPreferences.getBoolean("verification_code", false)
        config.privacy_mode = sharedPreferences.getBoolean("privacy_mode", false)
        val qr_image_imageview = findViewById<ImageView>(R.id.qr_imageview)
        qr_image_imageview.setImageBitmap(
            AwesomeQrRenderer().genQRcodeBitmap(Gson().toJson(config),
                ErrorCorrectionLevel.H,
                1024,
                1024
            )
        )
    }

    private class ConfigList {
        var bot_token: String? = ""
        var chat_id: String? = ""
        var trusted_phone_number: String? = ""
        var fallback_sms = false
        var chat_command = false
        var battery_monitoring_switch = false
        var charger_status = false
        var verification_code = false
        var privacy_mode = false
    }
}
