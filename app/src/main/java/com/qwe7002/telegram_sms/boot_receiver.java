package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.qwe7002.telegram_sms.data_structure.proxy_config;
import com.qwe7002.telegram_sms.static_class.public_func;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Paper;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NotNull final Context context, @NotNull Intent intent) {
        final String TAG = "boot_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            Paper.init(context);
            public_func.write_log(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            if (Paper.book().read("resend_list", new ArrayList<>()).size() != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.");
                public_func.start_resend(context);
            }
            if (intent.getAction().equals("android.intent.action.MY_PACKAGE_REPLACED")) {
                Log.i(TAG, "onReceive: Start the configuration file conversion");
                if (!Paper.book("system_config").read("convert", false)) {
                    List<String> notify_listen_list = Paper.book().read("notify_listen_list", new ArrayList<>());
                    ArrayList<String> black_keyword_list = Paper.book().read("black_keyword_list", new ArrayList<>());
                    outdated_proxy_config outdated_proxy_item = Paper.book().read("proxy_config", new outdated_proxy_config());
                    //Replacement object
                    proxy_config proxy_item = new proxy_config();
                    proxy_item.dns_over_socks5 = outdated_proxy_item.dns_over_socks5;
                    proxy_item.enable = outdated_proxy_item.enable;
                    proxy_item.password = outdated_proxy_item.password;
                    proxy_item.username = outdated_proxy_item.username;
                    proxy_item.host = outdated_proxy_item.proxy_host;
                    proxy_item.port = outdated_proxy_item.proxy_port;
                    Paper.book("system_config").write("notify_listen_list", notify_listen_list).write("block_keyword_list", black_keyword_list).write("proxy_config", proxy_item);
                    Paper.book("system_config").write("convert", true);
                    Paper.book().destroy();
                }
                public_func.reset_log_file(context);
            }
        }
    }
}
