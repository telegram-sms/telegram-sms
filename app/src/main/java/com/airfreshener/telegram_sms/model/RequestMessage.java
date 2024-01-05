package com.airfreshener.telegram_sms.model;

public class RequestMessage {
    //Turn off page preview to avoid being tracked
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public final boolean disable_web_page_preview = true;
    public long message_id;
    public String parse_mode;
    public String chat_id;
    public String text;
    public ReplyMarkupKeyboard.keyboard_markup reply_markup;
}
