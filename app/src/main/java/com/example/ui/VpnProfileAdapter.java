package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.vpn.VpnProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VpnProfileAdapter extends RecyclerView.Adapter<VpnProfileAdapter.ProfileViewHolder> {

    public interface OnProfileActionListener {
        void onSelectActive(VpnProfile profile);

        void onDelete(VpnProfile profile);
    }

    private final List<VpnProfile> profiles = new ArrayList<>();
    private String activeProfileId;
    private OnProfileActionListener listener;

    public void setListener(OnProfileActionListener listener) {
        this.listener = listener;
    }

    public void setProfiles(List<VpnProfile> newProfiles, String activeId) {
        profiles.clear();
        if (newProfiles != null) profiles.addAll(newProfiles);
        activeProfileId = activeId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vpn_profile, parent, false);
        return new ProfileViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        final VpnProfile profile = profiles.get(position);
        holder.tvName.setText(profile.getName());
        holder.tvTypeBadge.setText(profile.getType());
        holder.tvServer.setText(String.format(Locale.getDefault(), "%s:%d", profile.getServer(), profile.getPort()));
        holder.radioActive.setChecked(profile.getId().equals(activeProfileId));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onSelectActive(profile);
            }
        });
        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDelete(profile);
            }
        });
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvTypeBadge;
        TextView tvServer;
        RadioButton radioActive;
        ImageView btnDelete;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_profile_name);
            tvTypeBadge = itemView.findViewById(R.id.tv_profile_type_badge);
            tvServer = itemView.findViewById(R.id.tv_profile_server);
            radioActive = itemView.findViewById(R.id.radio_profile_active);
            btnDelete = itemView.findViewById(R.id.btn_delete_profile);
        }
    }
}
