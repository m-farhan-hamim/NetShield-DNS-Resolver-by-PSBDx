package com.example.ui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.vpn.OpenVpnConfigParser;
import com.example.vpn.ProxySubscription;
import com.example.vpn.ProxySubscriptionStore;
import com.example.vpn.VpnProfile;
import com.example.vpn.VpnProfileStore;
import com.example.vpn.WireGuardConfigParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class VpnServersActivity extends AppCompatActivity {
    private static final String TAG = "VpnServersActivity";
    private static final int REQUEST_PICK_OPENVPN = 2001;
    private static final int REQUEST_PICK_WIREGUARD = 2002;

    private VpnProfileStore profileStore;
    private VpnProfileAdapter adapter;
    private RecyclerView rvProfiles;
    private TextView tvEmpty;
    private ProxySubscriptionStore subscriptionStore;
    private LinearLayout llSubscriptions;
    private TextView tvNoSubscriptions;

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

        subscriptionStore = new ProxySubscriptionStore(this);
        llSubscriptions = findViewById(R.id.ll_subscriptions);
        tvNoSubscriptions = findViewById(R.id.tv_no_subscriptions);
        findViewById(R.id.btn_add_subscription).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddSubscriptionDialog();
            }
        });
        findViewById(R.id.btn_coexistence_guide).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CoexistenceGuide.show(VpnServersActivity.this, null);
            }
        });
        refreshSubscriptions();

        refreshList();
    }

    // ==================== PROXY SUBSCRIPTIONS ====================
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showAddSubscriptionDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.proxy_sub_name_hint);
        etName.setSingleLine(true);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(etName);

        final EditText etUrl = new EditText(this);
        etUrl.setHint(R.string.proxy_sub_url_hint);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(etUrl);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.proxy_sub_add_title)
                .setView(box)
                .setPositiveButton(R.string.proxy_sub_add_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        // Overridden after show() so a bad URL keeps the dialog (and what was typed) open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = ProxySubscriptionStore.normalizeUrl(etUrl.getText().toString());
                if (url == null) {
                    Toast.makeText(VpnServersActivity.this, R.string.proxy_sub_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (subscriptionStore.contains(url)) {
                    Toast.makeText(VpnServersActivity.this, R.string.proxy_sub_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }
                ProxySubscription sub = subscriptionStore.add(etName.getText().toString(), url);
                refreshSubscriptions();
                Toast.makeText(VpnServersActivity.this,
                        getString(R.string.proxy_sub_saved, sub.getName()), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
    }

    private void refreshSubscriptions() {
        llSubscriptions.removeAllViews();
        List<ProxySubscription> subs = subscriptionStore.getAll();
        tvNoSubscriptions.setVisibility(subs.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final ProxySubscription sub : subs) {
            View row = inflater.inflate(R.layout.item_proxy_subscription, llSubscriptions, false);
            ((TextView) row.findViewById(R.id.tv_sub_name)).setText(sub.getName());
            // Host only: the path/query hold the secret token and are never displayed.
            ((TextView) row.findViewById(R.id.tv_sub_host)).setText(ProxySubscriptionStore.displayHost(sub.getUrl()));
            row.findViewById(R.id.btn_open_v2rayng).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openInV2rayNg(sub);
                }
            });
            row.findViewById(R.id.btn_copy_sub).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copySubscription(sub);
                }
            });
            row.findViewById(R.id.btn_delete_sub).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    subscriptionStore.delete(sub.getId());
                    refreshSubscriptions();
                    Toast.makeText(VpnServersActivity.this, R.string.proxy_sub_deleted, Toast.LENGTH_SHORT).show();
                }
            });
            llSubscriptions.addView(row);
        }
    }

    /**
     * Hands the subscription to v2rayNG's own import link. The intent is pinned to v2rayNG's
     * package so no other app registered for the scheme can receive the secret URL.
     */
    private void openInV2rayNg(ProxySubscription sub) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("v2rayng://install-sub?url=" + Uri.encode(sub.getUrl())));
        intent.setPackage(ProxySubscriptionStore.V2RAYNG_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.proxy_sub_v2rayng_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void copySubscription(ProxySubscription sub) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.proxy_sub_clip_label), sub.getUrl());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Keeps the token out of the clipboard preview overlay.
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.proxy_sub_copied, Toast.LENGTH_SHORT).show();
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
        if (!isSafePickedUri(uri)) {
            Log.w(TAG, "Rejected picked file: unexpected URI");
            Toast.makeText(this, R.string.vpn_profile_read_failed, Toast.LENGTH_LONG).show();
            return;
        }
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

    /**
     * The system picker (ACTION_OPEN_DOCUMENT) only returns content:// URIs issued by
     * another app's document provider. The result still originates outside this app,
     * so before resolving it make sure it cannot be steered at this app's own content
     * providers (confused deputy), at file:// paths, or through path traversal.
     */
    private boolean isSafePickedUri(@Nullable Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return false;
        }
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) {
            return false;
        }
        // content://<userId>@<authority>/... is resolved against <authority>, so drop the user info.
        int at = authority.lastIndexOf('@');
        String providerName = (at >= 0 ? authority.substring(at + 1) : authority).toLowerCase(Locale.ROOT);
        String ownPackage = getPackageName().toLowerCase(Locale.ROOT);
        if (providerName.equals(ownPackage) || providerName.startsWith(ownPackage + ".")) {
            return false;
        }
        for (String segment : uri.getPathSegments()) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
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
