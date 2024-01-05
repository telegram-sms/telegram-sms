package com.airfreshener.telegram_sms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.airfreshener.telegram_sms.utils.Consts
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result

class ScannerActivity : Activity() {

    private var mCodeScanner: CodeScanner? = null

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_scanner)
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        val codeScanner = CodeScanner(this, scannerView).apply { mCodeScanner = this }
        codeScanner.formats = mutableListOf(BarcodeFormat.QR_CODE)
        codeScanner.decodeCallback = DecodeCallback { result: Result ->
            runOnUiThread {
                Log.d(
                    TAG,
                    "format: " + result.barcodeFormat.toString() + " content: " + result.text
                )
                if (!jsonValidate(result.text)) {
                    Toast.makeText(
                        this@ScannerActivity,
                        "The QR code is not legal",
                        Toast.LENGTH_SHORT
                    ).show()
                    codeScanner.startPreview()
                    return@runOnUiThread
                }
                val intent = Intent().putExtra("config_json", result.text)
                setResult(Consts.RESULT_CONFIG_JSON, intent)
                finish()
            }
        }
        scannerView.setOnClickListener { view: View? -> codeScanner.startPreview() }
    }

    public override fun onResume() {
        super.onResume()
        mCodeScanner?.startPreview()
    }

    public override fun onPause() {
        mCodeScanner?.releaseResources()
        super.onPause()
    }

    private fun jsonValidate(jsonStr: String?): Boolean = try {
        JsonParser.parseString(jsonStr)?.isJsonObject ?: false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    companion object {
        private const val TAG = "activity_scanner"
    }
}
