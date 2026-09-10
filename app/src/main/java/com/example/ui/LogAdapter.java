package com.example.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.db.DnsLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    public interface OnLogClickListener {
        void onLogClick(DnsLog log);
        void onLogLongClick(DnsLog log);
    }

    private final List<DnsLog> logs = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private OnLogClickListener listener;

    public void setListener(OnLogClickListener listener) {
        this.listener = listener;
    }

    public void setLogs(List<DnsLog> newLogs) {
        logs.clear();
        if (newLogs != null) {
            logs.addAll(newLogs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dns_log, parent, false);
        return new LogViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        final DnsLog log = logs.get(position);
        holder.tvDomain.setText(log.getDomain());
        holder.tvQueryType.setText(log.getQueryType() != null ? log.getQueryType() : "A");
        holder.tvTime.setText(timeFormat.format(new Date(log.getTimestamp())));
        holder.tvLatency.setText(log.getResponseTimeMs() + " ms");
        holder.tvUpstream.setText((log.getUpstream() != null ? log.getUpstream() : "Unknown")
                + " • Client: " + (log.getClientIp() != null ? log.getClientIp() : "127.0.0.1"));

        Context context = holder.itemView.getContext();
        String status = log.getStatus() != null ? log.getStatus() : "ALLOWED";
        holder.tvStatusBadge.setText(status);

        if ("BLOCKED".equalsIgnoreCase(status)) {
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorBlocked));
        } else if ("CACHED".equalsIgnoreCase(status)) {
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorCached));
        } else if ("LOCAL".equalsIgnoreCase(status)) {
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorLocal));
        } else {
            holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorAllowed));
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onLogClick(log);
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) {
                    listener.onLogLongClick(log);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatusBadge;
        TextView tvQueryType;
        TextView tvLatency;
        TextView tvTime;
        TextView tvDomain;
        TextView tvUpstream;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvQueryType = itemView.findViewById(R.id.tv_query_type);
            tvLatency = itemView.findViewById(R.id.tv_latency);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvDomain = itemView.findViewById(R.id.tv_domain);
            tvUpstream = itemView.findViewById(R.id.tv_upstream);
        }
    }
}
