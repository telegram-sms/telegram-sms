package com.airfreshener.telegram_sms.notification_screen

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
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import java.util.Locale

class NotifyAppsListActivity : AppCompatActivity() {

    private var appAdapter: AppAdapter? = null

    private fun scanAppList(packageManager: PackageManager): List<AppInfo> {
        val appInfoList: MutableList<AppInfo> = ArrayList()
        try {
            val packageInfoList = packageManager.getInstalledPackages(0)
            for (packageInfo in packageInfoList) {
                if (packageInfo.packageName == applicationContext.packageName) {
                    continue
                }
                val appInfo = AppInfo()
                appInfo.packageName = packageInfo.packageName
                appInfo.appName = packageInfo.applicationInfo.loadLabel(packageManager).toString()
                if (packageInfo.applicationInfo.loadIcon(packageManager) == null) {
                    continue
                }
                appInfo.appIcon = packageInfo.applicationInfo.loadIcon(packageManager)
                appInfoList.add(appInfo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return appInfoList
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.title = getString(R.string.app_list)
        setContentView(R.layout.activity_notify_apps_list)
        val appList = findViewById<ListView>(R.id.app_listview)
        val filterEdit = findViewById<SearchView>(R.id.filter_searchview)
        filterEdit.isIconifiedByDefault = false
        appList.isTextFilterEnabled = true
        appAdapter = AppAdapter()
        filterEdit.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                appAdapter!!.filter.filter(newText)
                return false
            }
        })
        appList.adapter = appAdapter
        Thread {
            val appInfoList = scanAppList(this@NotifyAppsListActivity.packageManager)
            runOnUiThread {
                val scanLabel = findViewById<ProgressBar>(R.id.progress_view)
                scanLabel.visibility = View.GONE
                appAdapter!!.data = appInfoList
            }
        }.start()
    }

    internal class AppAdapter : BaseAdapter(), Filterable {
        val TAG = "notify_activity"
        var listenList: List<String?> = SYSTEM_BOOK.tryRead("notify_listen_list", ArrayList())
        var appInfoList: List<AppInfo> = ArrayList()
        val viewAppInfoList: ArrayList<AppInfo> = ArrayList()
        private val filter: Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val results = FilterResults()
                val list: MutableList<AppInfo> = ArrayList()
                for (appInfoItem in appInfoList) {
                    if (appInfoItem.appName!!.lowercase(Locale.getDefault()).contains(
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
                viewAppInfoList.clear()
                viewAppInfoList.addAll(results.values as ArrayList<AppInfo>)
                notifyDataSetChanged()
            }
        }

        var data: List<AppInfo>
            get() = appInfoList
            set(appsInfoList) {
                appInfoList = appsInfoList
                viewAppInfoList.clear()
                viewAppInfoList.addAll(appInfoList)
                notifyDataSetChanged()
            }

        override fun getCount(): Int = viewAppInfoList.size
        override fun getItem(position: Int): Any = viewAppInfoList[position]
        override fun getItemId(position: Int): Long = 0

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val viewHolderObject: ViewHolder
            val appInfo = viewAppInfoList[position]
            if (convertView == null) {
                viewHolderObject = ViewHolder()
                val inflater = LayoutInflater.from(parent.context)
                convertView = inflater.inflate(R.layout.item_app_info, parent, false)
                viewHolderObject.appIcon = convertView.findViewById(R.id.app_icon_imageview)
                viewHolderObject.packageName = convertView.findViewById(R.id.package_name_textview)
                viewHolderObject.appName = convertView.findViewById(R.id.app_name_textview)
                viewHolderObject.appCheckbox = convertView.findViewById(R.id.select_checkbox)
                convertView.tag = viewHolderObject
            } else {
                viewHolderObject = convertView.tag as ViewHolder
            }
            viewHolderObject.appIcon.setImageDrawable(appInfo.appIcon)
            viewHolderObject.appName.text = appInfo.appName
            viewHolderObject.packageName.text = appInfo.packageName
            viewHolderObject.appCheckbox.isChecked = listenList.contains(appInfo.packageName)
            viewHolderObject.appCheckbox.setOnClickListener { v: View? ->
                val itemInfo = getItem(position) as AppInfo
                val packageName = itemInfo.packageName
                val listenListTemp: MutableList<String?> =
                    SYSTEM_BOOK.read("notify_listen_list", ArrayList())!!
                if (viewHolderObject.appCheckbox.isChecked) {
                    if (!listenListTemp.contains(packageName)) {
                        listenListTemp.add(packageName)
                    }
                } else {
                    listenListTemp.remove(packageName)
                }
                Log.d(TAG, "notify_listen_list: $listenListTemp")
                SYSTEM_BOOK.write<List<String?>>("notify_listen_list", listenListTemp)
                listenList = listenListTemp
            }
            return requireNotNull(convertView)
        }

        override fun getFilter(): Filter = filter

        internal class ViewHolder {
            lateinit var appIcon: ImageView
            lateinit var appName: TextView
            lateinit var packageName: TextView
            lateinit var appCheckbox: CheckBox
        }
    }

    internal class AppInfo {
        var appIcon: Drawable? = null
        var packageName: String? = null
        var appName: String? = null
    }
}
