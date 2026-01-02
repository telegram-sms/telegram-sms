package com.qwe7002.telegram_sms.MMKV
/**
 * Example of how to implement data structure migrations
 * This file serves as a reference for developers
 */
object MigrationExamples {
    // Example 1: Adding a new field
    // if (!preferences.contains("new_field")) {
    //     preferences.putBoolean("new_field", false)
    // }
    // Example 2: Data type conversion
    // val oldValue = preferences.getString("chat_id", "")
    // val newValue = oldValue.toLong()
    // preferences.putLong("chat_id_long", newValue)
    // Example 3: Remove deprecated field
    // preferences.remove("deprecated_field")
    // See DATA_STRUCTURE_VERSION.md for more examples
}
