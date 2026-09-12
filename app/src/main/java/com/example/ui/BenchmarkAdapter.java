package com.example.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.dns.DnsBenchmark;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkAdapter extends RecyclerView.Adapter<BenchmarkAdapter.ViewHolder> {

    public interface OnApplyListener {
        void onApply(DnsBenchmark.Result result);
    }

    private final List<DnsBenchmark.Result> items = new ArrayList<>();
    private final OnApplyListener listener;
    private int fastestIndex = -1;

    public BenchmarkAdapter(OnApplyListener listener) {
        this.listener = listener;
    }

    public void submitList(List<DnsBenchmark.Result> newItems) {
        items.clear();
        fastestIndex = -1;
        if (newItems != null) {
            items.addAll(newItems);
            long minLatency = Long.MAX_VALUE;
            for (int i = 0; i < items.size(); i++) {
                DnsBenchmark.Result r = items.get(i);
                if (r.isSuccess() && r.getLatencyMs() > 0 && r.getLatencyMs() < minLatency) {
                    minLatency = r.getLatencyMs();
                    fastestIndex = i;
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_benchmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DnsBenchmark.Result result = items.get(position);
        holder.tvName.setText(result.getName());
        holder.tvIp.setText(result.getIp());

        if (position == fastestIndex) {
            holder.tvFastest.setVisibility(View.VISIBLE);
        } else {
            holder.tvFastest.setVisibility(View.GONE);
        }

        if (result.isSuccess() && result.getLatencyMs() >= 0) {
            holder.tvLatency.setText(result.getLatencyMs() + " ms");
            if (result.getLatencyMs() < 35) {
                holder.tvLatency.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorAllowed));
            } else if (result.getLatencyMs() < 80) {
                holder.tvLatency.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorWarning));
            } else {
                holder.tvLatency.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorBlocked));
            }
            holder.btnApply.setEnabled(true);
        } else {
            holder.tvLatency.setText("Timeout");
            holder.tvLatency.setTextColor(Color.parseColor("#94A3B8"));
            holder.btnApply.setEnabled(false);
        }

        holder.btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onApply(result);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvIp;
        TextView tvFastest;
        TextView tvLatency;
        Button btnApply;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_benchmark_name);
            tvIp = itemView.findViewById(R.id.tv_benchmark_ip);
            tvFastest = itemView.findViewById(R.id.tv_fastest_badge);
            tvLatency = itemView.findViewById(R.id.tv_benchmark_latency);
            btnApply = itemView.findViewById(R.id.btn_apply_upstream);
        }
    }
}
