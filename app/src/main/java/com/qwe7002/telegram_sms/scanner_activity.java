package com.qwe7002.telegram_sms;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.Result;

import org.jetbrains.annotations.NotNull;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class scanner_activity extends Activity implements ZXingScannerView.ResultHandler {
    private ZXingScannerView scanner_view;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scanner);
        Toolbar toolbar = findViewById(R.id.scan_toolbar);
        toolbar.setTitle(R.string.scan_title);
        toolbar.setTitleTextColor(Color.WHITE);
        ViewGroup contentFrame = findViewById(R.id.content_frame);
        scanner_view = new ZXingScannerView(this);
        contentFrame.addView(scanner_view);
    }


    @Override
    public void onResume() {
        scanner_view.setResultHandler(this);
        scanner_view.startCamera();
        super.onResume();
    }

    @Override
    public void onPause() {
        scanner_view.stopCamera();
        super.onPause();
    }

    @Override
    public void handleResult(@NotNull Result rawResult) {
        String TAG = "activity_scanner";
        Log.d(TAG, "format: " + rawResult.getBarcodeFormat().toString() + " content: " + rawResult.getText());
        if (!json_validate(rawResult.getText())) {
            Intent intent = new Intent().putExtra("bot_token", rawResult.getText());
            setResult(Activity.RESULT_FIRST_USER, intent);
        } else {
            Intent intent = new Intent().putExtra("config_json", rawResult.getText());
            setResult(Activity.RESULT_OK, intent);
        }

        finish();
    }

    boolean json_validate(String jsonStr) {
        JsonElement jsonElement;
        try {
            jsonElement = JsonParser.parseString(jsonStr);
        } catch (Exception e) {
            return false;
        }
        if (jsonElement == null) {
            return false;
        }
        return jsonElement.isJsonObject();
    }

}
