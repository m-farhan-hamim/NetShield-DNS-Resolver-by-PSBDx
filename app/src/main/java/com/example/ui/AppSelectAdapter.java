package com.example.ui;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppSelectAdapter extends RecyclerView.Adapter<AppSelectAdapter.AppViewHolder> {

    public static class AppItem {
        public String label;
        public String packageName;
        public Drawable icon;
        public boolean isSelected;
        /** A built-in system app that was never updated. Hidden unless "Show system apps" is on. */
        public boolean isSystem;

        public AppItem(String label, String packageName, Drawable icon, boolean isSelected) {
            this(label, packageName, icon, isSelected, false);
        }

        public AppItem(String label, String packageName, Drawable icon, boolean isSelected, boolean isSystem) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.isSelected = isSelected;
            this.isSystem = isSystem;
        }
    }

    /** Every app, whatever the search box says. {@link #items} is the filtered view of it. */
    private final List<AppItem> allItems = new ArrayList<>();
    private final List<AppItem> items = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private String query = "";
    private boolean showSystem = true;

    /** Icons are loaded only for the rows that are on screen, a few at a time, and kept in a small cache. */
    private PackageManager iconSource;
    private ExecutorService iconExecutor;
    private final LruCache<String, Drawable> iconCache = new LruCache<>(120);

    /** Turns on lazy icon loading for items that were created without an icon. */
    public void setIconSource(PackageManager pm) {
        this.iconSource = pm;
        if (iconExecutor == null) {
            iconExecutor = Executors.newFixedThreadPool(2);
        }
    }

    /** Call when the picker closes. */
    public void shutdown() {
        if (iconExecutor != null) {
            iconExecutor.shutdownNow();
        }
    }

    public void setShowSystem(boolean show) {
        showSystem = show;
        applyFilter();
    }

    /** True when any ticked app is a built-in system app (so the picker should show them by default). */
    public boolean hasSelectedSystemApp() {
        for (AppItem item : allItems) {
            if (item.isSystem && selectedPackages.contains(item.packageName)) {
                return true;
            }
        }
        return false;
    }

    public void setItems(List<AppItem> newItems, Set<String> initialSelected) {
        allItems.clear();
        selectedPackages.clear();
        if (initialSelected != null) {
            selectedPackages.addAll(initialSelected);
        }
        if (newItems != null) {
            for (AppItem item : newItems) {
                item.isSelected = selectedPackages.contains(item.packageName);
                allItems.add(item);
            }
        }
        applyFilter();
    }

    /**
     * Swaps the ticked apps (used when the routing mode changes and the other mode's list has to
     * be shown) without reloading the app list.
     */
    public void replaceSelection(Set<String> selected) {
        selectedPackages.clear();
        if (selected != null) {
            selectedPackages.addAll(selected);
        }
        for (AppItem item : allItems) {
            item.isSelected = selectedPackages.contains(item.packageName);
        }
        notifyDataSetChanged();
    }

    /** Shows only apps whose name or package contains the text. Ticks are kept for hidden apps. */
    public void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        items.clear();
        for (AppItem item : allItems) {
            if (item.isSystem && !showSystem) {
                continue;
            }
            if (query.isEmpty()
                    || item.label.toLowerCase(Locale.ROOT).contains(query)
                    || item.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                items.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedPackages() {
        return new HashSet<>(selectedPackages);
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_select, parent, false);
        return new AppViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final AppViewHolder holder, int position) {
        final AppItem item = items.get(position);
        holder.tvName.setText(item.label);
        holder.tvPackage.setText(item.packageName);
        bindIcon(holder.ivIcon, item);

        holder.cbSelected.setOnCheckedChangeListener(null);
        holder.cbSelected.setChecked(item.isSelected);
        holder.cbSelected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                item.isSelected = isChecked;
                if (isChecked) {
                    selectedPackages.add(item.packageName);
                } else {
                    selectedPackages.remove(item.packageName);
                }
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder.cbSelected.setChecked(!holder.cbSelected.isChecked());
            }
        });
    }

    private void bindIcon(final ImageView view, final AppItem item) {
        final String pkg = item.packageName;
        view.setTag(pkg);
        if (item.icon != null) {
            view.setImageDrawable(item.icon);
            return;
        }
        Drawable cached = iconCache.get(pkg);
        if (cached != null) {
            view.setImageDrawable(cached);
            return;
        }
        view.setImageDrawable(null); // blank until the real icon arrives
        final PackageManager pm = iconSource;
        final ExecutorService exec = iconExecutor;
        if (pm == null || exec == null) {
            view.setImageResource(R.drawable.ic_shield);
            return;
        }
        try {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    Drawable loaded;
                    try {
                        loaded = pm.getApplicationIcon(pkg);
                    } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                        return;
                    }
                    iconCache.put(pkg, loaded);
                    final Drawable result = loaded;
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            // the row may have been reused for another app while we were loading
                            if (pkg.equals(view.getTag())) {
                                view.setImageDrawable(result);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            // the picker was closed
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvPackage;
        CheckBox cbSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            tvName = itemView.findViewById(R.id.tv_app_name);
            tvPackage = itemView.findViewById(R.id.tv_app_package);
            cbSelected = itemView.findViewById(R.id.cb_app_selected);
        }
    }
}
