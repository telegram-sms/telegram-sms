package com.airfreshener.telegram_sms.logScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airfreshener.telegram_sms.common.data.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogViewModel(
    private val logRepository: LogRepository,
) : ViewModel() {

    private val _logs: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        viewModelScope.launch { logRepository.logs.collect { _logs.value = it } }
    }

}
