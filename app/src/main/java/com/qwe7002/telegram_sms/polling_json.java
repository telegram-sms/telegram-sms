package com.qwe7002.telegram_sms;

class polling_json {
    long offset;
    int timeout;
    //Predefined types that accept return
    @SuppressWarnings({"unused", "RedundantSuppression"})
    final String[] allowed_updates = {"message", "channel_post"};
}
