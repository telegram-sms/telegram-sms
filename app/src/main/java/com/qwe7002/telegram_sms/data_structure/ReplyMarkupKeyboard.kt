package com.qwe7002.telegram_sms.data_structure;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class replyMarkupKeyboard {
    public static ArrayList<InlineKeyboardButton> getInlineKeyboardObj(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.text = text;
        button.callbackData = callbackData;
        ArrayList<InlineKeyboardButton> button_ArrayList = new ArrayList<>();
        button_ArrayList.add(button);
        return button_ArrayList;
    }

    public static class keyboardMarkup {
        @SerializedName("inline_keyboard")
        public ArrayList<ArrayList<InlineKeyboardButton>> inlineKeyboard;
        boolean oneTimeKeyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        @SerializedName("callback_data")
        String callbackData;
    }
}

