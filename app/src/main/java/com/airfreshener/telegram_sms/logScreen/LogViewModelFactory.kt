package com.airfreshener.telegram_sms.logScreen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airfreshener.telegram_sms.utils.ContextUtils.app

class LogViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LogViewModel(
            logRepository = appContext.app().logRepository,
        ) as T
    }

}
