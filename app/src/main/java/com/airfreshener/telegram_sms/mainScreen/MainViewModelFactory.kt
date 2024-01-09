package com.airfreshener.telegram_sms.mainScreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airfreshener.telegram_sms.TelegramSmsApp

class MainViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = appContext as TelegramSmsApp
        return MainViewModel(
            appContext = appContext,
            prefsRepository = app.prefsRepository,
        ) as T
    }
}
