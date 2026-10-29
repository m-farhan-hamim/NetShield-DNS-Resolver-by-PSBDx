package com.example.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.util.AppRouting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * "Which apps use NetShield" picker, shared by the Settings tile and the proxy screen so both
 * show the same setting. Two modes (see {@link AppRouting}): exclude the chosen apps, or include
 * ONLY the chosen apps.
 */
public final class AppRoutingDialog {

    private AppRoutingDialog() {
    }

    public static void show(final Activity activity, final SharedPreferences prefs,
                            final Executor background, @Nullable final Runnable onSaved) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_split_tunnel, null);
        final TextView tvTitle = view.findViewById(R.id.tv_split_title);
        final TextView tvDesc = view.findViewById(R.id.tv_split_desc);
        final RadioGroup rgMode = view.findViewById(R.id.rg_routing_mode);
        final RadioButton rbExclude = view.findViewById(R.id.rb_mode_exclude);
        final RadioButton rbInclude = view.findViewById(R.id.rb_mode_include);
        final EditText etSearch = view.findViewById(R.id.et_search_apps);
        final ProgressBar pbLoading = view.findViewById(R.id.pb_loading_apps);
        final RecyclerView rvApps = view.findViewById(R.id.rv_split_apps);

        tvTitle.setText(R.string.routing_dialog_title);
        rgMode.setVisibility(View.VISIBLE);

        rvApps.setLayoutManager(new LinearLayoutManager(activity));
        final AppSelectAdapter adapter = new AppSelectAdapter();
        rvApps.setAdapter(adapter);
        pbLoading.setVisibility(View.VISIBLE);

        // Two working copies, so flipping the mode back and forth inside the dialog never loses ticks.
        final Set<String> excludedWork = AppRouting.getExcluded(prefs);
        final Set<String> includedWork = AppRouting.getIncluded(prefs);
        final boolean[] includeMode = {AppRouting.isIncludeMode(prefs)};
        // Until the app list has loaded the adapter knows no ticks yet, so the working copies are the truth.
        final boolean[] loaded = {false};

        rbExclude.setChecked(!includeMode[0]);
        rbInclude.setChecked(includeMode[0]);
        tvDesc.setText(includeMode[0] ? R.string.routing_desc_include : R.string.routing_desc_exclude);

        rgMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean nowInclude = checkedId == R.id.rb_mode_include;
                if (nowInclude == includeMode[0]) {
                    return;
                }
                // Park the ticks of the mode we are leaving, then show the other mode's ticks.
                if (loaded[0]) {
                    Set<String> leaving = includeMode[0] ? includedWork : excludedWork;
                    leaving.clear();
                    leaving.addAll(adapter.getSelectedPackages());
                }
                includeMode[0] = nowInclude;
                adapter.replaceSelection(nowInclude ? includedWork : excludedWork);
                tvDesc.setText(nowInclude ? R.string.routing_desc_include : R.string.routing_desc_exclude);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });

        final Context appContext = activity.getApplicationContext();
        background.execute(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = appContext.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                final List<AppSelectAdapter.AppItem> items = new ArrayList<>();
                for (ApplicationInfo app : apps) {
                    try {
                        String label = pm.getApplicationLabel(app).toString();
                        Drawable icon = pm.getApplicationIcon(app);
                        items.add(new AppSelectAdapter.AppItem(label, app.packageName, icon, false));
                    } catch (Exception ignored) {
                    }
                }
                Collections.sort(items, new Comparator<AppSelectAdapter.AppItem>() {
                    @Override
                    public int compare(AppSelectAdapter.AppItem a, AppSelectAdapter.AppItem b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pbLoading.setVisibility(View.GONE);
                        adapter.setItems(items, includeMode[0] ? includedWork : excludedWork);
                        loaded[0] = true;
                    }
                });
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setPositiveButton(R.string.routing_save, null) // real handler below, so it can refuse to close
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Set<String> current = loaded[0]
                                ? new HashSet<>(adapter.getSelectedPackages())
                                : new HashSet<>(includeMode[0] ? includedWork : excludedWork);
                        Set<String> excluded = includeMode[0] ? excludedWork : current;
                        Set<String> included = includeMode[0] ? current : includedWork;
                        if (includeMode[0] && included.isEmpty()) {
                            // Include mode with nothing ticked would route nothing at all; make that explicit.
                            Toast.makeText(activity, R.string.routing_include_empty, Toast.LENGTH_LONG).show();
                            return;
                        }
                        AppRouting.save(prefs, includeMode[0], excluded, included);
                        Toast.makeText(activity, R.string.routing_saved, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        if (onSaved != null) {
                            onSaved.run();
                        }
                    }
                });
            }
        });
        dialog.show();
    }
}
