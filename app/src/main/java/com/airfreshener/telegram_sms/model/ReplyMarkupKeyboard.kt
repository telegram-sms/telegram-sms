package com.airfreshener.telegram_sms.model;

import java.util.ArrayList;

public class ReplyMarkupKeyboard {
    public static ArrayList<InlineKeyboardButton> getInlineKeyboardObj(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.text = text;
        button.callback_data = callbackData;
        ArrayList<InlineKeyboardButton> buttonArraylist = new ArrayList<>();
        buttonArraylist.add(button);
        return buttonArraylist;
    }

    @SuppressWarnings("unused")
    public static class KeyboardMarkup {
        public ArrayList<ArrayList<InlineKeyboardButton>> inline_keyboard;
        boolean one_time_keyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        String callback_data;
    }
}

