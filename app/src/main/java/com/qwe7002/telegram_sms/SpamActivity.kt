package com.qwe7002.telegram_sms;
import android.os.Bundle;
import android.util.Log;
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
        final FloatingActionButton fab = findViewById(R.id.spam_list_fab);
        final ListView spamList = findViewById(R.id.spam_list);

        ArrayList<String> blockKeywordList = Paper.book("system_config").read("block_keyword_list", new ArrayList<>());
        assert blockKeywordList != null;
        ArrayAdapter<String> spam_list_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                blockKeywordList);
        spamList.setAdapter(spam_list_adapter);
        spamList.setOnItemClickListener((parent, view, position, id) -> {
            View spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null);
            final EditText editText = spam_dialog_view.findViewById(R.id.spam_sms_keyword_editview);
            editText.setText(blockKeywordList.get(position));
            new AlertDialog.Builder(spam_list_activity.this).setTitle(R.string.spam_keyword_edit_title)
                    .setView(spam_dialog_view)
                    .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                        blockKeywordList.set(position, editText.getText().toString());
                        saveAndFlush(blockKeywordList, spam_list_adapter);
                    })
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(R.string.delete_button, (((dialog, which) -> {
                        blockKeywordList.remove(position);
                        saveAndFlush(blockKeywordList, spam_list_adapter);
                    })))
                    .show();
        });

        fab.setOnClickListener(v -> {
            View spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null);
            final EditText editText = spam_dialog_view.findViewById(R.id.spam_sms_keyword_editview);
            new AlertDialog.Builder(spam_list_activity.this).setTitle(R.string.spam_keyword_add_title)
                    .setView(spam_dialog_view)
                    .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                        blockKeywordList.add(editText.getText().toString());
                        saveAndFlush(blockKeywordList, spam_list_adapter);
                    })
                    .setNeutralButton(R.string.cancel_button, null)
                    .show();
        });
    }

    void saveAndFlush(ArrayList<String> block_keyword_list, ArrayAdapter<String> list_adapter) {
        Log.d("save_and_flush", String.valueOf(block_keyword_list));
        Paper.book("system_config").write("block_keyword_list", block_keyword_list);
        list_adapter.notifyDataSetChanged();
    }
}
