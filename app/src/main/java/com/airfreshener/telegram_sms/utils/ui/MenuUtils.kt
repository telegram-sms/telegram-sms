package com.airfreshener.telegram_sms.utils.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.config.proxy
import com.airfreshener.telegram_sms.static_class.service_func
import com.google.android.material.switchmaterial.SwitchMaterial
import io.paperdb.Paper

object MenuUtils {
    @JvmStatic
    fun showProxySettingsDialog(
        inflater: LayoutInflater,
        activity: Activity,
        context: Context,
        sharedPreferences: SharedPreferences,
        onOkCallback: OkCallback
    ) {
        val proxy_dialog_view = inflater.inflate(R.layout.set_proxy_layout, null)
        val proxy_enable = proxy_dialog_view.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
        val proxy_doh_socks5 =
            proxy_dialog_view.findViewById<SwitchMaterial>(R.id.doh_over_socks5_switch)
        val proxy_host = proxy_dialog_view.findViewById<EditText>(R.id.proxy_host_editview)
        val proxy_port = proxy_dialog_view.findViewById<EditText>(R.id.proxy_port_editview)
        val proxy_username = proxy_dialog_view.findViewById<EditText>(R.id.proxy_username_editview)
        val proxy_password = proxy_dialog_view.findViewById<EditText>(R.id.proxy_password_editview)
        val proxy_item = Paper.book("system_config").read("proxy_config", proxy())
        proxy_enable.isChecked = proxy_item!!.enable
        proxy_doh_socks5.isChecked = proxy_item.dns_over_socks5
        proxy_host.setText(proxy_item.host)
        proxy_port.setText(proxy_item.port.toString())
        proxy_username.setText(proxy_item.username)
        proxy_password.setText(proxy_item.password)
        AlertDialog.Builder(activity).setTitle(R.string.proxy_dialog_title)
            .setView(proxy_dialog_view)
            .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                onOkCallback.onOkClicked(proxy_enable.isChecked)
                proxy_item.enable = proxy_enable.isChecked
                proxy_item.dns_over_socks5 = proxy_doh_socks5.isChecked
                proxy_item.host = proxy_host.text.toString()
                proxy_item.port = proxy_port.text.toString().toInt()
                proxy_item.username = proxy_username.text.toString()
                proxy_item.password = proxy_password.text.toString()
                Paper.book("system_config").write("proxy_config", proxy_item)
                Thread {
                    service_func.stop_all_service(context!!)
                    if (sharedPreferences.getBoolean("initialized", false)) {
                        service_func.start_service(
                            context,
                            sharedPreferences.getBoolean("battery_monitoring_switch", false),
                            sharedPreferences.getBoolean("chat_command", false)
                        )
                    }
                }.start()
            }
            .show()
    }

    interface OkCallback {
        fun onOkClicked(isChecked: Boolean)
    }
}
