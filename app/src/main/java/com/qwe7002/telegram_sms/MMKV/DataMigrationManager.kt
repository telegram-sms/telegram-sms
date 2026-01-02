package com.qwe7002.telegram_sms.MMKV

import android.content.Context
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * Data Migration Manager
 * Handles automatic data structure upgrades between versions
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object DataMigrationManager {

    /**
     * Current data structure version
     * Increment this when making breaking changes to data structure
     */
    const val CURRENT_DATA_VERSION = 1

    private const val DATA_VERSION_KEY = "data_structure_version"
    private const val TAG = "DataMigration"

    /**
     * Check and perform necessary data migrations
     * Should be called during app initialization
     */
    fun checkAndMigrate(context: Context) {
        val preferences = MMKV.defaultMMKV()
        val savedVersion = preferences.getInt(DATA_VERSION_KEY, 0)

        if (savedVersion == CURRENT_DATA_VERSION) {
            Log.d(TAG, "Data structure is up to date (version $CURRENT_DATA_VERSION)")
            return
        }

        if (savedVersion == 0) {
            Log.i(TAG, "First time initialization or legacy data detected")
            // Handle legacy data or first installation
            handleLegacyData(context, preferences)
        } else {
            Log.i(TAG, "Data structure upgrade needed: $savedVersion -> $CURRENT_DATA_VERSION")
            performMigration(context, savedVersion, CURRENT_DATA_VERSION)
        }

        // Update to current version
        preferences.putInt(DATA_VERSION_KEY, CURRENT_DATA_VERSION)
        Log.i(TAG, "Data structure version updated to $CURRENT_DATA_VERSION")
    }

    /**
     * Handle legacy data from versions before data structure versioning was implemented
     */
    private fun handleLegacyData(context: Context, preferences: MMKV) {
        Log.d(TAG, "Processing legacy data...")

        // Check if this is an existing installation with data
        val isInitialized = preferences.getBoolean("initialized", false)

        if (isInitialized) {
            Log.i(TAG, "Legacy installation detected, applying compatibility layer")
            // Legacy data exists, mark as version 1 (current baseline)
            // No migration needed as version 1 is our baseline
            migrateToVersion1(context, preferences)
        } else {
            Log.d(TAG, "New installation, no legacy data to migrate")
        }
    }

    /**
     * Perform migration from old version to new version
     */
    private fun performMigration(context: Context, fromVersion: Int, toVersion: Int) {
        Log.i(TAG, "Starting migration from version $fromVersion to $toVersion")

        // Apply migrations sequentially
        var currentVersion = fromVersion

        while (currentVersion < toVersion) {
            val nextVersion = currentVersion + 1
            Log.d(TAG, "Applying migration: $currentVersion -> $nextVersion")

            when (nextVersion) {
                1 -> migrateToVersion1(context, MMKV.defaultMMKV())
                2 -> migrateToVersion2(context, MMKV.defaultMMKV())
                3 -> migrateToVersion3(context, MMKV.defaultMMKV())
                // Add more migration cases as needed
                else -> {
                    Log.w(TAG, "No migration handler for version $nextVersion")
                }
            }

            currentVersion = nextVersion
        }

        Log.i(TAG, "Migration completed successfully")
    }

    /**
     * Migration to version 1 (baseline version)
     * This represents the current data structure
     */
    private fun migrateToVersion1(context: Context, preferences: MMKV) {
        Log.d(TAG, "Migrating to version 1 (baseline)")

        // Version 1 structure includes:
        // - bot_token (String)
        // - chat_id (String)
        // - message_thread_id (String)
        // - trusted_phone_number (String)
        // - fallback_sms (Boolean)
        // - chat_command (Boolean)
        // - battery_monitoring_switch (Boolean)
        // - charger_status (Boolean)
        // - verification_code (Boolean)
        // - doh_switch (Boolean)
        // - initialized (Boolean)
        // - privacy_dialog_agree (Boolean)
        // - call_notify (Boolean)
        // - hide_phone_number (Boolean)
        // - version_code (Int)
        // - api_address (String)

        // MMKV instances:
        // - MMKVConst.PROXY_ID: proxy settings
        // - MMKVConst.CHAT_ID: chat data
        // - MMKVConst.CHAT_INFO_ID: chat information
        // - MMKVConst.RESEND_ID: resend queue
        // - MMKVConst.CARBON_COPY_ID: carbon copy service config
        // - MMKVConst.UPDATE_ID: update check data
        // - MMKVConst.NOTIFY_ID: notification settings
        // - MMKVConst.TEMPLATE_ID: message templates
        // - MMKVConst.LOG_ID: log data

        // No actual migration needed for baseline version
        // Just validate data integrity
        validateVersion1Data(preferences)
    }

    /**
     * Validate version 1 data structure
     */
    private fun validateVersion1Data(preferences: MMKV) {
        // Ensure critical fields have valid defaults
        if (!preferences.contains("doh_switch")) {
            preferences.putBoolean("doh_switch", true)
        }

        if (!preferences.contains("api_address")) {
            preferences.putString("api_address", "api.telegram.org")
        }

        Log.d(TAG, "Version 1 data validation completed")
    }

    /**
     * Migration to version 2 (example for future use)
     * Add your migration logic here when you need to upgrade to version 2
     */
    private fun migrateToVersion2(context: Context, preferences: MMKV) {
        Log.d(TAG, "Migrating to version 2")

        // Example migration tasks:
        // 1. Add new fields with default values
        // 2. Transform existing data
        // 3. Move data between MMKV instances
        // 4. Remove deprecated fields

        // Example:
        // if (!preferences.contains("new_field_in_v2")) {
        //     preferences.putBoolean("new_field_in_v2", false)
        // }

        // Example: Migrate data from one format to another
        // val oldValue = preferences.getString("old_field", "")
        // if (oldValue.isNotEmpty()) {
        //     preferences.putString("new_field", transformData(oldValue))
        //     preferences.remove("old_field")
        // }
    }

    /**
     * Migration to version 3 (example for future use)
     */
    private fun migrateToVersion3(context: Context, preferences: MMKV) {
        Log.d(TAG, "Migrating to version 3")

        // Add your version 3 migration logic here
    }

    /**
     * Get current data structure version
     */
    fun getCurrentVersion(): Int {
        return MMKV.defaultMMKV().getInt(DATA_VERSION_KEY, 0)
    }

    /**
     * Check if migration is needed
     */
    fun isMigrationNeeded(): Boolean {
        val savedVersion = MMKV.defaultMMKV().getInt(DATA_VERSION_KEY, 0)
        return savedVersion != CURRENT_DATA_VERSION
    }

    /**
     * Force reset data structure version (use with caution)
     */
    fun resetDataVersion() {
        Log.w(TAG, "Resetting data structure version - this may cause data inconsistency!")
        MMKV.defaultMMKV().remove(DATA_VERSION_KEY)
    }

    /**
     * Backup current data before migration
     * This creates a snapshot of all MMKV data
     */
    fun backupData(context: Context): Boolean {
        return try {
            Log.i(TAG, "Creating data backup...")

            // Backup default MMKV
            val defaultMMKV = MMKV.defaultMMKV()
            val backupMMKV = MMKV.mmkvWithID("backup_${System.currentTimeMillis()}")
            copyMMKVData(defaultMMKV, backupMMKV)

            // Backup all other MMKV instances
            val mmkvIds = listOf(
                MMKVConst.PROXY_ID,
                MMKVConst.CHAT_ID,
                MMKVConst.CHAT_INFO_ID,
                MMKVConst.RESEND_ID,
                MMKVConst.CARBON_COPY_ID,
                MMKVConst.UPDATE_ID,
                MMKVConst.NOTIFY_ID,
                MMKVConst.TEMPLATE_ID,
                MMKVConst.LOG_ID
            )

            for (id in mmkvIds) {
                val sourceMMKV = MMKV.mmkvWithID(id)
                val backupInstanceMMKV = MMKV.mmkvWithID("backup_${id}_${System.currentTimeMillis()}")
                copyMMKVData(sourceMMKV, backupInstanceMMKV)
            }

            Log.i(TAG, "Data backup completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup data: ${e.message}", e)
            false
        }
    }

    /**
     * Copy all data from source MMKV to destination MMKV
     */
    private fun copyMMKVData(source: MMKV, destination: MMKV) {
        val allKeys = source.allKeys() ?: return

        for (key in allKeys) {
            // Try to decode and copy based on type
            // String values are most common
            source.decodeString(key)?.let {
                destination.encode(key, it)
                return@let
            }

            // Try other primitive types
            try {
                val intVal = source.decodeInt(key, Int.MIN_VALUE)
                if (intVal != Int.MIN_VALUE) {
                    destination.encode(key, intVal)
                    continue
                }
            } catch (e: Exception) { /* Not an int */ }

            try {
                val longVal = source.decodeLong(key, Long.MIN_VALUE)
                if (longVal != Long.MIN_VALUE) {
                    destination.encode(key, longVal)
                    continue
                }
            } catch (e: Exception) { /* Not a long */ }

            try {
                val boolVal = source.decodeBool(key)
                destination.encode(key, boolVal)
            } catch (e: Exception) { /* Not a boolean */ }
        }
    }
}

