package com.airfreshener.telegram_sms.migration

import android.util.Log
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.migration.ProxyConfigV1
import io.paperdb.Paper

class UpdateVersion1 {

    fun checkError() {
        try {
            Paper.book("system_config").read("proxy_config", ProxyConfigV2())
        } catch (e: Exception) {
            e.printStackTrace()
            Paper.book("system_config").delete("proxy_config")
            Log.i(TAG, "update_config: Unsupported type")
        }
    }

    fun update() {
        Log.i(TAG, "onReceive: Start the configuration file conversion")
        val notifyListenList: List<String> =
            Paper.book().read("notify_listen_list", ArrayList())!!
        val blackKeywordList = Paper.book().read("black_keyword_list", ArrayList<String>())!!
        val outdatedProxyItem = Paper.book().read("proxy_config", ProxyConfigV1())
        //Replacement object
        val proxyItem = ProxyConfigV2()
        proxyItem.dns_over_socks5 = outdatedProxyItem!!.dns_over_socks5
        proxyItem.enable = outdatedProxyItem.enable
        proxyItem.password = outdatedProxyItem.password
        proxyItem.username = outdatedProxyItem.username
        proxyItem.host = outdatedProxyItem.proxy_host
        proxyItem.port = outdatedProxyItem.proxy_port
        Paper.book("system_config")
            .write("notify_listen_list", notifyListenList)
            .write("block_keyword_list", blackKeywordList)
            .write("proxy_config", proxyItem)
        Paper.book("system_config").write("version", 1)
        Paper.book().destroy()
    }

    companion object {
        private const val TAG = "update_to_version1"
    }
}
