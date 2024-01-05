package com.airfreshener.telegram_sms

import android.util.Log
import com.airfreshener.telegram_sms.config.proxy
import com.airfreshener.telegram_sms.upgrade_data.proxy_config
import io.paperdb.Paper

class update_to_version1 {
    private val TAG = "update_to_version1"
    fun check_error() {
        try {
            Paper.book("system_config").read("proxy_config", proxy())
        } catch (e: Exception) {
            e.printStackTrace()
            Paper.book("system_config").delete("proxy_config")
            Log.i(TAG, "update_config: Unsupported type")
        }
    }

    fun update() {
        Log.i(TAG, "onReceive: Start the configuration file conversion")
        val notify_listen_list: List<String> =
            Paper.book().read("notify_listen_list", ArrayList())!!
        val black_keyword_list = Paper.book().read("black_keyword_list", ArrayList<String>())!!
        val outdated_proxy_item = Paper.book().read("proxy_config", proxy_config())
        //Replacement object
        val proxy_item = proxy()
        proxy_item.dns_over_socks5 = outdated_proxy_item!!.dns_over_socks5
        proxy_item.enable = outdated_proxy_item.enable
        proxy_item.password = outdated_proxy_item.password
        proxy_item.username = outdated_proxy_item.username
        proxy_item.host = outdated_proxy_item.proxy_host
        proxy_item.port = outdated_proxy_item.proxy_port
        Paper.book("system_config").write("notify_listen_list", notify_listen_list)
            .write("block_keyword_list", black_keyword_list).write("proxy_config", proxy_item)
        Paper.book("system_config").write("version", 1)
        Paper.book().destroy()
    }
}
