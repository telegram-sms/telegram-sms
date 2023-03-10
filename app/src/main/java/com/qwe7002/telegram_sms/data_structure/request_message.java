package com.qwe7002.telegram_sms.data_structure;

public class request_message {
    //Turn off page preview to avoid being tracked
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public final boolean disable_web_page_preview = true;
    public long message_id;
    public String parse_mode;
    public String chat_id;
    public String text;
    public String message_thread_id;
    public reply_markup_keyboard.keyboard_markup reply_markup;
}
