package com.qwe7002.telegram_sms;

import android.util.Log;

import com.qwe7002.telegram_sms.config.proxy;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Paper;

public class update_to_version1 {
    private final String TAG = "update_to_version1";

    public void update() {
        Log.i(TAG, "onReceive: Start the configuration file conversion");

        List<String> notify_listen_list = Paper.book().read("notify_listen_list", new ArrayList<>());
        ArrayList<String> black_keyword_list = Paper.book().read("black_keyword_list", new ArrayList<>());
        com.qwe7002.telegram_sms.proxy_config outdated_proxy_item = Paper.book().read("proxy_config", new com.qwe7002.telegram_sms.proxy_config());
        //Replacement object
        proxy proxy_item = new proxy();
        proxy_item.dns_over_socks5 = outdated_proxy_item.dns_over_socks5;
        proxy_item.enable = outdated_proxy_item.enable;
        proxy_item.password = outdated_proxy_item.password;
        proxy_item.username = outdated_proxy_item.username;
        proxy_item.host = outdated_proxy_item.proxy_host;
        proxy_item.port = outdated_proxy_item.proxy_port;
        Paper.book("system_config").write("notify_listen_list", notify_listen_list).write("block_keyword_list", black_keyword_list).write("proxy_config", proxy_item);
        Paper.book("system_config").write("version", 1);
        Paper.book().destroy();
    }
}
