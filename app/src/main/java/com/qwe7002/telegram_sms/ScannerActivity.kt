package com.qwe7002.telegram_sms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.qwe7002.telegram_sms.value.Const

class ScannerActivity : Activity() {
    private lateinit var mCodeScanner: CodeScanner
    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_scanner)
        // Enable edge-to-edge using WindowCompat
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        mCodeScanner = CodeScanner(this, scannerView)
        mCodeScanner.formats = object : ArrayList<BarcodeFormat?>() {
            init {
                add(BarcodeFormat.QR_CODE)
            }
        }
        mCodeScanner.decodeCallback = DecodeCallback { result: Result ->
            runOnUiThread {
                Log.d(
                    this::class.simpleName,
                    "format: " + result.barcodeFormat.toString() + " content: " + result.text
                )
                if (!jsonValidate(result.text)) {
                    Toast.makeText(
                        this@ScannerActivity,
                        "The QR code is not legal",
                        Toast.LENGTH_SHORT
                    ).show()
                    mCodeScanner.startPreview()
                    return@runOnUiThread
                }
                val intent = Intent().putExtra("config_json", result.text)
                setResult(Const.RESULT_CONFIG_JSON, intent)
                finish()
            }
        }
        scannerView.setOnClickListener { mCodeScanner.startPreview() }
    }


    public override fun onResume() {
        super.onResume()
        mCodeScanner.startPreview()
    }

    public override fun onPause() {
        super.onPause()
        mCodeScanner.releaseResources()
    }

    private fun jsonValidate(jsonStr: String): Boolean {
        val jsonElement: JsonElement?
        try {
            jsonElement = JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        if (jsonElement == null) {
            return false
        }
        return jsonElement.isJsonObject
    }
}
