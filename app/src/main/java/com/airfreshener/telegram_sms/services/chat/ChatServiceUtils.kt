package com.airfreshener.telegram_sms.services.chat

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
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.ServiceUtils.batteryManager
import com.airfreshener.telegram_sms.utils.ServiceUtils.connectivityManager
import com.airfreshener.telegram_sms.utils.ServiceUtils.telephonyManager

object ChatServiceUtils {
    private const val TAG = "ChatServiceUtils"

    fun getBatteryInfo(appContext: Context): String {
        var batteryLevel = appContext.batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.i(TAG, "The previous battery is over 100%, and the correction is 100%.")
            batteryLevel = 100
        }
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = appContext.registerReceiver(null, intentFilter)!!
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val stringBuilder = StringBuilder().append(batteryLevel).append("%")
        when (chargeStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> stringBuilder
                .append(" (")
                .append(appContext.getString(R.string.charging))
                .append(")")
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    BatteryManager.BATTERY_PLUGGED_AC,
                    BatteryManager.BATTERY_PLUGGED_USB,
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> stringBuilder
                        .append(" (")
                        .append(appContext.getString(R.string.not_charging))
                        .append(")")
                }
            }
        }
        return stringBuilder.toString()
    }

    fun getNetworkType(appContext: Context): String {
        var netType = "Unknown"
        val connectManager = appContext.connectivityManager
        val telephonyManager = appContext.telephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networks = connectManager.allNetworks
            if (networks.isNotEmpty()) {
                for (network in networks) {
                    val networkCapabilities = connectManager.getNetworkCapabilities(network)!!
                    if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            netType = "WIFI"
                        }
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                                continue
                            }
                            if (ActivityCompat.checkSelfPermission(
                                    appContext,
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
            }
        } else {
            val networkInfo = connectManager.activeNetworkInfo ?: return netType
            when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> netType = "WIFI"
                ConnectivityManager.TYPE_MOBILE -> netType =
                    checkCellularNetworkType(networkInfo.subtype)
            }
        }
        return netType
    }

    private fun checkCellularNetworkType(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
        else -> "Unknown"
    }
}
