package com.qwe7002.telegram_sms

import android.app.Application
import com.tencent.mmkv.MMKV

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }
}
