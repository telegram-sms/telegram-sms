package com.airfreshener.telegram_sms.model;

public class PollingJson {
    //Predefined types that accept return
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public final String[] allowed_updates = {"message", "channel_post", "callback_query"};
    public long offset;
    public int timeout;
}
