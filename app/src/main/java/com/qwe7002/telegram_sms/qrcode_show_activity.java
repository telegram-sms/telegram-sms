package com.qwe7002.telegram_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.sumimakito.awesomeqr.AwesomeQrRenderer;
import com.google.gson.Gson;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.QRCode;

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
        ImageView qr_image = findViewById(R.id.qr_image_imageview);
        qr_image.setImageBitmap(gen_qrcode_bitmap(new Gson().toJson(config)));

    }

    private Bitmap gen_qrcode_bitmap(String content) {
        AwesomeQrRenderer renderer = new AwesomeQrRenderer();
        ByteMatrix byte_matrix;
        try {
            QRCode qrcode = renderer.getProtoQrCode(content, ErrorCorrectionLevel.Q);
            byte_matrix = qrcode.getMatrix();
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
        BitMatrix bitMatrix = renderer.convertByteMatrixToBitMatrix(byte_matrix);
        int height = bitMatrix.getHeight();
        int width = bitMatrix.getWidth();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return Bitmap.createScaledBitmap(bmp, 600, 600, false);
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
