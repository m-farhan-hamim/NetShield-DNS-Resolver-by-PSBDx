package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.db.DomainStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TopDomainAdapter extends RecyclerView.Adapter<TopDomainAdapter.ViewHolder> {

    public interface OnDomainActionListener {
        void onAction(DomainStat stat, boolean isBlockedList);
    }

    private final List<DomainStat> items = new ArrayList<>();
    private final boolean isBlockedList;
    private final OnDomainActionListener listener;

    public TopDomainAdapter(boolean isBlockedList, OnDomainActionListener listener) {
        this.isBlockedList = isBlockedList;
        this.listener = listener;
    }

    public void submitList(List<DomainStat> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_top_domain, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DomainStat stat = items.get(position);
        holder.tvRank.setText(String.valueOf(position + 1));
        holder.tvDomain.setText(stat.getDomain());
        holder.tvCount.setText(String.format(Locale.getDefault(), "%,d hits", stat.getCount()));

        if (isBlockedList) {
            holder.btnAction.setText("Whitelist");
            holder.btnAction.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorAllowed));
        } else {
            holder.btnAction.setText("Block");
            holder.btnAction.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorBlocked));
        }

        holder.btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onAction(stat, isBlockedList);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank;
        TextView tvDomain;
        TextView tvCount;
        Button btnAction;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_domain_rank);
            tvDomain = itemView.findViewById(R.id.tv_domain_name);
            tvCount = itemView.findViewById(R.id.tv_domain_count);
            btnAction = itemView.findViewById(R.id.btn_quick_action);
        }
    }
}
