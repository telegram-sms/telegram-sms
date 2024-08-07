package com.qwe7002.telegram_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer;
import com.google.gson.Gson;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class qrcode_show_activity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);
        Context context = getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        configList config = new configList();
        config.botToken = sharedPreferences.getString("bot_token", "");
        config.chatId = sharedPreferences.getString("chat_id", "");
        config.trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", "");
        config.fallbackSMS = sharedPreferences.getBoolean("fallback_sms", false);
        config.chatCommand = sharedPreferences.getBoolean("chat_command", false);
        config.batteryMonitoring = sharedPreferences.getBoolean("battery_monitoring_switch", false);
        config.chargerStatus = sharedPreferences.getBoolean("charger_status", false);
        config.verificationCode = sharedPreferences.getBoolean("verification_code", false);
        config.privacyMode = sharedPreferences.getBoolean("privacy_mode", false);
        ImageView qrCodeImageview = findViewById(R.id.qr_imageview);
        qrCodeImageview.setImageBitmap(new AwesomeQrRenderer().genQRcodeBitmap(new Gson().toJson(config), ErrorCorrectionLevel.H, 1024, 1024));
    }


    private static class configList {
        String botToken = "";
        String chatId = "";
        String trustedPhoneNumber = "";
        boolean fallbackSMS = false;
        boolean chatCommand = false;
        boolean batteryMonitoring = false;
        boolean chargerStatus = false;
        boolean verificationCode = false;
        boolean privacyMode = false;
    }
}
