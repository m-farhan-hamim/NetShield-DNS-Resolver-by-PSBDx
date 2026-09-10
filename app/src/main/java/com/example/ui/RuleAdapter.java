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
import com.example.db.CustomRule;

import java.util.ArrayList;
import java.util.List;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.RuleViewHolder> {

    public interface OnRuleActionListener {
        void onToggleEnabled(CustomRule rule, boolean enabled);
        void onDelete(CustomRule rule);
    }

    private final List<CustomRule> rules = new ArrayList<>();
    private OnRuleActionListener listener;

    public void setListener(OnRuleActionListener listener) {
        this.listener = listener;
    }

    public void setRules(List<CustomRule> newRules) {
        rules.clear();
        if (newRules != null) {
            rules.addAll(newRules);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rule, parent, false);
        return new RuleViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        final CustomRule rule = rules.get(position);
        holder.tvDomain.setText(rule.getDomain());

        if (CustomRule.TYPE_MAPPING.equals(rule.getRuleType()) && rule.getTargetIp() != null && !rule.getTargetIp().isEmpty()) {
            holder.tvTarget.setVisibility(View.VISIBLE);
            holder.tvTarget.setText("Target IP: " + rule.getTargetIp());
        } else {
            holder.tvTarget.setVisibility(View.GONE);
        }

        // Avoid triggering listener during binding
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(rule.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                rule.setEnabled(isChecked);
                if (listener != null) {
                    listener.onToggleEnabled(rule, isChecked);
                }
            }
        });

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDelete(rule);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    static class RuleViewHolder extends RecyclerView.ViewHolder {
        TextView tvDomain;
        TextView tvTarget;
        SwitchCompat switchEnabled;
        ImageView btnDelete;

        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDomain = itemView.findViewById(R.id.tv_rule_domain);
            tvTarget = itemView.findViewById(R.id.tv_rule_target);
            switchEnabled = itemView.findViewById(R.id.switch_rule_enabled);
            btnDelete = itemView.findViewById(R.id.btn_delete_rule);
        }
    }
}
