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
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.Template.getStringByName
import io.paperdb.Paper

data class Message(val title: String, val template: String, val content: Map<String, String>)

class TemplateActivity : AppCompatActivity() {
    lateinit var context: Context
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Paper.init(applicationContext)
        context = applicationContext
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
                getString(R.string.receive_sms_title),
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
        )

        val adapter = MessageAdapter(messages)
        recyclerView.adapter = adapter
    }

    class MessageAdapter(private val messageList: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
        lateinit var context: Context

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView = view.findViewById(R.id.template_title)
            val contentTextView: TextView = view.findViewById(R.id.template_content)
            val cardView: View = view.findViewById(R.id.template_card)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_template_card, parent, false)
            context = parent.context
            return MessageViewHolder(view)
        }

        @SuppressLint("InflateParams")
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messageList[position]
            holder.titleTextView.text = message.title
            holder.contentTextView.text =
                Template.render(context, message.template, message.content)
            holder.cardView.setOnClickListener {
                val inflater = LayoutInflater.from(context)
                val dialogView = inflater.inflate(R.layout.set_template, null)

                val editText = dialogView.findViewById<EditText>(R.id.template_editview)
                val result = Paper.book("Template")
                    .read(message.template, getStringByName(context, message.template)).toString()
                editText.setText(result)
                AlertDialog.Builder(context)
                    .setTitle(message.title)
                    .setView(dialogView as View)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                        val inputText = editText.text.toString()
                        Template.save(context, message.template, inputText)
                        holder.contentTextView.text =
                            Template.render(context, message.template, message.content)
                    }
                    .setNegativeButton(R.string.cancel_button, null)
                    .setNeutralButton(R.string.reset_button) { _: DialogInterface, _: Int ->
                        Paper.book("Template").delete(message.template)
                        holder.contentTextView.text =
                            Template.render(context, message.template, message.content)
                    }
                    .show()
            }
        }

        override fun getItemCount() = messageList.size
    }
}
