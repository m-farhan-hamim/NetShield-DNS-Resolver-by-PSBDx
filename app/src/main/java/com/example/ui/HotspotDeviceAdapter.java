package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.db.ClientDeviceStat;
import com.example.hotspot.HotspotManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HotspotDeviceAdapter extends RecyclerView.Adapter<HotspotDeviceAdapter.DeviceViewHolder> {

    public interface OnDeviceActionListener {
        void onToggleBlock(ClientDeviceStat device, boolean blocked);

        void onSetLimit(ClientDeviceStat device);

        void onRename(ClientDeviceStat device);

        void onViewHistory(ClientDeviceStat device);
    }

    private final List<ClientDeviceStat> devices = new ArrayList<>();
    private HotspotManager hotspotManager;
    private OnDeviceActionListener listener;

    public void setHotspotManager(HotspotManager manager) {
        this.hotspotManager = manager;
    }

    public void setListener(OnDeviceActionListener listener) {
        this.listener = listener;
    }

    public void setDevices(List<ClientDeviceStat> newDevices) {
        devices.clear();
        if (newDevices != null) devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hotspot_device, parent, false);
        return new DeviceViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        final ClientDeviceStat device = devices.get(position);
        String name = hotspotManager != null ? hotspotManager.getClientName(device.getClientIp()) : null;
        holder.tvName.setText(name != null ? name + " (" + device.getClientIp() + ")" : device.getClientIp());

        int todayCount = hotspotManager != null ? hotspotManager.getTodayQueryCount(device.getClientIp()) : 0;
        holder.tvStats.setText(String.format(Locale.getDefault(), "%d queries today • %d total • %d blocked",
                todayCount, device.getTotalQueries(), device.getBlockedQueries()));

        int limit = hotspotManager != null ? hotspotManager.getClientLimit(device.getClientIp()) : 0;
        if (limit > 0) {
            holder.tvLimit.setText("Daily limit: " + limit + " queries");
            holder.tvLimit.setVisibility(View.VISIBLE);
        } else {
            holder.tvLimit.setVisibility(View.GONE);
        }

        holder.switchBlock.setOnCheckedChangeListener(null);
        holder.switchBlock.setChecked(hotspotManager != null && hotspotManager.isBlocked(device.getClientIp()));
        holder.switchBlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleBlock(device, isChecked);
        });

        holder.btnSetLimit.setOnClickListener(v -> {
            if (listener != null) listener.onSetLimit(device);
        });
        holder.btnRename.setOnClickListener(v -> {
            if (listener != null) listener.onRename(device);
        });
        holder.btnHistory.setOnClickListener(v -> {
            if (listener != null) listener.onViewHistory(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvStats;
        TextView tvLimit;
        SwitchCompat switchBlock;
        Button btnSetLimit;
        Button btnRename;
        Button btnHistory;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvStats = itemView.findViewById(R.id.tv_device_stats);
            tvLimit = itemView.findViewById(R.id.tv_device_limit);
            switchBlock = itemView.findViewById(R.id.switch_device_block);
            btnSetLimit = itemView.findViewById(R.id.btn_device_set_limit);
            btnRename = itemView.findViewById(R.id.btn_device_rename);
            btnHistory = itemView.findViewById(R.id.btn_device_history);
        }
    }
}
