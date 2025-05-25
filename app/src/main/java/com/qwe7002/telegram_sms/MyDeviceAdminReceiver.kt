package com.qwe7002.telegram_sms

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
// import android.widget.Toast // Usually not needed for a "hidden" app

// Modified at 10:00 AM UTC, October 26, 2023 - Created DeviceAdminReceiver
// Modified at 12:00 PM UTC, October 26, 2023 - Added secret key check for deactivation
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("MyDeviceAdminReceiver", "Device admin enabled")
        // Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // Modified at 12:00 PM UTC, October 26, 2023 - Check for deactivation authorization
        if (!KeyHelper.isKeySet(context)) {
            // No key set, so protection is not active. Allow deactivation.
            return context.getString(R.string.device_admin_disable_warning_no_key) // Example: "Standard device admin deactivation."
        }

        return if (KeyHelper.isAdminDisableAuthorized(context)) {
            KeyHelper.setAdminDisableAuthorized(context, false) // Reset flag after use
            Log.d("MyDeviceAdminReceiver", "Device admin deactivation authorized by key.")
            context.getString(R.string.device_admin_disable_authorized) // Example: "Deactivation authorized. You may now proceed."
        } else {
            Log.d("MyDeviceAdminReceiver", "Device admin deactivation attempt without key authorization.")
            context.getString(R.string.device_admin_disable_protected_message) // Example: "Deactivation requires authorization. Open app, use 'Authorize Admin Deactivation' with your secret key."
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Modified at 12:00 PM UTC, October 26, 2023 - Log if disabled while protection was set
        if (KeyHelper.isKeySet(context)) {
            // If a key was set, it implies protection was active.
            // Note: isAdminDisableAuthorized might have been true if onDisableRequested allowed it.
            // This log indicates deactivation happened when the feature was generally configured.
            Log.w("MyDeviceAdminReceiver", "Device admin has been disabled. Secret key protection was configured on this app.")
            // Consider if any specific action should be taken here, e.g., notifying via Telegram if that functionality is available.
        } else {
            Log.d("MyDeviceAdminReceiver", "Device admin disabled (no key protection was set).")
        }
        // Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
