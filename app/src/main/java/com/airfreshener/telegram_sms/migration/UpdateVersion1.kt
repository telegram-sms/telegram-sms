package com.airfreshener.telegram_sms.migration

import android.util.Log
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.migration.ProxyConfigV1
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead

class UpdateVersion1 {

    fun checkError() {
        try {
            PaperUtils.getProxyConfig()
        } catch (e: Exception) {
            e.printStackTrace()
            SYSTEM_BOOK.delete("proxy_config")
            Log.i(TAG, "update_config: Unsupported type")
        }
    }

    fun update() {
        Log.i(TAG, "onReceive: Start the configuration file conversion")
        val notifyListenList: List<String> = DEFAULT_BOOK.tryRead("notify_listen_list", ArrayList())
        val blackKeywordList = DEFAULT_BOOK.tryRead("black_keyword_list", ArrayList<String>())
        val outdatedProxyItem = DEFAULT_BOOK.tryRead("proxy_config", ProxyConfigV1())
        // Replacement object
        val proxyItem = ProxyConfigV2()
        proxyItem.dns_over_socks5 = outdatedProxyItem.dns_over_socks5
        proxyItem.enable = outdatedProxyItem.enable
        proxyItem.password = outdatedProxyItem.password
        proxyItem.username = outdatedProxyItem.username
        proxyItem.host = outdatedProxyItem.proxy_host
        proxyItem.port = outdatedProxyItem.proxy_port
        SYSTEM_BOOK
            .write("notify_listen_list", notifyListenList)
            .write("block_keyword_list", blackKeywordList)
            .write("proxy_config", proxyItem)
            .write("version", 1)
        DEFAULT_BOOK.destroy()
    }

    companion object {
        private const val TAG = "update_to_version1"
    }
}
