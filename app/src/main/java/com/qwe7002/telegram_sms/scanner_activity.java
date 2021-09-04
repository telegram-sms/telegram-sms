package com.qwe7002.telegram_sms;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_sms.value.const_value;


public class scanner_activity extends Activity {

    private CodeScanner mCodeScanner;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scanner);
        Toolbar toolbar = findViewById(R.id.scan_toolbar);
        toolbar.setTitle(R.string.scan_title);
        toolbar.setTitleTextColor(Color.WHITE);
        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        mCodeScanner = new CodeScanner(this, scannerView);
        mCodeScanner.setDecodeCallback(result -> runOnUiThread(() -> {
            String TAG = "activity_scanner";
            Log.d(TAG, "format: " + result.getBarcodeFormat().toString() + " content: " + result.getText());
            if (!json_validate(result.getText())) {
                Toast.makeText(scanner_activity.this, "The QR code is not legal", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent().putExtra("config_json", result.getText());
            setResult(const_value.RESULT_CONFIG_JSON, intent);
            finish();
        }));
        scannerView.setOnClickListener(view -> mCodeScanner.startPreview());
    }


    @Override
    public void onResume() {
        super.onResume();
        mCodeScanner.startPreview();
    }

    @Override
    public void onPause() {
        mCodeScanner.releaseResources();
        super.onPause();
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
