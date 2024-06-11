package com.qwe7002.telegram_sms.static_class;

import static android.content.Context.BATTERY_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.qwe7002.telegram_sms.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import io.paperdb.Paper;

public class chatCommand {
    public static String getCommandList(Context context,String command,boolean isPrivate,boolean privacyMode,String botUsername){
        String smsCommand = context.getString(R.string.sendsms);
        if (other.getActiveCard(context) == 2) {
            smsCommand = context.getString(R.string.sendsms_dual);
        }
        smsCommand += "\n" + context.getString(R.string.get_spam_sms);

        String ussdCommand = "";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ussdCommand = "\n" + context.getString(R.string.send_ussd_command);
                if (other.getActiveCard(context) == 2) {
                    ussdCommand = "\n" + context.getString(R.string.send_ussd_dual_command);
                }
            }
        }
        String result;
        if (command.equals("/commandlist")) {
            result = (context.getString(R.string.available_command) + "\n" + smsCommand + ussdCommand).replace("/", "");
        }else {
            result = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.available_command) + "\n" + smsCommand + ussdCommand;
        }
        if (!isPrivate && privacyMode && !botUsername.isEmpty()) {
            result = result.replace(" -", "@" + botUsername + " -");
        }
        return result;
    }

    public static String getInfo(Context context){
        String cardInfo = "";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            cardInfo = "\nSIM: " + other.getSimDisplayName(context, 0);
            if (other.getActiveCard(context) == 2) {
                cardInfo = "\nSIM1: " + other.getSimDisplayName(context, 0) + "\nSIM2: " + other.getSimDisplayName(context, 1);
            }
        }
        String spamCount = "";
        ArrayList<String> spamSmsList = Paper.book().read("spam_sms_list", new ArrayList<>());
        assert spamSmsList != null;
        if (!spamSmsList.isEmpty()) {
            spamCount = "\n" + context.getString(R.string.spam_count_title) + spamSmsList.size();
        }
        return  context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo(context) + "\n" + context.getString(R.string.current_network_connection_status) + getNetworkType(context) + spamCount + cardInfo;

    }

    @NotNull
    public static String getBatteryInfo(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        assert batteryManager != null;
        int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (battery_level > 100) {
            Log.i("getBatteryInfo", "The previous battery is over 100%, and the correction is 100%.");
            battery_level = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder battery_string_builder = new StringBuilder().append(battery_level).append("%");
        switch (charge_status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                battery_string_builder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        battery_string_builder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return battery_string_builder.toString();
    }

    private static String getNetworkType(Context context) {
        String netType = "Unknown";
        ConnectivityManager connectManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connectManager != null;
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Network[] networks = connectManager.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities networkCapabilities = connectManager.getNetworkCapabilities(network);
                assert networkCapabilities != null;
                if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        netType = "WIFI";
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                            continue;
                        }
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                            Log.d("get_network_type", "No permission.");
                        }
                        netType = checkCellularNetworkType(telephonyManager.getDataNetworkType());
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        netType = "Bluetooth";
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        netType = "Ethernet";
                    }
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connectManager.getActiveNetworkInfo();
            if (activeNetworkInfo == null) {
                return netType;
            }
            switch (activeNetworkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    netType = "WIFI";
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    netType = checkCellularNetworkType(activeNetworkInfo.getSubtype());
                    break;
            }
        }

        return netType;
    }

    private static String checkCellularNetworkType(int type) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "NR";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
                net_type = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                net_type = "2G";
                break;
        }
        return net_type;
    }

}
