package com.qwe7002.telegram_sms.data_structure;

import java.util.ArrayList;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class reply_markup_keyboard {
    public static ArrayList<InlineKeyboardButton> get_inline_keyboard_obj(String text, String callback_data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.text = text;
        button.callback_data = callback_data;
        ArrayList<InlineKeyboardButton> button_ArrayList = new ArrayList<>();
        button_ArrayList.add(button);
        return button_ArrayList;
    }

    public static class keyboard_markup {
        public ArrayList<ArrayList<InlineKeyboardButton>> inline_keyboard;
        boolean one_time_keyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        String callback_data;
    }
}

