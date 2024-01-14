package com.airfreshener.telegram_sms.utils

fun String?.isNumeric(): Boolean {
    if (this == null) return false
    for (i in indices) {
        println(this[i])
        if (!Character.isDigit(this[i])) {
            return false
        }
    }
    return true
}
