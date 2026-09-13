package com.example.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.vpn.OpenVpnConfigParser;
import com.example.vpn.VpnProfile;
import com.example.vpn.VpnProfileStore;
import com.example.vpn.WireGuardConfigParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class VpnServersActivity extends AppCompatActivity {
    private static final String TAG = "VpnServersActivity";
    private static final int REQUEST_PICK_OPENVPN = 2001;
    private static final int REQUEST_PICK_WIREGUARD = 2002;

    private VpnProfileStore profileStore;
    private VpnProfileAdapter adapter;
    private RecyclerView rvProfiles;
    private TextView tvEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_servers);

        profileStore = new VpnProfileStore(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button btnImportOpenVpn = findViewById(R.id.btn_import_openvpn);
        Button btnImportWireGuard = findViewById(R.id.btn_import_wireguard);
        btnImportOpenVpn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchFilePicker(REQUEST_PICK_OPENVPN);
            }
        });
        btnImportWireGuard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchFilePicker(REQUEST_PICK_WIREGUARD);
            }
        });

        tvEmpty = findViewById(R.id.tv_no_profiles);
        rvProfiles = findViewById(R.id.rv_vpn_profiles);
        rvProfiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VpnProfileAdapter();
        adapter.setListener(new VpnProfileAdapter.OnProfileActionListener() {
            @Override
            public void onSelectActive(VpnProfile profile) {
                profileStore.setActiveProfileId(profile.getId());
                refreshList();
                Toast.makeText(VpnServersActivity.this,
                        getString(R.string.vpn_profile_set_active, profile.getName()), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(VpnProfile profile) {
                profileStore.delete(profile.getId());
                refreshList();
                Toast.makeText(VpnServersActivity.this, R.string.vpn_profile_deleted, Toast.LENGTH_SHORT).show();
            }
        });
        rvProfiles.setAdapter(adapter);

        refreshList();
    }

    private void launchFilePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            Log.e(TAG, "No file picker available", e);
            Toast.makeText(this, R.string.vpn_profile_read_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        String fileText = readTextFromUri(uri);
        if (fileText == null) {
            Toast.makeText(this, R.string.vpn_profile_read_failed, Toast.LENGTH_LONG).show();
            return;
        }

        String displayName = queryDisplayName(uri);

        if (requestCode == REQUEST_PICK_OPENVPN) {
            importOpenVpn(fileText, displayName);
        } else if (requestCode == REQUEST_PICK_WIREGUARD) {
            importWireGuard(fileText, displayName);
        }
    }

    private void importOpenVpn(String fileText, String displayName) {
        if (!OpenVpnConfigParser.looksValid(fileText)) {
            Toast.makeText(this, R.string.vpn_profile_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        OpenVpnConfigParser.ParsedResult parsed = OpenVpnConfigParser.parse(fileText);
        String name = displayName != null ? stripExtension(displayName) : parsed.server;
        VpnProfile profile = profileStore.add(name, VpnProfile.TYPE_OPENVPN,
                parsed.server, parsed.port, parsed.protocol, fileText);
        if (profileStore.getActiveProfileId() == null) {
            profileStore.setActiveProfileId(profile.getId());
        }
        refreshList();
        Toast.makeText(this, getString(R.string.vpn_profile_imported, profile.getName()), Toast.LENGTH_SHORT).show();
    }

    private void importWireGuard(String fileText, String displayName) {
        if (!WireGuardConfigParser.looksValid(fileText)) {
            Toast.makeText(this, R.string.vpn_profile_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        WireGuardConfigParser.ParsedResult parsed = WireGuardConfigParser.parse(fileText);
        String name = displayName != null ? stripExtension(displayName) : parsed.server;
        VpnProfile profile = profileStore.add(name, VpnProfile.TYPE_WIREGUARD,
                parsed.server, parsed.port, "wg", fileText);
        if (profileStore.getActiveProfileId() == null) {
            profileStore.setActiveProfileId(profile.getId());
        }
        refreshList();
        Toast.makeText(this, getString(R.string.vpn_profile_imported, profile.getName()), Toast.LENGTH_SHORT).show();
    }

    private void refreshList() {
        List<VpnProfile> profiles = profileStore.getAll();
        adapter.setProfiles(profiles, profileStore.getActiveProfileId());
        tvEmpty.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
        rvProfiles.setVisibility(profiles.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Nullable
    private String readTextFromUri(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read picked file", e);
            return null;
        }
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't read display name", e);
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
