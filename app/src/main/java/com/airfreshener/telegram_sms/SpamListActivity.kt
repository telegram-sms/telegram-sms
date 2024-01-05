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
        val spamList = findViewById<ListView>(R.id.spam_list)
        val blockKeywordList =
            Paper.book("system_config").read("block_keyword_list", ArrayList<String>())!!
        val spamListAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            blockKeywordList
        )
        spamList.adapter = spamListAdapter
        spamList.onItemClickListener =
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val spamDialogView = inflater.inflate(R.layout.set_keyword_layout, null)
                val editText =
                    spamDialogView.findViewById<EditText>(R.id.spam_sms_keyword_editview)
                editText.setText(blockKeywordList[position])
                AlertDialog.Builder(this@SpamListActivity)
                    .setTitle(R.string.spam_keyword_edit_title)
                    .setView(spamDialogView)
                    .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                        blockKeywordList[position] = editText.text.toString()
                        saveAndFlush(blockKeywordList, spamListAdapter)
                    }
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(R.string.delete_button) { dialog: DialogInterface?, which: Int ->
                        blockKeywordList.removeAt(position)
                        saveAndFlush(blockKeywordList, spamListAdapter)
                    }
                    .show()
            }
        fab.setOnClickListener { v: View? ->
            val spamDialogView = inflater.inflate(R.layout.set_keyword_layout, null)
            val editText = spamDialogView.findViewById<EditText>(R.id.spam_sms_keyword_editview)
            AlertDialog.Builder(this@SpamListActivity).setTitle(R.string.spam_keyword_add_title)
                .setView(spamDialogView)
                .setPositiveButton(R.string.ok_button) { dialog: DialogInterface?, which: Int ->
                    blockKeywordList.add(editText.text.toString())
                    saveAndFlush(blockKeywordList, spamListAdapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }

    private fun saveAndFlush(blockKeywordList: ArrayList<String>, listAdapter: ArrayAdapter<String>) {
        Log.d("save_and_flush", blockKeywordList.toString())
        Paper.book("system_config").write("block_keyword_list", blockKeywordList)
        listAdapter.notifyDataSetChanged()
    }
}
