package com.airfreshener.telegram_sms

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QrCodeShowActivity : AppCompatActivity() {

    private val prefsRepository by lazy { (application as TelegramSmsApp).prefsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val settings = prefsRepository.getSettings()
        val config = ConfigList(
            bot_token = settings.botToken,
            chat_id = settings.chatId,
            trusted_phone_number = settings.trustedPhoneNumber,
            fallback_sms = settings.isFallbackSms,
            chat_command = settings.isChatCommand,
            battery_monitoring_switch = settings.isBatteryMonitoring,
            charger_status = settings.isChargerStatus,
            verification_code = settings.isVerificationCode,
            privacy_mode = settings.isPrivacyMode,
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

    @Suppress("unused", "PropertyName")
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
