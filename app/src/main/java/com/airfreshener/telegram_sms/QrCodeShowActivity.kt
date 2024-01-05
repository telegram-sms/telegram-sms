package com.airfreshener.telegram_sms;

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
        config_list config = new config_list();
        config.bot_token = sharedPreferences.getString("bot_token", "");
        config.chat_id = sharedPreferences.getString("chat_id", "");
        config.trusted_phone_number = sharedPreferences.getString("trusted_phone_number", "");
        config.fallback_sms = sharedPreferences.getBoolean("fallback_sms", false);
        config.chat_command = sharedPreferences.getBoolean("chat_command", false);
        config.battery_monitoring_switch = sharedPreferences.getBoolean("battery_monitoring_switch", false);
        config.charger_status = sharedPreferences.getBoolean("charger_status", false);
        config.verification_code = sharedPreferences.getBoolean("verification_code", false);
        config.privacy_mode = sharedPreferences.getBoolean("privacy_mode", false);
        ImageView qr_image_imageview = findViewById(R.id.qr_imageview);
        qr_image_imageview.setImageBitmap(new AwesomeQrRenderer().genQRcodeBitmap(new Gson().toJson(config), ErrorCorrectionLevel.H, 1024, 1024));
    }


    private static class config_list {
        String bot_token = "";
        String chat_id = "";
        String trusted_phone_number = "";
        boolean fallback_sms = false;
        boolean chat_command = false;
        boolean battery_monitoring_switch = false;
        boolean charger_status = false;
        boolean verification_code = false;
        boolean privacy_mode = false;
    }
}
