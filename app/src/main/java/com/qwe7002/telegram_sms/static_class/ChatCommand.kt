package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.qwe7002.telegram_sms.R
import io.paperdb.Paper

@Suppress("DEPRECATION")
object ChatCommand {
    @JvmStatic
    fun getCommandList(
        context: Context,
        command: String,
        isPrivate: Boolean,
        privacyMode: Boolean,
        botUsername: String
    ): String {
        var smsCommand = context.getString(R.string.sendsms)
        if (Other.getActiveCard(context) == 2) {
            smsCommand = context.getString(R.string.sendsms_dual)
        }
       /* smsCommand += """
            
            ${context.getString(R.string.get_spam_sms)}
            """.trimIndent()
*/
        smsCommand += "\n" + context.getString(R.string.get_spam_sms)
        var ussdCommand = ""
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ussdCommand = "\n" + context.getString(R.string.send_ussd_command);
                if (Other.getActiveCard(context) == 2) {
                    ussdCommand = "\n" + context.getString(R.string.send_ussd_dual_command);
                }
            }
        }

        var result:String = if (command == "/commandlist") {
            (context.getString(R.string.available_command) + "\n" + smsCommand + ussdCommand).replace("/", "");
        }else {
            context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.available_command) + "\n" + smsCommand + ussdCommand;
        }
        if (!isPrivate && privacyMode && botUsername.isNotEmpty()) {
            result = result.replace(" -", "@$botUsername -");
        }
        return result
    }

    @JvmStatic
    fun getInfo(context: Context): String {
        var cardInfo = ""
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            cardInfo = "\nSIM: " + Other.getSimDisplayName(context, 0);
            if (Other.getActiveCard(context) == 2) {
                cardInfo = "\nSIM1: " + Other.getSimDisplayName(context, 0) + "\nSIM2: " + Other.getSimDisplayName(context, 1);
            }
        }
        var spamCount = ""
        val spamSmsList = checkNotNull(Paper.book().read("spam_sms_list", ArrayList<String>()))
        if (spamSmsList.isNotEmpty()) {
            spamCount = "\n" + context.getString(R.string.spam_count_title) + spamSmsList.size;
        }
        return  context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + batteryInfo(context) + "\n" + context.getString(R.string.current_network_connection_status) + getNetworkType(context) + spamCount + cardInfo;

    }

    private fun batteryInfo(context: Context): String {
        val batteryManager =
            checkNotNull(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        var batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.i(
                "getBatteryInfo",
                "The previous battery is over 100%, and the correction is 100%."
            )
            batteryLevel = 100
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = checkNotNull(context.registerReceiver(null, filter))
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
        when (chargeStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> batteryStringBuilder.append(
                " ("
            ).append(context.getString(R.string.charging)).append(")")

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> batteryStringBuilder.append(
                    " ("
                ).append(context.getString(R.string.not_charging)).append(")")
            }
        }
        return batteryStringBuilder.toString()
    }

    @Suppress("DEPRECATION")
    private fun getNetworkType(context: Context): String {
        var netType = "Unknown"
        val connectManager =
            checkNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        val telephonyManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networks = connectManager.allNetworks
            for (network in networks) {
                val networkCapabilities =
                    checkNotNull(connectManager.getNetworkCapabilities(network))
                if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        netType = "WIFI"
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                            continue
                        }
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_STATE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.d("get_network_type", "No permission.")
                        }
                        netType = checkCellularNetworkType(telephonyManager.dataNetworkType)
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        netType = "Bluetooth"
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        netType = "Ethernet"
                    }
                }
            }
        } else {
            val activeNetworkInfo = connectManager.activeNetworkInfo ?: return netType
            netType = when (activeNetworkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> "WIFI"
                ConnectivityManager.TYPE_MOBILE -> checkCellularNetworkType(activeNetworkInfo.subtype)
                else -> netType
            }
        }

        return netType
    }

    private fun checkCellularNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            else -> "Unknown"
        }
    }
}
