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

    val SEND_TEMP_BOOK: Book
        get() = getSendTempBook()

    @JvmStatic
    fun init(context: Context): Unit = Paper.init(context)

    @JvmStatic
    fun getSystemBook(): Book = Paper.book("system_config")

    @JvmStatic
    fun getDefaultBook(): Book = Paper.book()

    @JvmStatic
    fun getSendTempBook(): Book = Paper.book("send_temp")

    @JvmStatic
    fun getProxyConfig():ProxyConfigV2 =
        getSystemBook().tryRead("proxy_config", ProxyConfigV2())

    inline fun <reified T> Book.tryRead(key: String, def: T): T = read<T>(key) ?: def
}
