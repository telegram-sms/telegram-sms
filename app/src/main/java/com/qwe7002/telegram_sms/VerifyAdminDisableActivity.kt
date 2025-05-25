package com.qwe7002.telegram_sms

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Modified at 11:30 AM UTC, October 26, 2023 - Activity to verify secret key for admin deactivation
class VerifyAdminDisableActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_admin_disable)

        val secretKeyEditText = findViewById<EditText>(R.id.secret_key_edit_text)
        val verifyKeyButton = findViewById<Button>(R.id.verify_key_button)

        verifyKeyButton.setOnClickListener {
            val enteredKey = secretKeyEditText.text.toString()
            if (enteredKey.length == 16) {
                if (KeyHelper.verifyKey(this, enteredKey)) {
                    KeyHelper.setAdminDisableAuthorized(this, true)
                    Toast.makeText(this, "Authorization successful. You can now disable Device Admin.", Toast.LENGTH_LONG).show()
                    // Optionally, you could try to direct the user back to Device Admin settings
                    // For example, by starting an intent:
                    // try {
                    //    startActivity(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")))
                    // } catch (e: Exception) {
                    //    // Handle case where direct navigation fails
                    // }
                    finish() // Close this activity
                } else {
                    Toast.makeText(this, "Invalid key.", Toast.LENGTH_SHORT).show()
                    KeyHelper.setAdminDisableAuthorized(this, false) // Explicitly set to false
                }
            } else {
                Toast.makeText(this, "Key must be 16 digits.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
