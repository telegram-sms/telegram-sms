package com.qwe7002.telegram_sms

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.Template.getStringByName
import com.tencent.mmkv.MMKV


class TemplateActivity : AppCompatActivity() {
    data class Message(val title: String, val template: String, val content: Map<String, String>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MMKV.initialize(this)
        setContentView(R.layout.activity_template)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val messages = listOf(
            Message(
                getString(R.string.receive_sms_title),
                "TPL_received_sms",
                mapOf("SIM" to "SIM1 ", "From" to "+16505551212", "Content" to "Hello, World!")
            ),
            Message(
                getString(R.string.receive_mms_title),
                "TPL_received_mms",
                mapOf("SIM" to "SIM1 ", "From" to "+16505551212", "Subject" to "MMS Subject", "ContentType" to "application/vnd.wap.multipart.mixed", "Size" to "10 KB")
            ),
            Message(
                getString(R.string.send_sms_title),
                "TPL_send_sms",
                mapOf("SIM" to "SIM1 ", "To" to "+16505551212", "Content" to "Hello, World!")
            ),
            Message(
                getString(R.string.missed_call_title),
                "TPL_missed_call",
                mapOf("SIM" to "SIM1 ", "From" to "+16505551212")
            ),
            Message(
                getString(R.string.receive_notification_title),
                "TPL_notification",
                mapOf(
                    "APP" to "Telegram SMS",
                    "Title" to "This is title",
                    "Description" to "This is description"
                )
            ),
            Message(
                getString(R.string.battery_title),
                "TPL_battery",
                mapOf(
                    "Message" to applicationContext.getString(R.string.battery_low),
                    "BatteryLevel" to "10"
                )
            ),
            Message(
                getString(R.string.missed_call_title),
                "TPL_receiving_call",
                mapOf("SIM" to "SIM1 ", "From" to "+16505551212")
            ),
        )

        val adapter = MessageAdapter(this, messages)
        recyclerView.adapter = adapter
    }

    class MessageAdapter(private val context: Context, private val messageList: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView = view.findViewById(R.id.template_title)
            val contentTextView: TextView = view.findViewById(R.id.template_content)
            val cardView: View = view.findViewById(R.id.template_card)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_template_card, parent, false)
            return MessageViewHolder(view)
        }

        @SuppressLint("InflateParams", "NotifyDataSetChanged")
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messageList[position]
            holder.titleTextView.text = message.title
            holder.contentTextView.text =
                Template.render(context, message.template, message.content)
            holder.cardView.setOnClickListener {
                val inflater = LayoutInflater.from(context)
                val dialogView = inflater.inflate(R.layout.set_template, null)

                val editText = dialogView.findViewById<EditText>(R.id.template_editview)
                val templateMMKV = MMKV.mmkvWithID(MMKVConst.TEMPLATE_ID)
                val result =
                    templateMMKV.decodeString(
                        message.template,
                        getStringByName(context, message.template)
                    )
                editText.setText(result)
                AlertDialog.Builder(context)
                    .setTitle(message.title)
                    .setView(dialogView as View)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                        val inputText = editText.text.toString()
                        Template.save(message.template, inputText)
                        notifyDataSetChanged()
                    }
                    .setNegativeButton(R.string.cancel_button, null)
                    .setNeutralButton(R.string.reset_button) { _: DialogInterface, _: Int ->
                        templateMMKV.removeValueForKey(message.template)
                        notifyDataSetChanged()
                    }
                    .show()
            }
        }

        override fun getItemCount() = messageList.size
    }
}
