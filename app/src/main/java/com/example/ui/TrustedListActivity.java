package com.example.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.trust.TrustedListManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrustedListActivity extends AppCompatActivity {
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private TrustedListManager trustedListManager;
    private TrustedDomainAdapter domainAdapter;
    private TrustedAppAdapter appAdapter;
    private TextView tvNoTrustedApps;
    private RecyclerView rvTrustedApps;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trusted_list);

        trustedListManager = TrustedListManager.getInstance(this);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setupDomainsSection();
        setupAppsSection();

        refreshDomains();
        refreshApps();
    }

    private void setupDomainsSection() {
        RecyclerView rvDomains = findViewById(R.id.rv_trusted_domains);
        rvDomains.setLayoutManager(new LinearLayoutManager(this));
        domainAdapter = new TrustedDomainAdapter();
        domainAdapter.setListener(new TrustedDomainAdapter.OnRemoveListener() {
            @Override
            public void onRemove(String domain) {
                trustedListManager.removeTrustedDomain(domain);
                refreshDomains();
                Toast.makeText(TrustedListActivity.this, "Removed " + domain, Toast.LENGTH_SHORT).show();
            }
        });
        rvDomains.setAdapter(domainAdapter);

        final EditText etDomain = findViewById(R.id.et_new_trusted_domain);
        Button btnAdd = findViewById(R.id.btn_add_trusted_domain);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String domain = etDomain.getText().toString().trim();
                if (domain.isEmpty()) {
                    Toast.makeText(TrustedListActivity.this, "Enter a domain first", Toast.LENGTH_SHORT).show();
                    return;
                }
                trustedListManager.addTrustedDomain(domain);
                etDomain.setText("");
                refreshDomains();
            }
        });
    }

    private void setupAppsSection() {
        rvTrustedApps = findViewById(R.id.rv_trusted_apps);
        tvNoTrustedApps = findViewById(R.id.tv_no_trusted_apps);
        rvTrustedApps.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new TrustedAppAdapter(getPackageManager());
        appAdapter.setListener(new TrustedAppAdapter.OnRemoveListener() {
            @Override
            public void onRemove(String packageName) {
                trustedListManager.removeTrustedApp(packageName);
                refreshApps();
            }
        });
        rvTrustedApps.setAdapter(appAdapter);

        Button btnAddApp = findViewById(R.id.btn_add_trusted_app);
        btnAddApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPickerDialog();
            }
        });
    }

    private void showAppPickerDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_split_tunnel, null);
        final ProgressBar pbLoading = view.findViewById(R.id.pb_loading_apps);
        final RecyclerView rvApps = view.findViewById(R.id.rv_split_apps);

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        final AppSelectAdapter picker = new AppSelectAdapter();
        rvApps.setAdapter(picker);

        pbLoading.setVisibility(View.VISIBLE);
        final Set<String> currentTrusted = new java.util.HashSet<>(trustedListManager.getTrustedApps());

        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                final List<AppSelectAdapter.AppItem> appItems = new ArrayList<>();
                for (ApplicationInfo app : apps) {
                    try {
                        String label = pm.getApplicationLabel(app).toString();
                        Drawable icon = pm.getApplicationIcon(app);
                        appItems.add(new AppSelectAdapter.AppItem(label, app.packageName, icon, false));
                    } catch (Exception ignored) {
                    }
                }
                Collections.sort(appItems, new Comparator<AppSelectAdapter.AppItem>() {
                    @Override
                    public int compare(AppSelectAdapter.AppItem o1, AppSelectAdapter.AppItem o2) {
                        return o1.label.compareToIgnoreCase(o2.label);
                    }
                });
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        pbLoading.setVisibility(View.GONE);
                        picker.setItems(appItems, currentTrusted);
                    }
                });
            }
        });

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(R.string.action_save, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Set<String> selected = picker.getSelectedPackages();
                        for (String pkg : selected) {
                            trustedListManager.addTrustedApp(pkg);
                        }
                        for (String pkg : currentTrusted) {
                            if (!selected.contains(pkg)) {
                                trustedListManager.removeTrustedApp(pkg);
                            }
                        }
                        refreshApps();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshDomains() {
        domainAdapter.setDomains(trustedListManager.getTrustedDomains());
    }

    private void refreshApps() {
        List<String> apps = trustedListManager.getTrustedApps();
        appAdapter.setPackageNames(apps);
        tvNoTrustedApps.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        rvTrustedApps.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
