package com.qwe7002.telegram_sms

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class FirstRunManager {
    companion object {
        private const val PREF_FIRST_RUN_COMPLETED = "first_run_completed"
        
        fun checkAndMarkFirstRun(context: Context): Boolean {
            val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
            val isFirstRun = !sharedPreferences.getBoolean(PREF_FIRST_RUN_COMPLETED, false)
            
            if (isFirstRun) {
                sharedPreferences.edit().putBoolean(PREF_FIRST_RUN_COMPLETED, true).apply()
                
                // Set the activity to be excluded from recents after first run
                val packageManager = context.packageManager
                val componentName = ComponentName(context, main_activity::class.java)
                
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            
            return isFirstRun
        }
    }
}
