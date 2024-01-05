package com.airfreshener.telegram_sms

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.paperdb.Paper

class SpamListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spam_list)
        val inflater = this.layoutInflater
        val fab = findViewById<FloatingActionButton>(R.id.spam_list_fab)
        val spam_list = findViewById<ListView>(R.id.spam_list)
        val block_keyword_list =
            Paper.book("system_config").read("block_keyword_list", ArrayList<String>())!!
        val spam_list_adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            block_keyword_list
        )
        spam_list.adapter = spam_list_adapter
        spam_list.onItemClickListener =
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null)
                val editText =
                    spam_dialog_view.findViewById<EditText>(R.id.spam_sms_keyword_editview)
                editText.setText(block_keyword_list[position])
                AlertDialog.Builder(this@SpamListActivity)
                    .setTitle(R.string.spam_keyword_edit_title)
                    .setView(spam_dialog_view)
                    .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                        block_keyword_list[position] = editText.text.toString()
                        save_and_flush(block_keyword_list, spam_list_adapter)
                    }
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(R.string.delete_button) { dialog: DialogInterface?, which: Int ->
                        block_keyword_list.removeAt(position)
                        save_and_flush(block_keyword_list, spam_list_adapter)
                    }
                    .show()
            }
        fab.setOnClickListener { v: View? ->
            val spam_dialog_view = inflater.inflate(R.layout.set_keyword_layout, null)
            val editText = spam_dialog_view.findViewById<EditText>(R.id.spam_sms_keyword_editview)
            AlertDialog.Builder(this@SpamListActivity).setTitle(R.string.spam_keyword_add_title)
                .setView(spam_dialog_view)
                .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                    block_keyword_list.add(editText.text.toString())
                    save_and_flush(block_keyword_list, spam_list_adapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }

    private fun save_and_flush(block_keyword_list: ArrayList<String>, list_adapter: ArrayAdapter<String>) {
        Log.d("save_and_flush", block_keyword_list.toString())
        Paper.book("system_config").write("block_keyword_list", block_keyword_list)
        list_adapter.notifyDataSetChanged()
    }
}
