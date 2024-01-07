package com.airfreshener.telegram_sms.mainScreen

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.airfreshener.telegram_sms.R

class MainViewModel(
    private val appContext: Context
) : ViewModel() {
    private val prefs = appContext.getSharedPreferences("data", AppCompatActivity.MODE_PRIVATE)
    private val privacyPolice = "/guide/" + appContext.getString(R.string.Lang) + "/privacy-policy"

}
