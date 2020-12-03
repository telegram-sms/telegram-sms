package com.qwe7002.telegram_sms.data_structure;

public class polling_json {
    //Predefined types that accept return
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public final String[] allowed_updates = {"message", "channel_post", "callback_query"};
    public long offset;
    public int timeout;
}
