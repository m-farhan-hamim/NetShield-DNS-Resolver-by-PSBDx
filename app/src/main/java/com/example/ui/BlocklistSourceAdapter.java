package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.db.BlocklistSource;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BlocklistSourceAdapter extends RecyclerView.Adapter<BlocklistSourceAdapter.SourceViewHolder> {

    public interface OnSourceActionListener {
        void onToggleEnabled(BlocklistSource source, boolean enabled);
        void onDelete(BlocklistSource source);
    }

    private final List<BlocklistSource> sources = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    private OnSourceActionListener listener;

    public void setListener(OnSourceActionListener listener) {
        this.listener = listener;
    }

    public void setSources(List<BlocklistSource> newSources) {
        sources.clear();
        if (newSources != null) {
            sources.addAll(newSources);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocklist_source, parent, false);
        return new SourceViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        final BlocklistSource source = sources.get(position);
        holder.tvName.setText(source.getName());
        holder.tvUrl.setText(source.getUrl());

        String lastSyncStr = source.getLastUpdated() > 0 ? dateFormat.format(new Date(source.getLastUpdated())) : "Never synced";
        holder.tvStats.setText(String.format(Locale.getDefault(), "%,d rules loaded • Last synced: %s", source.getRuleCount(), lastSyncStr));

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(source.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                source.setEnabled(isChecked);
                if (listener != null) {
                    listener.onToggleEnabled(source, isChecked);
                }
            }
        });

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDelete(source);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvUrl;
        TextView tvStats;
        SwitchCompat switchEnabled;
        ImageView btnDelete;

        public SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_source_name);
            tvUrl = itemView.findViewById(R.id.tv_source_url);
            tvStats = itemView.findViewById(R.id.tv_source_stats);
            switchEnabled = itemView.findViewById(R.id.switch_source_enabled);
            btnDelete = itemView.findViewById(R.id.btn_delete_source);
        }
    }
}
