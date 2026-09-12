package com.example.ui;

import android.graphics.drawable.Drawable;
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
import java.util.Set;

public class AppSelectAdapter extends RecyclerView.Adapter<AppSelectAdapter.AppViewHolder> {

    public static class AppItem {
        public String label;
        public String packageName;
        public Drawable icon;
        public boolean isSelected;

        public AppItem(String label, String packageName, Drawable icon, boolean isSelected) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.isSelected = isSelected;
        }
    }

    private final List<AppItem> items = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();

    public void setItems(List<AppItem> newItems, Set<String> initialSelected) {
        items.clear();
        selectedPackages.clear();
        if (initialSelected != null) {
            selectedPackages.addAll(initialSelected);
        }
        if (newItems != null) {
            for (AppItem item : newItems) {
                item.isSelected = selectedPackages.contains(item.packageName);
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
        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_shield);
        }

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
