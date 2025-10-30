package com.qwe7002.telegram_sms

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.tencent.mmkv.MMKV
import java.util.Locale

class NotifyActivity : AppCompatActivity() {
    private lateinit var appAdapter: AppAdapter

    private fun scanAppList(packageManager: PackageManager): List<applicationInfo> {
        val appInfoList: MutableList<applicationInfo> = ArrayList()
        try {
            val packageInfoList = packageManager.getInstalledPackages(0)
            for (i in packageInfoList.indices) {
                val packageInfo = packageInfoList[i]
                val appInfo = applicationInfo()
                if (packageInfo.packageName == applicationContext.packageName) {
                    continue
                }
                appInfo.packageName = packageInfo.packageName
                appInfo.appName = packageInfo.applicationInfo?.loadLabel(packageManager).toString()
                if (packageInfo.applicationInfo!!.loadIcon(packageManager) == null) {
                    continue
                }
                appInfo.appIcon = packageInfo.applicationInfo!!.loadIcon(packageManager)
                appInfoList.add(appInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return appInfoList
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MMKV.initialize(applicationContext)
        this.title = getString(R.string.app_list)
        setContentView(R.layout.activity_notify_apps_list)
        val appList = findViewById<ListView>(R.id.app_listview)
        val filterEdit = findViewById<SearchView>(R.id.filter_searchview)
        filterEdit.isIconifiedByDefault = false
        appList.isTextFilterEnabled = true
        appAdapter = AppAdapter(applicationContext)
        filterEdit.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                appAdapter.filter.filter(newText)
                return false
            }
        })

        appList.adapter = appAdapter
        Thread {
            val appInfoList = scanAppList(this@NotifyActivity.packageManager)
            runOnUiThread {
                val scanLabel = findViewById<ProgressBar>(R.id.progress_view)
                scanLabel.visibility = View.GONE
                appAdapter.data = appInfoList
            }
        }.start()
    }

    internal class AppAdapter(private val context: Context?) : BaseAdapter(), Filterable {
        val TAG: String = "notify_activity"
        private var listenList: List<String>
        var appInfoList: List<applicationInfo> = ArrayList()
        var viewAppInfoList: List<applicationInfo> = ArrayList()
        var notifyMMKV = MMKV.mmkvWithID(MMKVConst.NOTIFY_ID)

        @Suppress("UNCHECKED_CAST")
        private val filter: Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val results = FilterResults()
                val list: MutableList<applicationInfo> = ArrayList()
                for (appInfoItem in appInfoList) {
                    if (appInfoItem.appName.lowercase(Locale.getDefault()).contains(
                            constraint.toString().lowercase(
                                Locale.getDefault()
                            )
                        )
                    ) {
                        list.add(appInfoItem)
                    }
                }
                results.values = list
                results.count = list.size
                return results
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                viewAppInfoList = results.values as ArrayList<applicationInfo>
                notifyDataSetChanged()
            }
        }

        init {
            //this.listenList = Paper.book("system_config").read("notify_listen_list", ArrayList())!!
            val notifyListStr = notifyMMKV.getString("listen_list", "[]")
            this.listenList =
                Gson().fromJson(notifyListStr, Array<String>::class.java).toList()
        }

        var data: List<applicationInfo>
            get() = appInfoList
            set(appsInfoList) {
                this.appInfoList = appsInfoList
                this.viewAppInfoList = appInfoList
                notifyDataSetChanged()
            }

        override fun getCount(): Int {
            if (viewAppInfoList.isNotEmpty()) {
                return viewAppInfoList.size
            }
            return 0
        }

        override fun getItem(position: Int): Any {
            if (viewAppInfoList.isNotEmpty()) {
                return viewAppInfoList[position]
            }
            return applicationInfo()
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            var view = convertView
            val viewHolderObject: holder
            val appInfo = viewAppInfoList[position]
            if (view == null) {
                viewHolderObject = holder()
                view =
                    LayoutInflater.from(context).inflate(R.layout.item_app_info, parent, false)
                viewHolderObject.appIcon = view.findViewById(R.id.app_icon_imageview)
                viewHolderObject.packageName =
                    view.findViewById(R.id.package_name_textview)
                viewHolderObject.appName = view.findViewById(R.id.app_name_textview)
                viewHolderObject.appCheckbox = view.findViewById(R.id.select_checkbox)
                view.tag = viewHolderObject
            } else {
                viewHolderObject = view.tag as holder
            }
            viewHolderObject.appIcon.setImageDrawable(appInfo.appIcon)
            viewHolderObject.appName.text = appInfo.appName
            viewHolderObject.packageName.text = appInfo.packageName
            viewHolderObject.appCheckbox.isChecked =
                listenList.contains(appInfo.packageName)
            viewHolderObject.appCheckbox.setOnClickListener {
                val itemInfo = getItem(position) as applicationInfo
                val packageName = itemInfo.packageName

                val listenListTemp: MutableList<String> = MMKV.defaultMMKV()
                    .getStringSet("notify_listen_list", setOf())?.toMutableList() ?: mutableListOf()
                if (viewHolderObject.appCheckbox.isChecked) {
                    if (!listenListTemp.contains(packageName)) {
                        listenListTemp.add(packageName)
                    }
                } else {
                    listenListTemp.remove(packageName)
                }
                Log.d(TAG, "notify_listen_list: $listenListTemp")
                MMKV.defaultMMKV().encode("notify_listen_list", listenListTemp.toSet())
                listenList = listenListTemp
            }
            return view
        }

        override fun getFilter(): Filter {
            return filter
        }

        internal class holder {
            lateinit var appIcon: ImageView
            lateinit var appName: TextView
            lateinit var packageName: TextView
            lateinit var appCheckbox: CheckBox
        }
    }

    internal class applicationInfo {
        lateinit var appIcon: Drawable
        lateinit var packageName: String
        lateinit var appName: String
    }
}
