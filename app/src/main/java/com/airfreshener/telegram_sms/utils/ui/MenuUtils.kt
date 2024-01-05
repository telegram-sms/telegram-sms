package com.airfreshener.telegram_sms.utils.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.utils.ServiceUtils
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
        val proxyDialogView = inflater.inflate(R.layout.set_proxy_layout, null)
        val proxyEnableView = proxyDialogView.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
        val proxyDohSocks5View =
            proxyDialogView.findViewById<SwitchMaterial>(R.id.doh_over_socks5_switch)
        val proxyHostView = proxyDialogView.findViewById<EditText>(R.id.proxy_host_editview)
        val proxyPortView = proxyDialogView.findViewById<EditText>(R.id.proxy_port_editview)
        val proxyUsernameView = proxyDialogView.findViewById<EditText>(R.id.proxy_username_editview)
        val proxyPasswordView = proxyDialogView.findViewById<EditText>(R.id.proxy_password_editview)
        val proxyItem = Paper.book("system_config").read("proxy_config", ProxyConfigV2())
        proxyEnableView.isChecked = proxyItem!!.enable
        proxyDohSocks5View.isChecked = proxyItem.dns_over_socks5
        proxyHostView.setText(proxyItem.host)
        proxyPortView.setText(proxyItem.port.toString())
        proxyUsernameView.setText(proxyItem.username)
        proxyPasswordView.setText(proxyItem.password)
        AlertDialog.Builder(activity).setTitle(R.string.proxy_dialog_title)
            .setView(proxyDialogView)
            .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                onOkCallback.onOkClicked(proxyEnableView.isChecked)
                proxyItem.enable = proxyEnableView.isChecked
                proxyItem.dns_over_socks5 = proxyDohSocks5View.isChecked
                proxyItem.host = proxyHostView.text.toString()
                proxyItem.port = proxyPortView.text.toString().toInt()
                proxyItem.username = proxyUsernameView.text.toString()
                proxyItem.password = proxyPasswordView.text.toString()
                Paper.book("system_config").write("proxy_config", proxyItem)
                Thread {
                    ServiceUtils.stopAllService(context!!)
                    if (sharedPreferences.getBoolean("initialized", false)) {
                        ServiceUtils.startService(
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
