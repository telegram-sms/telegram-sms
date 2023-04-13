package com.qwe7002.telegram_sms.static_class;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_sms.R;
import com.qwe7002.telegram_sms.data_structure.smsRequestInfo;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import io.paperdb.Paper;


public class otherFunc {

    public static String getNineKeyMapConvert(String input) {
        final Map<Character, Integer> nineKeyMap = new HashMap<>() {
            {
                put('A', 2);
                put('B', 2);
                put('C', 2);
                put('D', 3);
                put('E', 3);
                put('F', 3);
                put('G', 4);
                put('H', 4);
                put('I', 4);
                put('J', 5);
                put('K', 5);
                put('L', 5);
                put('M', 6);
                put('N', 6);
                put('O', 6);
                put('P', 7);
                put('Q', 7);
                put('R', 7);
                put('S', 7);
                put('T', 8);
                put('U', 8);
                put('V', 8);
                put('W', 9);
                put('X', 9);
                put('Y', 9);
                put('Z', 9);
            }
        };
        StringBuilder result = new StringBuilder();
        char[] phoneNumberCharArray = input.toUpperCase().toCharArray();
        for (char c : phoneNumberCharArray) {
            if (Character.isUpperCase(c)) {
                result.append(nineKeyMap.get(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static long parseStringToLong(String content) {
        long result = 0;
        try {
            result = Long.parseLong(content);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return result;
    }

    @NotNull
    public static String getSendPhoneNumber(@NotNull String phone_number) {
        phone_number = getNineKeyMapConvert(phone_number);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < phone_number.length(); ++i) {
            char c = phone_number.charAt(i);
            if (c == '+' || Character.isDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String getDualSimCardDisplay(Context context, int slot, boolean show_name) {
        String dualSim = "";
        if (slot == -1) {
            return dualSim;
        }
        if (otherFunc.getActiveCard(context) >= 2) {
            String result = "";
            if (show_name) {
                result = "(" + getSimDisplayName(context, slot) + ")";
            }
            dualSim = "SIM" + (slot + 1) + result + " ";
        }
        return dualSim;
    }

    public static boolean isPhoneNumber(@NotNull String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                Log.d("is_phone_number", "is_phone_number: found +.");
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }


    public static long get_message_id(String result) {
        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsLong();
    }

    @NotNull
    public static Notification getNotificationObj(Context context, String notification_name) {
        Notification.Builder notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_name, notification_name,
                    NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(channel);
            notification = new Notification.Builder(context, notification_name);
        } else {//Notification generation method after O
            notification = new Notification.Builder(context).setPriority(Notification.PRIORITY_MIN);
        }
        notification.setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        return notification.build();
    }

    public static int getSubId(Context context, int slot) {
        int active_card = otherFunc.getActiveCard(context);
        if (active_card >= 2) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return -1;
            }
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            assert subscriptionManager != null;
            return subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot).getSubscriptionId();
        }
        return -1;
    }

    public static int getActiveCard(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d("get_active_card", "No permission.");
            return -1;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        return subscriptionManager.getActiveSubscriptionInfoCount();
    }


    public static String getSimDisplayName(Context context, int slot) {
        final String TAG = "get_sim_display_name";
        String result = "Unknown";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No permission.");
            return result;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot);
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.");
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);
            }
            if (info == null) {
                return result;
            }
            return result;
        }
        result = info.getDisplayName().toString();
        if (info.getDisplayName().toString().contains("CARD") || info.getDisplayName().toString().contains("SUB")) {
            result = info.getCarrierName().toString();
        }
        return result;
    }


    public static void add_message_list(long messageId, String phone, int slot) {
        smsRequestInfo item = new smsRequestInfo();
        item.phone = phone;
        item.card = slot;
        Paper.book().write(String.valueOf(messageId), item);
        Log.d("add_message_list", "add_message_list: " + messageId);
    }

}
