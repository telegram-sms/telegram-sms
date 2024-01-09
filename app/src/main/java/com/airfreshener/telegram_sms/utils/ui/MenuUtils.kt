package com.airfreshener.telegram_sms.utils.ui

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.databinding.SetProxyLayoutBinding
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.ServiceUtils

object MenuUtils {
    fun showProxySettingsDialog(
        activity: Activity,
        prefsRepository: PrefsRepository,
        onOkCallback: (isChecked: Boolean) -> Unit
    ) {
        val appContext = activity.applicationContext
        val proxyItem = PaperUtils.getProxyConfig()
        val proxyDialogView = activity.layoutInflater.inflate(R.layout.set_proxy_layout, null)
        val binding = SetProxyLayoutBinding.bind(proxyDialogView)
        binding.proxyEnableSwitch.isChecked = proxyItem.enable
        binding.dohOverSocks5Switch.isChecked = proxyItem.dns_over_socks5
        binding.proxyHostEditview.setText(proxyItem.host)
        binding.proxyPortEditview.setText(proxyItem.port.toString())
        binding.proxyUsernameEditview.setText(proxyItem.username)
        binding.proxyPasswordEditview.setText(proxyItem.password)
        AlertDialog.Builder(activity).setTitle(R.string.proxy_dialog_title)
            .setView(proxyDialogView)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                onOkCallback(binding.proxyEnableSwitch.isChecked)
                proxyItem.enable = binding.proxyEnableSwitch.isChecked
                proxyItem.dns_over_socks5 = binding.dohOverSocks5Switch.isChecked
                proxyItem.host = binding.proxyHostEditview.text.toString()
                proxyItem.port = binding.proxyPortEditview.text.toString().toInt()
                proxyItem.username = binding.proxyUsernameEditview.text.toString()
                proxyItem.password = binding.proxyPasswordEditview.text.toString()
                SYSTEM_BOOK.write("proxy_config", proxyItem)
                Thread {
                    ServiceUtils.stopAllService(appContext)
                    if (prefsRepository.getInitialized()) {
                        ServiceUtils.startService(appContext, prefsRepository.getSettings())
                    }
                }.start()
            }
            .show()
    }

}
