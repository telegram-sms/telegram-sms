package com.qwe7002.telegram_sms;

class message_json {
    long message_id;
    String parse_mode;
    String chat_id;
    String text;
    //Turn off page preview to avoid being tracked
    @SuppressWarnings({"unused", "RedundantSuppression"})
    final boolean disable_web_page_preview = true;
}
