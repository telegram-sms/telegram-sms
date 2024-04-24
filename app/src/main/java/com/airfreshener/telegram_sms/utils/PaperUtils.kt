package com.airfreshener.telegram_sms.utils

import android.content.Context
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import io.paperdb.Book
import io.paperdb.Paper

object PaperUtils {

    val DEFAULT_BOOK: Book
        get() = getDefaultBook()

    val SYSTEM_BOOK: Book
        get() = getSystemBook()

    fun init(context: Context): Unit = Paper.init(context)

    fun getSystemBook(): Book = Paper.book("system_config")

    fun getDefaultBook(): Book = Paper.book()

    fun getSendTempBook(): Book = Paper.book("send_temp")

    fun getProxyConfig():ProxyConfigV2 =
        getSystemBook().tryRead("proxy_config", ProxyConfigV2())

    inline fun <reified T> Book.tryRead(key: String, def: T): T = read<T>(key) ?: def
}
