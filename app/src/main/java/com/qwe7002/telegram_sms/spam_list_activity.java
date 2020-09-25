package com.qwe7002.telegram_sms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import io.paperdb.Paper;

public class spam_list_activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spam_list);

        LayoutInflater inflater = this.getLayoutInflater();
        final FloatingActionButton fab = findViewById(R.id.fab);
        final ListView spam_list = findViewById(R.id.spam_list);

        ArrayList<String> black_keyword_list = Paper.book().read("black_keyword_list", new ArrayList<>());
        ArrayAdapter<String> spam_list_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                                                                    black_keyword_list);
        spam_list.setAdapter(spam_list_adapter);
        spam_list.setOnItemClickListener((parent, view, position, id) -> {
            View spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null);
            final EditText editText = spam_dialog_view.findViewById(R.id.spam_sms_keyword_menu_item);
            editText.setText(black_keyword_list.get(position));
            new AlertDialog.Builder(spam_list_activity.this).setTitle(R.string.spam_keyword_edit_title)
                    .setView(spam_dialog_view)
                    .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                        black_keyword_list.set(position, editText.getText().toString());
                        Paper.book().write("black_keyword_list", black_keyword_list);
                        spam_list_adapter.notifyDataSetChanged();
                    })
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(R.string.delete_button, (((dialog, which) -> {
                        black_keyword_list.remove(position);
                        Paper.book().write("black_keyword_list", black_keyword_list);
                        spam_list_adapter.notifyDataSetChanged();
                    })))
                    .show();
        });

        fab.setOnClickListener(v -> {
            View spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null);
            final EditText editText = spam_dialog_view.findViewById(R.id.spam_sms_keyword_menu_item);
            new AlertDialog.Builder(spam_list_activity.this).setTitle(R.string.spam_keyword_add_title)
                    .setView(spam_dialog_view)
                    .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                        black_keyword_list.add(editText.getText().toString());
                        Paper.book().write("black_keyword_list", black_keyword_list);
                        spam_list_adapter.notifyDataSetChanged();
                    })
                    .setNeutralButton(R.string.cancel_button, null)
                    .show();
        });
    }
}


