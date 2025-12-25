package com.qwe7002.telegram_sms

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tencent.mmkv.MMKV

class SpamActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spam_list)
        MMKV.initialize(this)
        val inflater = this.layoutInflater
        val fab = findViewById<FloatingActionButton>(R.id.spam_list_fab)
        val spamList = findViewById<ListView>(R.id.spam_list)
        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(spamList) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        val preferences = MMKV.defaultMMKV()
        val blockKeywordList =
            preferences.getStringSet("block_keyword_list", setOf())?.toMutableList()
                ?: mutableListOf()
        val spamListAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            blockKeywordList
        )
        spamList.adapter = spamListAdapter
        spamList.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val spamDialogView = inflater.inflate(R.layout.set_keyword_layout, null)
                val editText =
                    spamDialogView.findViewById<EditText>(R.id.spam_sms_keyword_editview)
                editText.setText(blockKeywordList[position])
                AlertDialog.Builder(this@SpamActivity)
                    .setTitle(R.string.spam_keyword_edit_title)
                    .setView(spamDialogView)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        blockKeywordList[position] = editText.text.toString()
                        saveAndFlush(blockKeywordList, spamListAdapter)
                    }
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(
                        R.string.delete_button,
                        ((DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                            blockKeywordList.removeAt(position)
                            saveAndFlush(blockKeywordList, spamListAdapter)
                        }))
                    )
                    .show()
            }

        fab.setOnClickListener {
            val spamDialogView = inflater.inflate(R.layout.set_keyword_layout, null)
            val editText = spamDialogView.findViewById<EditText>(R.id.spam_sms_keyword_editview)
            AlertDialog.Builder(this@SpamActivity).setTitle(R.string.spam_keyword_add_title)
                .setView(spamDialogView)
                .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                    blockKeywordList.add(editText.text.toString())
                    saveAndFlush(blockKeywordList, spamListAdapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }

    private fun saveAndFlush(
        blackKeywordList: MutableList<String>,
        listAdapter: ArrayAdapter<String>
    ) {
        Log.d("save_and_flush", blackKeywordList.toString())
        MMKV.defaultMMKV().encode("block_keyword_list", blackKeywordList.toSet())
        listAdapter.notifyDataSetChanged()
    }
}



