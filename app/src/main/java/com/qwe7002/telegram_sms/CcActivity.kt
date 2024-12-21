package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.ccOptions
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import org.json.JSONArray
import org.json.JSONObject

class CcActivity : AppCompatActivity() {
    private lateinit var listAdapter: ArrayAdapter<CcSendService>
    private lateinit var serviceList: ArrayList<CcSendService>
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
        val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
        serviceList = gson.fromJson(serviceListJson, type)
        listAdapter =
            object :
                ArrayAdapter<CcSendService>(this, R.layout.list_item_with_subtitle, serviceList) {
                @SuppressLint("SetTextI18n")
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
                        ccOptions.options[item?.method!!] + item.enabled.let { if (it) " (Enabled)" else " (Disabled)" }
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
                spinner.setSelection(serviceList[position].method)
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
                webhook.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidUrl(text)) {
                            webhook.error = "Invalid URL"
                        }
                    }
                })
                val body =
                    dialog.findViewById<EditText>(R.id.body_editview)
                body.setText(serviceList[position].body)
                body.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidJson(text)) {
                            body.error = "Invalid JSON"
                        }
                    }
                })
                val header =
                    dialog.findViewById<EditText>(R.id.header_editview)
                header.setText(serviceList[position].header)
                header.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidJson(text)) {
                            header.error = "Invalid JSON"
                        }
                    }
                })
                val switch =
                    dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch)
                switch.isChecked = serviceList[position].enabled
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.edit_cc_service))
                    .setView(dialog)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        CcSendService(
                            method = spinner.selectedItemPosition,
                            webhook = webhook.text.toString(),
                            body = body.text.toString(),
                            header = header.text.toString(),
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
            val webhook = dialog.findViewById<EditText>(R.id.webhook_editview)
            webhook.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidUrl(text)) {
                        webhook.error = "Invalid URL"
                    }
                }
            })
            val body =
                dialog.findViewById<EditText>(R.id.body_editview)
            body.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidJson(text)) {
                        body.error = "Invalid JSON"
                    }
                }
            })
            val header =
                dialog.findViewById<EditText>(R.id.header_editview)
            header.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidJson(text)) {
                        header.error = "Invalid JSON"
                    }
                }
            })
            AlertDialog.Builder(this).setTitle(getString(R.string.add_cc_service))
                .setView(dialog)
                .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                    CcSendService(
                        method = spinner.selectedItemPosition,
                        webhook = webhook.text.toString(),
                        body = body.text.toString(),
                        header = header.text.toString(),
                        enabled = dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch).isChecked
                    ).also { serviceList.add(it) }
                    saveAndFlush(serviceList, listAdapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("https://")
    }

    private fun isValidJson(json: String): Boolean {
        return try {
            JSONObject(json)
            true
        } catch (ex: Exception) {
            try {
                JSONArray(json)
                true
            } catch (ex1: Exception) {
                false
            }
        }
    }

    private fun saveAndFlush(
        serviceList: ArrayList<CcSendService>,
        listAdapter: ArrayAdapter<CcSendService>
    ) {
        Log.d("save_and_flush", serviceList.toString())
        Paper.book("system_config").write("CC_service_list", Gson().toJson(serviceList))
        listAdapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cc_menu, menu)
        return true
    }

    @SuppressLint("NonConstantResourceId", "SetTextI18n")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
                return true
            }

            R.id.send_test_menu_item -> {
                if (serviceList.isEmpty()) {
                    Snackbar.make(
                        findViewById(R.id.send_test_menu_item),
                        "No service available.",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return true
                }
                CcSendJob.startJob(
                    applicationContext,
                    getString(R.string.app_name),
                    Template.render(
                        applicationContext,
                        "TPL_system_message",
                        mapOf("Message" to "This is a test message.")
                    )
                )
                Snackbar.make(
                    findViewById(R.id.send_test_menu_item),
                    "Test message sent.",
                    Snackbar.LENGTH_SHORT
                ).show()
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("CcActivity", "onRequestPermissionsResult: No camera permissions.")
                    Snackbar.make(
                        findViewById(R.id.bot_token_editview),
                        R.string.no_camera_permission,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(applicationContext, ScannerActivity::class.java)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 1)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("onActivityResult", "onActivityResult: $resultCode")
        if (requestCode == 1) {
            if (resultCode == constValue.RESULT_CONFIG_JSON) {
                val gson = Gson()
                val jsonConfig = gson.fromJson(
                    data!!.getStringExtra("config_json"),
                    CcSendService::class.java
                )
                Log.d("onActivityResult", "onActivityResult: $jsonConfig")
                serviceList.add(jsonConfig)
                saveAndFlush(serviceList, listAdapter)
            }
        }
    }
}
