package com.airfreshener.telegram_sms.notification_screen;

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

import com.airfreshener.telegram_sms.R;
import com.airfreshener.telegram_sms.utils.PaperUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NotifyAppsListActivity extends AppCompatActivity {
    private AppAdapter appAdapter;
    private Context context;

    @NotNull
    private List<AppInfo> scanAppList(PackageManager packageManager) {
        List<AppInfo> appInfoList = new ArrayList<>();
        try {
            List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(0);
            for (int i = 0; i < packageInfoList.size(); i++) {
                PackageInfo packageInfo = packageInfoList.get(i);
                if (packageInfo.packageName.equals(context.getPackageName())) {
                    continue;
                }
                AppInfo appInfo = new AppInfo();
                appInfo.packageName = packageInfo.packageName;
                appInfo.appName = packageInfo.applicationInfo.loadLabel(packageManager).toString();
                if (packageInfo.applicationInfo.loadIcon(packageManager) == null) {
                    continue;
                }
                appInfo.appIcon = packageInfo.applicationInfo.loadIcon(packageManager);
                appInfoList.add(appInfo);
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
        PaperUtils.init(context);
        this.setTitle(getString(R.string.app_list));
        setContentView(R.layout.activity_notify_apps_list);
        final ListView appList = findViewById(R.id.app_listview);
        final SearchView filterEdit = findViewById(R.id.filter_searchview);
        filterEdit.setIconifiedByDefault(false);
        appList.setTextFilterEnabled(true);
        appAdapter = new AppAdapter(context);
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
            final List<AppInfo> appInfoList = scanAppList(NotifyAppsListActivity.this.getPackageManager());
            runOnUiThread(() -> {
                ProgressBar scanLabel = findViewById(R.id.progress_view);
                scanLabel.setVisibility(View.GONE);
                appAdapter.setData(appInfoList);
            });
        }).start();
    }

    static class AppAdapter extends BaseAdapter implements Filterable {
        final String TAG = "notify_activity";
        List<String> listenList;
        List<AppInfo> appInfoList = new ArrayList<>();
        List<AppInfo> viewAppInfoList = new ArrayList<>();
        private final Context context;
        private final Filter filter = new Filter() {
            @NotNull
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<AppInfo> list = new ArrayList<>();
                for (AppInfo appInfoItem : appInfoList) {
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
                viewAppInfoList = (ArrayList<AppInfo>) results.values;
                notifyDataSetChanged();
            }
        };

        AppAdapter(Context context) {
            this.context = context;
            this.listenList = PaperUtils.getSystemBook().read("notify_listen_list", new ArrayList<>());
        }

        public List<AppInfo> getData() {
            return appInfoList;
        }

        public void setData(List<AppInfo> appsInfoList) {
            this.appInfoList = appsInfoList;
            this.viewAppInfoList = appInfoList;
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
            ViewHolder viewHolderObject;
            AppInfo appInfo = viewAppInfoList.get(position);
            if (convertView == null) {
                viewHolderObject = new ViewHolder();
                convertView = LayoutInflater.from(context).inflate(R.layout.item_app_info, parent, false);
                viewHolderObject.appIcon = convertView.findViewById(R.id.app_icon_imageview);
                viewHolderObject.packageName = convertView.findViewById(R.id.package_name_textview);
                viewHolderObject.appName = convertView.findViewById(R.id.app_name_textview);
                viewHolderObject.appCheckbox = convertView.findViewById(R.id.select_checkbox);
                convertView.setTag(viewHolderObject);
            } else {
                viewHolderObject = (ViewHolder) convertView.getTag();
            }
            viewHolderObject.appIcon.setImageDrawable(appInfo.appIcon);
            viewHolderObject.appName.setText(appInfo.appName);
            viewHolderObject.packageName.setText(appInfo.packageName);
            viewHolderObject.appCheckbox.setChecked(listenList.contains(appInfo.packageName));
            viewHolderObject.appCheckbox.setOnClickListener(v -> {
                AppInfo itemInfo = (AppInfo) getItem(position);
                String packageName = itemInfo.packageName;
                List<String> listenListTemp = PaperUtils.getSystemBook().read("notify_listen_list", new ArrayList<>());
                if (viewHolderObject.appCheckbox.isChecked()) {
                    if (!listenListTemp.contains(packageName)) {
                        listenListTemp.add(packageName);
                    }
                } else {
                    listenListTemp.remove(packageName);
                }
                Log.d(TAG, "notify_listen_list: " + listenListTemp);
                PaperUtils.getSystemBook().write("notify_listen_list", listenListTemp);
                listenList = listenListTemp;
            });
            return convertView;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        static class ViewHolder {
            ImageView appIcon;
            TextView appName;
            TextView packageName;
            CheckBox appCheckbox;
        }

    }

    static class AppInfo {
        Drawable appIcon;
        String packageName;
        String appName;
    }
}
