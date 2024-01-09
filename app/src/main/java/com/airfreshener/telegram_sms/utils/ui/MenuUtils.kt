package com.airfreshener.telegram_sms.utils.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.common.PrefsRepository
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.ServiceUtils
import com.google.android.material.switchmaterial.SwitchMaterial

object MenuUtils {
    fun showProxySettingsDialog(
        inflater: LayoutInflater,
        activity: Activity,
        context: Context,
        prefsRepository: PrefsRepository,
        onOkCallback: (isChecked: Boolean) -> Unit
    ) {
        val proxyDialogView = inflater.inflate(R.layout.set_proxy_layout, null)
        val proxyEnableView = proxyDialogView.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
        val proxyDohSocks5View =
            proxyDialogView.findViewById<SwitchMaterial>(R.id.doh_over_socks5_switch)
        val proxyHostView = proxyDialogView.findViewById<EditText>(R.id.proxy_host_editview)
        val proxyPortView = proxyDialogView.findViewById<EditText>(R.id.proxy_port_editview)
        val proxyUsernameView = proxyDialogView.findViewById<EditText>(R.id.proxy_username_editview)
        val proxyPasswordView = proxyDialogView.findViewById<EditText>(R.id.proxy_password_editview)
        val proxyItem = PaperUtils.getProxyConfig()
        proxyEnableView.isChecked = proxyItem.enable
        proxyDohSocks5View.isChecked = proxyItem.dns_over_socks5
        proxyHostView.setText(proxyItem.host)
        proxyPortView.setText(proxyItem.port.toString())
        proxyUsernameView.setText(proxyItem.username)
        proxyPasswordView.setText(proxyItem.password)
        AlertDialog.Builder(activity).setTitle(R.string.proxy_dialog_title)
            .setView(proxyDialogView)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                onOkCallback(proxyEnableView.isChecked)
                proxyItem.enable = proxyEnableView.isChecked
                proxyItem.dns_over_socks5 = proxyDohSocks5View.isChecked
                proxyItem.host = proxyHostView.text.toString()
                proxyItem.port = proxyPortView.text.toString().toInt()
                proxyItem.username = proxyUsernameView.text.toString()
                proxyItem.password = proxyPasswordView.text.toString()
                SYSTEM_BOOK.write("proxy_config", proxyItem)
                Thread {
                    ServiceUtils.stopAllService(context)
                    if (prefsRepository.getInitialized()) {
                        ServiceUtils.startService(
                            context,
                            prefsRepository.getBatteryMonitoring(),
                            prefsRepository.getChatCommand()
                        )
                    }
                }.start()
            }
            .show()
    }

}