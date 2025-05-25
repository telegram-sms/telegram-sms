package com.qwe7002.telegram_sms

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

// Modified at 10:00 AM UTC, October 26, 2023 - Created DeviceAdminReceiver
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("MyDeviceAdminReceiver", "Device admin enabled")
        // You can add a Toast or log message here, but for the final hidden app, UI interactions should be minimal.
        // Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("MyDeviceAdminReceiver", "Device admin disabled")
        // Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
