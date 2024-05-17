package com.qwe7002.telegram_sms.data_structure;

import java.util.ArrayList;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class replyMarkupKeyboard {
    public static ArrayList<InlineKeyboardButton> getInlineKeyboardObj(String text, String callback_data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.text = text;
        button.callback_data = callback_data;
        ArrayList<InlineKeyboardButton> button_ArrayList = new ArrayList<>();
        button_ArrayList.add(button);
        return button_ArrayList;
    }

    public static class keyboardMarkup {
        public ArrayList<ArrayList<InlineKeyboardButton>> inlineKeyboard;
        boolean oneTimeKeyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        String callback_data;
    }
}

