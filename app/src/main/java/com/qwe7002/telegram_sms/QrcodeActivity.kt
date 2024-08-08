package com.qwe7002.telegram_sms

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_sms.data_structure.ScannerJson

class QrcodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        val config = ScannerJson(
            sharedPreferences.getString("bot_token", "")!!,
            sharedPreferences.getString("chat_id", "")!!,
            sharedPreferences.getString("trusted_phone_number", "")!!,
            sharedPreferences.getBoolean("battery_monitoring_switch", false),
            sharedPreferences.getBoolean("charger_status", false),
            sharedPreferences.getBoolean("chat_command", false),
            sharedPreferences.getBoolean("fallback_sms", false),
            sharedPreferences.getBoolean("privacy_mode", false),
            sharedPreferences.getBoolean("verification_code", false)
        )
        val qrCodeImageview = findViewById<ImageView>(R.id.qr_imageview)
        qrCodeImageview.setImageBitmap(
            AwesomeQrRenderer().genQRcodeBitmap(
                Gson().toJson(config),
                ErrorCorrectionLevel.H,
                1024,
                1024
            )
        )
    }
}
   
