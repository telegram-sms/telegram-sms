package com.qwe7002.telegram_sms

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_sms.data_structure.CCService
import com.qwe7002.telegram_sms.value.ccOptions
import io.paperdb.Paper

class CCActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cc)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val inflater = this.layoutInflater
        val fab = findViewById<FloatingActionButton>(R.id.cc_fab)
        val ccList = findViewById<ListView>(R.id.cc_list)

        val serviceListJson =
            Paper.book("system_config").read("CC_service_list", "[]").toString()
        val gson = Gson()
        val type = object : TypeToken<ArrayList<CCService>>() {}.type
        val serviceList: ArrayList<CCService> = gson.fromJson(serviceListJson, type)
        val listAdapter =
            object : ArrayAdapter<CCService>(this, R.layout.list_item_with_subtitle, serviceList) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(
                        R.layout.list_item_with_subtitle,
                        parent,
                        false
                    )
                    val item = getItem(position)

                    val title = view.findViewById<TextView>(R.id.title)
                    val subtitle = view.findViewById<TextView>(R.id.subtitle)

                    title.text =
                        ccOptions.options[item?.type!!] + item.enabled?.let { if (it) " (Enabled)" else " (Disabled)" }
                    subtitle.text = item.webhook

                    return view
                }
            }
        ccList.adapter = listAdapter
        ccList.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val dialog = inflater.inflate(R.layout.set_cc_layout, null)
                val spinner = dialog.findViewById<Spinner>(R.id.spinner_options)
                val adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_item, ccOptions.options)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
                spinner.setSelection(serviceList[position].type)
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        Log.d("spinner", position.toString())
                        when (position) {
                            0 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = false
                            1 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = true
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        Log.d("spinner", "nothing")
                    }
                }
                val webhook =
                    dialog.findViewById<EditText>(R.id.webhook_editview)
                webhook.setText(serviceList[position].webhook)
                val body =
                    dialog.findViewById<EditText>(R.id.body_editview)
                body.setText(serviceList[position].body)
                val switch =
                    dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch)
                switch.isChecked = serviceList[position].enabled
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.edit_cc_service))
                    .setView(dialog)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        CCService(
                            type = spinner.selectedItemPosition,
                            webhook = webhook.text.toString(),
                            body = body.text.toString(),
                            enabled = switch.isChecked
                        ).also { serviceList[position] = it }
                        saveAndFlush(serviceList, listAdapter)
                    }
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(
                        R.string.delete_button,
                        ((DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                            serviceList.removeAt(position)
                            saveAndFlush(serviceList, listAdapter)
                        }))
                    )
                    .show()
            }

        fab.setOnClickListener {
            val dialog = inflater.inflate(R.layout.set_cc_layout, null)
            val spinner = dialog.findViewById<Spinner>(R.id.spinner_options)

            val adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_item, ccOptions.options)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    Log.d("spinner", position.toString())
                    when (position) {
                        0 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = false
                        1 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = true
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.d("spinner", "nothing")
                }
            }
            val editText = dialog.findViewById<EditText>(R.id.webhook_editview)
            val body =
                dialog.findViewById<EditText>(R.id.body_editview)
            AlertDialog.Builder(this).setTitle(getString(R.string.add_cc_service))
                .setView(dialog)
                .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                    CCService(
                        type = spinner.selectedItemPosition,
                        webhook = editText.text.toString(),
                        body = body.text.toString(),
                        enabled = dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch).isChecked
                    ).also { serviceList.add(it) }
                    saveAndFlush(serviceList, listAdapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }


    private fun saveAndFlush(
        serviceList: ArrayList<CCService>,
        listAdapter: ArrayAdapter<CCService>
    ) {
        Log.d("save_and_flush", serviceList.toString())

        Paper.book("system_config").write("CC_service_list", Gson().toJson(serviceList))
        listAdapter.notifyDataSetChanged()
    }
}
