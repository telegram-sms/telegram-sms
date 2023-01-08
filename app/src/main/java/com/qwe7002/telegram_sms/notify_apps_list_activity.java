
package com.qwe7002.telegram_sms;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Paper;

public class notify_apps_list_activity extends AppCompatActivity {
    private notify_apps_list_activity.appAdapter appAdapter;
    private Context context;

    @NotNull
    private List<appInfo> scanAppList(PackageManager packageManager) {
        List<appInfo> appInfoList = new ArrayList<>();
        try {
            List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(0);
            for (int i = 0; i < packageInfoList.size(); i++) {
                PackageInfo packageInfo = packageInfoList.get(i);
                if (packageInfo.packageName.equals(context.getPackageName())) {
                    continue;
                }
                appInfo info = new appInfo();
                info.packageName = packageInfo.packageName;
                info.appName = packageInfo.applicationInfo.loadLabel(packageManager).toString();
                if (packageInfo.applicationInfo.loadIcon(packageManager) == null) {
                    continue;
                }
                info.appIcon = packageInfo.applicationInfo.loadIcon(packageManager);
                appInfoList.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return appInfoList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        Paper.init(context);
        this.setTitle(getString(R.string.app_list));
        setContentView(R.layout.activity_notify_apps_list);
        final ListView appList = findViewById(R.id.app_listview);
        final SearchView filterEdit = findViewById(R.id.filter_searchview);
        filterEdit.setIconifiedByDefault(false);
        appList.setTextFilterEnabled(true);
        appAdapter = new appAdapter(context);
        filterEdit.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                appAdapter.getFilter().filter(newText);
                return false;
            }
        });

        appList.setAdapter(appAdapter);
        new Thread(() -> {
            final List<appInfo> appInfoList = scanAppList(notify_apps_list_activity.this.getPackageManager());
            runOnUiThread(() -> {
                ProgressBar progressBar = findViewById(R.id.progress_view);
                progressBar.setVisibility(View.GONE);
                appAdapter.setData(appInfoList);
            });
        }).start();
    }

    static class appAdapter extends BaseAdapter implements Filterable {
        final String TAG = "notify_activity";
        List<String> listenList;
        List<appInfo> appInfoList = new ArrayList<>();
        List<appInfo> viewAppInfoList = new ArrayList<>();
        private final Context context;
        private final Filter filter = new Filter() {
            @NotNull
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<appInfo> list = new ArrayList<>();
                for (appInfo appInfoItem : appInfoList) {
                    if (appInfoItem.appName.toLowerCase().contains(constraint.toString().toLowerCase())) {
                        list.add(appInfoItem);
                    }
                }
                results.values = list;
                results.count = list.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, @NotNull FilterResults results) {
                viewAppInfoList = (ArrayList<appInfo>) results.values;
                notifyDataSetChanged();
            }
        };

        appAdapter(Context context) {
            this.context = context;
            this.listenList = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
        }

        public List<appInfo> getData() {
            return appInfoList;
        }

        public void setData(List<appInfo> appInfoList) {
            this.appInfoList = appInfoList;
            this.viewAppInfoList = this.appInfoList;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            if (viewAppInfoList != null && viewAppInfoList.size() > 0) {
                return viewAppInfoList.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            if (viewAppInfoList != null && viewAppInfoList.size() > 0) {
                return viewAppInfoList.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            viewHolder viewHolderObj;
            appInfo appInfoObj = viewAppInfoList.get(position);
            if (convertView == null) {
                viewHolderObj = new viewHolder();
                convertView = LayoutInflater.from(context).inflate(R.layout.item_app_info, parent, false);
                viewHolderObj.appIcon = convertView.findViewById(R.id.app_icon_imageview);
                viewHolderObj.packageName = convertView.findViewById(R.id.package_name_textview);
                viewHolderObj.appName = convertView.findViewById(R.id.app_name_textview);
                viewHolderObj.appCheckbox = convertView.findViewById(R.id.select_checkbox);
                convertView.setTag(viewHolderObj);
            } else {
                viewHolderObj = (viewHolder) convertView.getTag();
            }
            viewHolderObj.appIcon.setImageDrawable(appInfoObj.appIcon);
            viewHolderObj.appName.setText(appInfoObj.appName);
            viewHolderObj.packageName.setText(appInfoObj.packageName);
            viewHolderObj.appCheckbox.setChecked(listenList.contains(appInfoObj.packageName));
            viewHolderObj.appCheckbox.setOnClickListener(v -> {
                appInfo itemInfo = (appInfo) getItem(position);
                String packageName = itemInfo.packageName;
                List<String> listenListTemp = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
                if (viewHolderObj.appCheckbox.isChecked()) {
                    assert listenListTemp != null;
                    if (!listenListTemp.contains(packageName)) {
                        listenListTemp.add(packageName);
                    }
                } else {
                    assert listenListTemp != null;
                    listenListTemp.remove(packageName);
                }
                Log.d(TAG, "notify_listen_list: " + listenListTemp);
                Paper.book("system_config").write("notify_listen_list", listenListTemp);
                listenList = listenListTemp;
            });
            return convertView;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        static class viewHolder {
            ImageView appIcon;
            TextView appName;
            TextView packageName;
            CheckBox appCheckbox;
        }

    }

    static class appInfo {
        Drawable appIcon;
        String packageName;
        String appName;
    }
}
