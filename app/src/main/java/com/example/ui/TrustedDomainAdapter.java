package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.trust.TrustedListManager;

import java.util.ArrayList;
import java.util.List;

public class TrustedDomainAdapter extends RecyclerView.Adapter<TrustedDomainAdapter.DomainViewHolder> {

    public interface OnRemoveListener {
        void onRemove(String domain);
    }

    private final List<String> domains = new ArrayList<>();
    private OnRemoveListener listener;

    public void setListener(OnRemoveListener listener) {
        this.listener = listener;
    }

    public void setDomains(List<String> newDomains) {
        domains.clear();
        if (newDomains != null) domains.addAll(newDomains);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DomainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trusted_domain, parent, false);
        return new DomainViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DomainViewHolder holder, int position) {
        final String domain = domains.get(position);
        holder.tvDomain.setText(domain);
        holder.tvBadge.setVisibility(
                domain.equalsIgnoreCase(TrustedListManager.DEFAULT_TRUSTED_DOMAIN) ? View.VISIBLE : View.GONE);
        holder.btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onRemove(domain);
            }
        });
    }

    @Override
    public int getItemCount() {
        return domains.size();
    }

    static class DomainViewHolder extends RecyclerView.ViewHolder {
        TextView tvDomain;
        TextView tvBadge;
        ImageView btnRemove;

        DomainViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDomain = itemView.findViewById(R.id.tv_trusted_domain);
            tvBadge = itemView.findViewById(R.id.tv_trusted_domain_badge);
            btnRemove = itemView.findViewById(R.id.btn_remove_trusted_domain);
        }
    }
}
