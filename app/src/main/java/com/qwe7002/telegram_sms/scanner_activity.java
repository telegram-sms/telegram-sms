package com.qwe7002.telegram_sms;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class scanner_activity extends Activity implements ZXingScannerView.ResultHandler {
    private ZXingScannerView scanner_view;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.scanner_activity);
        Toolbar toolbar = findViewById(R.id.scan_toolbar);
        toolbar.setTitle(R.string.scan_title);
        toolbar.setTitleTextColor(Color.WHITE);
        ViewGroup contentFrame = findViewById(R.id.content_frame);
        scanner_view = new ZXingScannerView(this);
        contentFrame.addView(scanner_view);
    }


    @Override
    public void onResume() {
        super.onResume();
        scanner_view.setResultHandler(this);
        scanner_view.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        scanner_view.stopCamera();
    }

    @Override
    public void handleResult(Result rawResult) {
        String TAG = "scanner_activity";
        Log.d(TAG, rawResult.getText());
        Log.d(TAG, rawResult.getBarcodeFormat().toString());
        Intent intent = new Intent().putExtra("bot_token", rawResult.getText());
        setResult(Activity.RESULT_OK, intent);
        finish();
    }
}
