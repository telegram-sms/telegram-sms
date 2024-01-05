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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Paper;

public class NotifyAppsListActivity extends AppCompatActivity {
    private app_adapter app_adapter;
    private Context context;

    @NotNull
    private List<app_info> scan_app_list(PackageManager packageManager) {
        List<app_info> app_info_list = new ArrayList<>();
        try {
            List<PackageInfo> package_info_list = packageManager.getInstalledPackages(0);
            for (int i = 0; i < package_info_list.size(); i++) {
                PackageInfo package_info = package_info_list.get(i);
                if (package_info.packageName.equals(context.getPackageName())) {
                    continue;
                }
                app_info app_info = new app_info();
                app_info.package_name = package_info.packageName;
                app_info.app_name = package_info.applicationInfo.loadLabel(packageManager).toString();
                if (package_info.applicationInfo.loadIcon(packageManager) == null) {
                    continue;
                }
                app_info.app_icon = package_info.applicationInfo.loadIcon(packageManager);
                app_info_list.add(app_info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return app_info_list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        Paper.init(context);
        this.setTitle(getString(R.string.app_list));
        setContentView(R.layout.activity_notify_apps_list);
        final ListView app_list = findViewById(R.id.app_listview);
        final SearchView filter_edit = findViewById(R.id.filter_searchview);
        filter_edit.setIconifiedByDefault(false);
        app_list.setTextFilterEnabled(true);
        app_adapter = new app_adapter(context);
        filter_edit.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                app_adapter.getFilter().filter(newText);
                return false;
            }
        });

        app_list.setAdapter(app_adapter);
        new Thread(() -> {
            final List<app_info> app_info_list = scan_app_list(NotifyAppsListActivity.this.getPackageManager());
            runOnUiThread(() -> {
                ProgressBar scan_label = findViewById(R.id.progress_view);
                scan_label.setVisibility(View.GONE);
                app_adapter.setData(app_info_list);
            });
        }).start();
    }

    static class app_adapter extends BaseAdapter implements Filterable {
        final String TAG = "notify_activity";
        List<String> listen_list;
        List<app_info> app_info_list = new ArrayList<>();
        List<app_info> view_app_info_list = new ArrayList<>();
        private final Context context;
        private final Filter filter = new Filter() {
            @NotNull
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<app_info> list = new ArrayList<>();
                for (app_info app_info_item : app_info_list) {
                    if (app_info_item.app_name.toLowerCase().contains(constraint.toString().toLowerCase())) {
                        list.add(app_info_item);
                    }
                }
                results.values = list;
                results.count = list.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, @NotNull FilterResults results) {
                view_app_info_list = (ArrayList<app_info>) results.values;
                notifyDataSetChanged();
            }
        };

        app_adapter(Context context) {
            this.context = context;
            this.listen_list = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
        }

        public List<app_info> getData() {
            return app_info_list;
        }

        public void setData(List<app_info> apps_info_list) {
            this.app_info_list = apps_info_list;
            this.view_app_info_list = app_info_list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            if (view_app_info_list != null && view_app_info_list.size() > 0) {
                return view_app_info_list.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            if (view_app_info_list != null && view_app_info_list.size() > 0) {
                return view_app_info_list.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convert_view, ViewGroup parent) {
            view_holder view_holder_object;
            app_info app_info = view_app_info_list.get(position);
            if (convert_view == null) {
                view_holder_object = new view_holder();
                convert_view = LayoutInflater.from(context).inflate(R.layout.item_app_info, parent, false);
                view_holder_object.app_icon = convert_view.findViewById(R.id.app_icon_imageview);
                view_holder_object.package_name = convert_view.findViewById(R.id.package_name_textview);
                view_holder_object.app_name = convert_view.findViewById(R.id.app_name_textview);
                view_holder_object.app_checkbox = convert_view.findViewById(R.id.select_checkbox);
                convert_view.setTag(view_holder_object);
            } else {
                view_holder_object = (NotifyAppsListActivity.app_adapter.view_holder) convert_view.getTag();
            }
            view_holder_object.app_icon.setImageDrawable(app_info.app_icon);
            view_holder_object.app_name.setText(app_info.app_name);
            view_holder_object.package_name.setText(app_info.package_name);
            view_holder_object.app_checkbox.setChecked(listen_list.contains(app_info.package_name));
            view_holder_object.app_checkbox.setOnClickListener(v -> {
                app_info item_info = (app_info) getItem(position);
                String package_name = item_info.package_name;
                List<String> listen_list_temp = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
                if (view_holder_object.app_checkbox.isChecked()) {
                    if (!listen_list_temp.contains(package_name)) {
                        listen_list_temp.add(package_name);
                    }
                } else {
                    listen_list_temp.remove(package_name);
                }
                Log.d(TAG, "notify_listen_list: " + listen_list_temp);
                Paper.book("system_config").write("notify_listen_list", listen_list_temp);
                listen_list = listen_list_temp;
            });
            return convert_view;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        static class view_holder {
            ImageView app_icon;
            TextView app_name;
            TextView package_name;
            CheckBox app_checkbox;
        }

    }

    static class app_info {
        Drawable app_icon;
        String package_name;
        String app_name;
    }
}
