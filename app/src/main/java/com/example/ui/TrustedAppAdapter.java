package com.example.ui;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;

import java.util.ArrayList;
import java.util.List;

public class TrustedAppAdapter extends RecyclerView.Adapter<TrustedAppAdapter.AppViewHolder> {

    public interface OnRemoveListener {
        void onRemove(String packageName);
    }

    private final List<String> packageNames = new ArrayList<>();
    private final PackageManager packageManager;
    private OnRemoveListener listener;

    public TrustedAppAdapter(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    public void setListener(OnRemoveListener listener) {
        this.listener = listener;
    }

    public void setPackageNames(List<String> newNames) {
        packageNames.clear();
        if (newNames != null) packageNames.addAll(newNames);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trusted_app, parent, false);
        return new AppViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        final String packageName = packageNames.get(position);
        holder.tvPackage.setText(packageName);
        String label = packageName;
        Drawable icon = null;
        try {
            android.content.pm.ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            label = packageManager.getApplicationLabel(info).toString();
            icon = packageManager.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        holder.tvLabel.setText(label);
        if (icon != null) {
            holder.ivIcon.setImageDrawable(icon);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_shield);
        }
        holder.btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onRemove(packageName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return packageNames.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
        TextView tvPackage;
        ImageView btnRemove;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_trusted_app_icon);
            tvLabel = itemView.findViewById(R.id.tv_trusted_app_label);
            tvPackage = itemView.findViewById(R.id.tv_trusted_app_package);
            btnRemove = itemView.findViewById(R.id.btn_remove_trusted_app);
        }
    }
}
