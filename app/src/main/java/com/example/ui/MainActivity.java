package com.example.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.BuildConfig;
import com.example.R;
import com.example.db.BlocklistSource;
import com.example.db.CustomRule;
import com.example.db.DatabaseHelper;
import com.example.db.DnsLog;
import com.example.db.DomainStat;
import com.example.db.TrafficBucket;
import com.example.dns.BlocklistManager;
import com.example.dns.DnsBenchmark;
import com.example.dns.DnsPacketParser;
import com.example.dns.DnsResolverEngine;
import com.example.service.DnsServerService;
import com.example.service.DnsVpnService;
import com.example.service.ServiceManager;
import com.example.util.ConfigBackupUtils;
import com.google.android.material.tabs.TabLayout;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_VPN = 1002;

    private DatabaseHelper dbHelper;
    private DnsResolverEngine engine;
    private BlocklistManager blocklistManager;
    private SharedPreferences prefs;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI Containers
    private View containerDashboard;
    private View containerTraffic;
    private View containerLogs;
    private View containerRules;
    private View containerSettings;

    // Header Views
    private TextView tvHeaderStatus;
    private View indicatorStatusDot;

    // Dashboard Views
    private TextView tvDashboardStatus;
    private TextView tvDashboardSubtitle;
    private ImageView ivStatusIcon;
    private Button btnToggleService;
    private RadioGroup rgOperationMode;
    private RadioButton rbModeVpn;
    private RadioButton rbModeServer;
    private LinearLayout layoutServerAddress;
    private TextView tvServerAddress;
    private Button btnCopyAddress;
    private TextView tvStatTotal;
    private TextView tvStatBlocked;
    private TextView tvStatRate;
    private TextView tvStatCache;
    private EditText etTestDomain;
    private Button btnTestQuery;
    private TextView tvTestResult;
    private LinearLayout layoutPauseControls;
    private Button btnPause5m;
    private Button btnPause15m;
    private TextView tvPauseDot;
    private Button btnResumeProtection;
    private TextView tvPauseStatus;

    // Traffic Views
    private TextView tvTrafficUpstreamTag;
    private TextView tvTrafficThroughput;
    private TextView tvTrafficAvgLatency;
    private TextView tvTrafficCacheRatio;
    private Button btnTrafficFlushCache;
    private Button btnTrafficExportLogs;
    private TrafficChartView chartTrafficActivity;
    private Button btnRunBenchmark;
    private ProgressBar pbBenchmark;
    private RecyclerView rvBenchmark;
    private BenchmarkAdapter benchmarkAdapter;
    private RecyclerView rvTopBlocked;
    private TopDomainAdapter topBlockedAdapter;
    private TextView tvEmptyTopBlocked;
    private RecyclerView rvTopAllowed;
    private TopDomainAdapter topAllowedAdapter;
    private TextView tvEmptyTopAllowed;
    private TextView tvCountTypeA;
    private TextView tvCountTypeAaaa;
    private TextView tvCountTypeHttps;
    private TextView tvCountTypeOther;

    // Logs Views
    private EditText etSearchLogs;
    private TextView chipFilterAll;
    private TextView chipFilterBlocked;
    private TextView chipFilterAllowed;
    private Button btnExportLogs;
    private Button btnClearLogs;
    private RecyclerView rvLogs;
    private View layoutLogsEmpty;
    private LogAdapter logAdapter;
    private String currentLogFilter = "ALL";

    // Rules Views
    private TextView chipSubtabBlocked;
    private TextView chipSubtabWhitelist;
    private TextView chipSubtabMappings;
    private TextView chipSubtabRemote;
    private TextView tvRulesCount;
    private Button btnSyncSources;
    private Button btnAddRuleAction;
    private RecyclerView rvRules;
    private View layoutRulesEmpty;
    private TextView tvRulesEmptyText;
    private RuleAdapter ruleAdapter;
    private BlocklistSourceAdapter sourceAdapter;
    private String currentRuleSubtab = "BLOCKED";

    // Settings Views
    private RadioGroup rgUpstreamProtocol;
    private RadioButton rbUpstreamDoh;
    private RadioButton rbUpstreamDot;
    private RadioButton rbUpstreamUdp;
    private LinearLayout layoutDohSettings;
    private LinearLayout layoutDotSettings;
    private LinearLayout layoutUdpSettings;
    private EditText etDohUrl;
    private EditText etDotHost;
    private EditText etPrimaryDns;
    private EditText etSecondaryDns;
    private RadioGroup rgBlockAction;
    private RadioButton rbBlockZero;
    private RadioButton rbBlockNxdomain;
    private SwitchCompat switchCacheEnable;
    private Button btnClearCacheAction;
    private Button btnConfigureSplitTunnel;
    private SwitchCompat switchAutoStart;
    private EditText etServerPort;
    private Button btnExportBackup;
    private Button btnImportBackup;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ServiceManager.ACTION_STATE_CHANGED.equals(action)) {
                updateServiceStatusUI();
            } else if (DnsResolverEngine.ACTION_LOG_UPDATED.equals(action)) {
                refreshStats();
                if (containerTraffic != null && containerTraffic.getVisibility() == View.VISIBLE) {
                    refreshTrafficDashboard();
                }
                if (containerLogs != null && containerLogs.getVisibility() == View.VISIBLE) {
                    refreshLogs();
                }
            }
        }
    };

    private final Runnable pauseTickRunnable = new Runnable() {
        @Override
        public void run() {
            updatePauseUi();
            if (blocklistManager != null && blocklistManager.isPaused()) {
                mainHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = DatabaseHelper.getInstance(this);
        engine = DnsResolverEngine.getInstance(this);
        blocklistManager = BlocklistManager.getInstance(this);
        prefs = getSharedPreferences(DnsResolverEngine.PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupNavigationTabs();
        setupDashboard();
        setupTrafficDashboard();
        setupLogs();
        setupRules();
        setupSettings();

        updateServiceStatusUI();
        refreshStats();
        updatePauseUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ServiceManager.ACTION_STATE_CHANGED);
        filter.addAction(DnsResolverEngine.ACTION_LOG_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
        updateServiceStatusUI();
        refreshStats();
        updatePauseUi();
        if (blocklistManager != null && blocklistManager.isPaused()) {
            mainHandler.post(pauseTickRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(pauseTickRunnable);
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        containerDashboard = findViewById(R.id.container_dashboard);
        containerTraffic = findViewById(R.id.container_traffic);
        containerLogs = findViewById(R.id.container_logs);
        containerRules = findViewById(R.id.container_rules);
        containerSettings = findViewById(R.id.container_settings);

        tvHeaderStatus = findViewById(R.id.tv_header_status);
        indicatorStatusDot = findViewById(R.id.indicator_status_dot);
    }

    private void setupNavigationTabs() {
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void switchTab(int position) {
        containerDashboard.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        containerTraffic.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        containerLogs.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        containerRules.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
        containerSettings.setVisibility(position == 4 ? View.VISIBLE : View.GONE);

        if (position == 0) {
            refreshStats();
            updatePauseUi();
        } else if (position == 1) {
            refreshTrafficDashboard();
        } else if (position == 2) {
            refreshLogs();
        } else if (position == 3) {
            refreshRules();
        }
    }

    // ==================== DASHBOARD ====================
    private void setupDashboard() {
        tvDashboardStatus = findViewById(R.id.tv_dashboard_status);
        tvDashboardSubtitle = findViewById(R.id.tv_dashboard_subtitle);
        ivStatusIcon = findViewById(R.id.iv_status_icon);
        btnToggleService = findViewById(R.id.btn_toggle_service);
        rgOperationMode = findViewById(R.id.rg_operation_mode);
        rbModeVpn = findViewById(R.id.rb_mode_vpn);
        rbModeServer = findViewById(R.id.rb_mode_server);
        layoutServerAddress = findViewById(R.id.layout_server_address);
        tvServerAddress = findViewById(R.id.tv_server_address);
        btnCopyAddress = findViewById(R.id.btn_copy_address);
        tvStatTotal = findViewById(R.id.tv_stat_total);
        tvStatBlocked = findViewById(R.id.tv_stat_blocked);
        tvStatRate = findViewById(R.id.tv_stat_rate);
        tvStatCache = findViewById(R.id.tv_stat_cache);
        etTestDomain = findViewById(R.id.et_test_domain);
        btnTestQuery = findViewById(R.id.btn_test_query);
        tvTestResult = findViewById(R.id.tv_test_result);

        String mode = prefs.getString("operation_mode", "VPN");
        if ("SERVER".equalsIgnoreCase(mode)) {
            rbModeServer.setChecked(true);
            layoutServerAddress.setVisibility(View.VISIBLE);
        } else {
            rbModeVpn.setChecked(true);
            layoutServerAddress.setVisibility(View.GONE);
        }

        int port = prefs.getInt("server_port", 5353);
        tvServerAddress.setText("127.0.0.1:" + port);

        rgOperationMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_mode_server) {
                    prefs.edit().putString("operation_mode", "SERVER").apply();
                    layoutServerAddress.setVisibility(View.VISIBLE);
                } else {
                    prefs.edit().putString("operation_mode", "VPN").apply();
                    layoutServerAddress.setVisibility(View.GONE);
                }
            }
        });

        btnCopyAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Local DNS Server", tvServerAddress.getText().toString());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied address: " + tvServerAddress.getText(), Toast.LENGTH_SHORT).show();
            }
        });

        btnToggleService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int state = ServiceManager.getCurrentState();
                if (state != ServiceManager.STATE_STOPPED) {
                    stopActiveService();
                } else {
                    startConfiguredService();
                }
            }
        });

        btnTestQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runDnsTestQuery();
            }
        });

        layoutPauseControls = findViewById(R.id.layout_pause_controls);
        btnPause5m = findViewById(R.id.btn_pause_5m);
        btnPause15m = findViewById(R.id.btn_pause_15m);
        tvPauseDot = findViewById(R.id.tv_pause_dot);
        btnResumeProtection = findViewById(R.id.btn_resume_protection);
        tvPauseStatus = findViewById(R.id.tv_pause_status);

        btnPause5m.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blocklistManager.pauseProtection(5 * 60 * 1000L);
                updatePauseUi();
                mainHandler.removeCallbacks(pauseTickRunnable);
                mainHandler.post(pauseTickRunnable);
                Toast.makeText(MainActivity.this, "Sinkhole blocking paused for 5 minutes", Toast.LENGTH_SHORT).show();
            }
        });

        btnPause15m.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blocklistManager.pauseProtection(15 * 60 * 1000L);
                updatePauseUi();
                mainHandler.removeCallbacks(pauseTickRunnable);
                mainHandler.post(pauseTickRunnable);
                Toast.makeText(MainActivity.this, "Sinkhole blocking paused for 15 minutes", Toast.LENGTH_SHORT).show();
            }
        });

        btnResumeProtection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blocklistManager.resumeProtection();
                mainHandler.removeCallbacks(pauseTickRunnable);
                updatePauseUi();
                Toast.makeText(MainActivity.this, "Sinkhole protection resumed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePauseUi() {
        if (blocklistManager == null || tvPauseStatus == null) return;
        boolean paused = blocklistManager.isPaused();
        if (paused) {
            long remainingMs = blocklistManager.getRemainingPauseMillis();
            long totalSec = remainingMs / 1000;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            tvPauseStatus.setText(String.format(Locale.getDefault(), "Protection paused (%02d:%02d remaining)", min, sec));
            tvPauseStatus.setVisibility(View.VISIBLE);
            tvPauseDot.setVisibility(View.VISIBLE);
            btnResumeProtection.setVisibility(View.VISIBLE);
        } else {
            tvPauseStatus.setVisibility(View.GONE);
            tvPauseDot.setVisibility(View.GONE);
            btnResumeProtection.setVisibility(View.GONE);
        }
    }

    private void startConfiguredService() {
        String mode = prefs.getString("operation_mode", "VPN");
        if ("VPN".equalsIgnoreCase(mode)) {
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, REQUEST_VPN);
            } else {
                onActivityResult(REQUEST_VPN, RESULT_OK, null);
            }
        } else {
            Intent intent = new Intent(this, DnsServerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void stopActiveService() {
        // Only the service's own onDestroy() is allowed to report STOPPED -
        // that's what fixed the toggle appearing "off" while the VPN/server
        // kept running underneath it. Reflect the in-between "stopping"
        // state immediately so the button can't be double-tapped, then let
        // the ACTION_STATE_CHANGED broadcast (sent from onDestroy) confirm
        // the real state a moment later.
        btnToggleService.setEnabled(false);
        btnToggleService.setText(R.string.status_stopping);
        ServiceManager.stopActiveService(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            Intent intent = new Intent(this, DnsVpnService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void updateServiceStatusUI() {
        int state = ServiceManager.getCurrentState();
        int port = prefs.getInt("server_port", 5353);
        tvServerAddress.setText("127.0.0.1:" + port);
        btnToggleService.setEnabled(true);

        if (state == ServiceManager.STATE_VPN) {
            tvHeaderStatus.setText("PROTECTED (VPN)");
            tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.colorAllowed));
            indicatorStatusDot.getBackground().setTint(ContextCompat.getColor(this, R.color.colorAllowed));

            tvDashboardStatus.setText(R.string.status_vpn_running);
            tvDashboardSubtitle.setText("Filtering all system DNS queries via local VPN sinkhole");
            btnToggleService.setText(R.string.btn_stop);
            btnToggleService.setBackgroundResource(R.drawable.bg_button_stop);
            btnToggleService.setTextColor(Color.WHITE);
            ivStatusIcon.setImageResource(R.drawable.ic_shield);
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.colorAllowed));
        } else if (state == ServiceManager.STATE_SERVER) {
            tvHeaderStatus.setText("LISTENING (127.0.0.1:" + port + ")");
            tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            indicatorStatusDot.getBackground().setTint(ContextCompat.getColor(this, R.color.colorPrimary));

            tvDashboardStatus.setText(R.string.status_server_running);
            tvDashboardSubtitle.setText("Ready for local client sockets on port " + port);
            btnToggleService.setText(R.string.btn_stop);
            btnToggleService.setBackgroundResource(R.drawable.bg_button_stop);
            btnToggleService.setTextColor(Color.WHITE);
            ivStatusIcon.setImageResource(R.drawable.ic_shield);
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
        } else {
            tvHeaderStatus.setText(R.string.status_stopped);
            tvHeaderStatus.setTextColor(ContextCompat.getColor(this, R.color.colorBlocked));
            indicatorStatusDot.getBackground().setTint(ContextCompat.getColor(this, R.color.colorBlocked));

            tvDashboardStatus.setText(R.string.status_stopped);
            tvDashboardSubtitle.setText("Tap start below to enable local privacy protection");
            btnToggleService.setText(R.string.btn_start);
            btnToggleService.setBackgroundResource(R.drawable.bg_button_start);
            btnToggleService.setTextColor(Color.parseColor("#031024"));
            ivStatusIcon.setImageResource(R.drawable.ic_power);
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void refreshStats() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final long[] stats = dbHelper.getStats();
                final long total = stats[0];
                final long blocked = stats[1];
                final double rate = total > 0 ? ((double) blocked / total * 100.0) : 0.0;
                final long cacheHits = engine.getCache().getHitCount();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvStatTotal.setText(String.format(Locale.getDefault(), "%,d", total));
                        tvStatBlocked.setText(String.format(Locale.getDefault(), "%,d", blocked));
                        tvStatRate.setText(String.format(Locale.getDefault(), "%.1f%%", rate));
                        tvStatCache.setText(String.format(Locale.getDefault(), "%,d", cacheHits));
                    }
                });
            }
        });
    }

    private void runDnsTestQuery() {
        final String domain = etTestDomain.getText().toString().trim();
        if (domain.isEmpty()) {
            Toast.makeText(this, "Please enter a domain name", Toast.LENGTH_SHORT).show();
            return;
        }

        tvTestResult.setText("Querying DNS engine for '" + domain + "'…");
        btnTestQuery.setEnabled(false);

        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Synthesize a minimal RFC 1035 A-query for testing
                    byte[] query = buildTestQueryPacket(domain);
                    long start = System.currentTimeMillis();
                    byte[] response = engine.resolve(query, query.length, "127.0.0.1");
                    final long latency = System.currentTimeMillis() - start;

                    final String resultText;
                    if (response != null && response.length >= 12) {
                        int flags = ((response[2] & 0xFF) << 8) | (response[3] & 0xFF);
                        int rcode = flags & 0x0F;
                        int ancount = ((response[6] & 0xFF) << 8) | (response[7] & 0xFF);

                        boolean blocked = blocklistManager.isBlocked(domain);
                        String custom = blocklistManager.getCustomMapping(domain);

                        if (custom != null) {
                            resultText = "✅ LOCAL OVERRIDE: Mapped to " + custom + " (" + latency + " ms)";
                        } else if (blocked) {
                            resultText = "🛑 SINKHOLED: Domain blocked by rules (" + latency + " ms, RCODE=" + rcode + ", Answers=" + ancount + ")";
                        } else {
                            resultText = "⚡ RESOLVED: " + ancount + " answer(s) received from upstream (" + latency + " ms)";
                        }
                    } else {
                        resultText = "❌ FAILED: Upstream timeout or no DNS response received (" + latency + " ms)";
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvTestResult.setText(resultText);
                            btnTestQuery.setEnabled(true);
                            refreshStats();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvTestResult.setText("Error: " + e.getMessage());
                            btnTestQuery.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    private byte[] buildTestQueryPacket(String domain) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // ID
        baos.write(0x12);
        baos.write(0x34);
        // Flags (standard recursive query)
        baos.write(0x01);
        baos.write(0x00);
        // QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        baos.write(0x00);
        baos.write(0x01);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);
        baos.write(0x00);

        // QNAME
        String[] labels = domain.split("\\.");
        for (String l : labels) {
            byte[] bytes = l.getBytes("UTF-8");
            baos.write(bytes.length);
            baos.write(bytes);
        }
        baos.write(0x00);

        // QTYPE = 1 (A)
        baos.write(0x00);
        baos.write(0x01);
        // QCLASS = 1 (IN)
        baos.write(0x00);
        baos.write(0x01);

        return baos.toByteArray();
    }

    // ==================== TRAFFIC DASHBOARD ====================
    private void setupTrafficDashboard() {
        tvTrafficUpstreamTag = findViewById(R.id.tv_traffic_upstream_tag);
        tvTrafficThroughput = findViewById(R.id.tv_traffic_throughput);
        tvTrafficAvgLatency = findViewById(R.id.tv_traffic_avg_latency);
        tvTrafficCacheRatio = findViewById(R.id.tv_traffic_cache_ratio);
        btnTrafficFlushCache = findViewById(R.id.btn_traffic_flush_cache);
        btnTrafficExportLogs = findViewById(R.id.btn_traffic_export_logs);
        chartTrafficActivity = findViewById(R.id.chart_traffic_activity);
        btnRunBenchmark = findViewById(R.id.btn_run_benchmark);
        pbBenchmark = findViewById(R.id.pb_benchmark);
        rvBenchmark = findViewById(R.id.rv_benchmark);
        rvTopBlocked = findViewById(R.id.rv_top_blocked);
        tvEmptyTopBlocked = findViewById(R.id.tv_empty_top_blocked);
        rvTopAllowed = findViewById(R.id.rv_top_allowed);
        tvEmptyTopAllowed = findViewById(R.id.tv_empty_top_allowed);
        tvCountTypeA = findViewById(R.id.tv_count_type_a);
        tvCountTypeAaaa = findViewById(R.id.tv_count_type_aaaa);
        tvCountTypeHttps = findViewById(R.id.tv_count_type_https);
        tvCountTypeOther = findViewById(R.id.tv_count_type_other);

        // Benchmark Setup
        rvBenchmark.setLayoutManager(new LinearLayoutManager(this));
        benchmarkAdapter = new BenchmarkAdapter(new BenchmarkAdapter.OnApplyListener() {
            @Override
            public void onApply(DnsBenchmark.Result result) {
                applyUpstreamBenchmark(result);
            }
        });
        rvBenchmark.setAdapter(benchmarkAdapter);

        btnRunBenchmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runUpstreamBenchmark();
            }
        });

        // Top Blocked Setup
        rvTopBlocked.setLayoutManager(new LinearLayoutManager(this));
        topBlockedAdapter = new TopDomainAdapter(true, new TopDomainAdapter.OnDomainActionListener() {
            @Override
            public void onAction(DomainStat stat, boolean isBlockedList) {
                addRule(stat.getDomain(), CustomRule.TYPE_WHITE, null);
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshTrafficDashboard();
                    }
                }, 300);
            }
        });
        rvTopBlocked.setAdapter(topBlockedAdapter);

        // Top Allowed Setup
        rvTopAllowed.setLayoutManager(new LinearLayoutManager(this));
        topAllowedAdapter = new TopDomainAdapter(false, new TopDomainAdapter.OnDomainActionListener() {
            @Override
            public void onAction(DomainStat stat, boolean isBlockedList) {
                addRule(stat.getDomain(), CustomRule.TYPE_BLOCK, null);
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshTrafficDashboard();
                    }
                }, 300);
            }
        });
        rvTopAllowed.setAdapter(topAllowedAdapter);

        btnTrafficFlushCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.getCache().clear();
                refreshStats();
                refreshTrafficDashboard();
                Toast.makeText(MainActivity.this, "DNS memory cache flushed", Toast.LENGTH_SHORT).show();
            }
        });

        btnTrafficExportLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLogsCsv();
            }
        });
    }

    private void runUpstreamBenchmark() {
        pbBenchmark.setVisibility(View.VISIBLE);
        btnRunBenchmark.setEnabled(false);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<DnsBenchmark.Result> results = DnsBenchmark.runBenchmark();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        pbBenchmark.setVisibility(View.GONE);
                        btnRunBenchmark.setEnabled(true);
                        benchmarkAdapter.submitList(results);
                    }
                });
            }
        });
    }

    private void applyUpstreamBenchmark(DnsBenchmark.Result result) {
        prefs.edit()
                .putString("upstream_mode", "DOH")
                .putString("doh_url", result.getDohUrl())
                .apply();
        if (etDohUrl != null) {
            etDohUrl.setText(result.getDohUrl());
        }
        if (rbUpstreamDoh != null) {
            rbUpstreamDoh.setChecked(true);
        }
        Toast.makeText(this, "Active upstream changed to " + result.getName(), Toast.LENGTH_SHORT).show();
        if (tvTrafficUpstreamTag != null) {
            tvTrafficUpstreamTag.setText("DoH: " + result.getName());
        }

        int state = ServiceManager.getCurrentState();
        if (state != ServiceManager.STATE_STOPPED) {
            stopActiveService();
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startConfiguredService();
                }
            }, 600);
        }
    }

    private void refreshTrafficDashboard() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final double avgLatency = dbHelper.getAverageResponseTimeMs();
                final int count1Min = dbHelper.getRecentWindowQueryCount(60 * 1000L);
                final long[] stats = dbHelper.getStats();
                final List<TrafficBucket> buckets = dbHelper.getRecentTrafficBuckets(8);
                final List<DomainStat> topBlocked = dbHelper.getTopDomains(5, true);
                final List<DomainStat> topAllowed = dbHelper.getTopDomains(5, false);
                final Map<String, Integer> typeCounts = dbHelper.getQueryTypeCounts();

                String mode = prefs.getString("upstream_mode", "DOH");
                final String upstreamLabel;
                if ("DOT".equalsIgnoreCase(mode)) {
                    upstreamLabel = "DoT: " + prefs.getString("dot_host", "dns.google");
                } else if ("UDP".equalsIgnoreCase(mode)) {
                    upstreamLabel = "UDP: " + prefs.getString("upstream_primary", "1.1.1.1");
                } else {
                    String doh = prefs.getString("doh_url", "https://cloudflare-dns.com/dns-query");
                    if (doh.contains("cloudflare")) {
                        upstreamLabel = "DoH: Cloudflare";
                    } else if (doh.contains("google")) {
                        upstreamLabel = "DoH: Google";
                    } else if (doh.contains("adguard")) {
                        upstreamLabel = "DoH: AdGuard";
                    } else if (doh.contains("quad9")) {
                        upstreamLabel = "DoH: Quad9";
                    } else {
                        upstreamLabel = "DoH: Custom";
                    }
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (tvTrafficUpstreamTag != null) {
                            tvTrafficUpstreamTag.setText(upstreamLabel);
                        }
                        if (tvTrafficThroughput != null) {
                            tvTrafficThroughput.setText(count1Min + " q/m");
                        }
                        if (tvTrafficAvgLatency != null) {
                            tvTrafficAvgLatency.setText(avgLatency > 0 ? String.format(Locale.getDefault(), "%.1f ms", avgLatency) : "< 1 ms");
                        }
                        if (tvTrafficCacheRatio != null) {
                            double cacheRatio = stats[0] > 0 ? ((double) stats[2] / stats[0]) * 100.0 : 0.0;
                            tvTrafficCacheRatio.setText(String.format(Locale.getDefault(), "%.1f%%", cacheRatio));
                        }
                        if (chartTrafficActivity != null) {
                            chartTrafficActivity.setBuckets(buckets);
                        }
                        if (topBlockedAdapter != null) {
                            topBlockedAdapter.submitList(topBlocked);
                            rvTopBlocked.setVisibility(topBlocked.isEmpty() ? View.GONE : View.VISIBLE);
                            tvEmptyTopBlocked.setVisibility(topBlocked.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        if (topAllowedAdapter != null) {
                            topAllowedAdapter.submitList(topAllowed);
                            rvTopAllowed.setVisibility(topAllowed.isEmpty() ? View.GONE : View.VISIBLE);
                            tvEmptyTopAllowed.setVisibility(topAllowed.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        if (tvCountTypeA != null) {
                            int countA = typeCounts.containsKey("A") ? typeCounts.get("A") : 0;
                            int countAaaa = typeCounts.containsKey("AAAA") ? typeCounts.get("AAAA") : 0;
                            int countHttps = typeCounts.containsKey("HTTPS") ? typeCounts.get("HTTPS") : 0;
                            int totalTypeOther = 0;
                            for (Map.Entry<String, Integer> e : typeCounts.entrySet()) {
                                if (!"A".equals(e.getKey()) && !"AAAA".equals(e.getKey()) && !"HTTPS".equals(e.getKey())) {
                                    totalTypeOther += e.getValue();
                                }
                            }
                            tvCountTypeA.setText(String.valueOf(countA));
                            tvCountTypeAaaa.setText(String.valueOf(countAaaa));
                            tvCountTypeHttps.setText(String.valueOf(countHttps));
                            tvCountTypeOther.setText(String.valueOf(totalTypeOther));
                        }
                    }
                });
            }
        });
    }

    private void exportLogsCsv() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String csv = dbHelper.exportLogsCsv();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            ClipData clip = ClipData.newPlainText("DNS Query Logs", csv);
                            cm.setPrimaryClip(clip);
                        }
                        try {
                            Intent sendIntent = new Intent(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, csv);
                            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "NetShield DNS Logs Export");
                            sendIntent.setType("text/csv");
                            startActivity(Intent.createChooser(sendIntent, "Export DNS Logs"));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Logs CSV copied to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    // ==================== LOGS ====================
    private void setupLogs() {
        etSearchLogs = findViewById(R.id.et_search_logs);
        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterBlocked = findViewById(R.id.chip_filter_blocked);
        chipFilterAllowed = findViewById(R.id.chip_filter_allowed);
        btnExportLogs = findViewById(R.id.btn_export_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);
        rvLogs = findViewById(R.id.rv_logs);
        layoutLogsEmpty = findViewById(R.id.layout_logs_empty);

        btnExportLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLogsCsv();
            }
        });

        logAdapter = new LogAdapter();
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        rvLogs.setAdapter(logAdapter);

        logAdapter.setListener(new LogAdapter.OnLogClickListener() {
            @Override
            public void onLogClick(DnsLog log) {
                showLogDetailsDialog(log);
            }

            @Override
            public void onLogLongClick(DnsLog log) {
                showLogDetailsDialog(log);
            }
        });

        chipFilterAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLogFilter("ALL");
            }
        });

        chipFilterBlocked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLogFilter("BLOCKED");
            }
        });

        chipFilterAllowed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLogFilter("ALLOWED");
            }
        });

        btnClearLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Clear Logs")
                        .setMessage("Are you sure you want to erase all query logs?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                backgroundExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        dbHelper.clearLogs();
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                refreshLogs();
                                                refreshStats();
                                            }
                                        });
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        etSearchLogs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                refreshLogs();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setLogFilter(String filter) {
        currentLogFilter = filter;
        chipFilterAll.setBackgroundResource("ALL".equals(filter) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipFilterAll.setTextColor("ALL".equals(filter) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        chipFilterBlocked.setBackgroundResource("BLOCKED".equals(filter) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipFilterBlocked.setTextColor("BLOCKED".equals(filter) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        chipFilterAllowed.setBackgroundResource("ALLOWED".equals(filter) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipFilterAllowed.setTextColor("ALLOWED".equals(filter) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        refreshLogs();
    }

    private void refreshLogs() {
        final String query = etSearchLogs.getText().toString().trim();
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<DnsLog> logs = dbHelper.getFilteredLogs(currentLogFilter, query);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        logAdapter.setLogs(logs);
                        layoutLogsEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void showLogDetailsDialog(final DnsLog log) {
        String[] options = new String[]{"Add to Blocklist", "Add to Whitelist", "Copy Domain"};
        new AlertDialog.Builder(this)
                .setTitle(log.getDomain())
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            addRule(log.getDomain(), CustomRule.TYPE_BLOCK, null);
                        } else if (which == 1) {
                            addRule(log.getDomain(), CustomRule.TYPE_WHITE, null);
                        } else if (which == 2) {
                            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cb.setPrimaryClip(ClipData.newPlainText("DNS Domain", log.getDomain()));
                            Toast.makeText(MainActivity.this, "Copied domain to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ==================== RULES & SOURCES ====================
    private void setupRules() {
        chipSubtabBlocked = findViewById(R.id.chip_subtab_blocked);
        chipSubtabWhitelist = findViewById(R.id.chip_subtab_whitelist);
        chipSubtabMappings = findViewById(R.id.chip_subtab_mappings);
        chipSubtabRemote = findViewById(R.id.chip_subtab_remote);
        tvRulesCount = findViewById(R.id.tv_rules_count);
        btnSyncSources = findViewById(R.id.btn_sync_sources);
        btnAddRuleAction = findViewById(R.id.btn_add_rule_action);
        rvRules = findViewById(R.id.rv_rules);
        layoutRulesEmpty = findViewById(R.id.layout_rules_empty);
        tvRulesEmptyText = findViewById(R.id.tv_rules_empty_text);

        rvRules.setLayoutManager(new LinearLayoutManager(this));

        ruleAdapter = new RuleAdapter();
        ruleAdapter.setListener(new RuleAdapter.OnRuleActionListener() {
            @Override
            public void onToggleEnabled(final CustomRule rule, final boolean enabled) {
                backgroundExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        dbHelper.updateRule(rule);
                        blocklistManager.reloadRules();
                    }
                });
            }

            @Override
            public void onDelete(final CustomRule rule) {
                backgroundExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        dbHelper.deleteRule(rule.getId());
                        blocklistManager.reloadRules();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                refreshRules();
                            }
                        });
                    }
                });
            }
        });

        sourceAdapter = new BlocklistSourceAdapter();
        sourceAdapter.setListener(new BlocklistSourceAdapter.OnSourceActionListener() {
            @Override
            public void onToggleEnabled(final BlocklistSource source, final boolean enabled) {
                backgroundExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        dbHelper.updateBlocklistSource(source);
                    }
                });
            }

            @Override
            public void onDelete(final BlocklistSource source) {
                backgroundExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        dbHelper.deleteBlocklistSource(source.getId());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                refreshRules();
                            }
                        });
                    }
                });
            }
        });

        chipSubtabBlocked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRuleSubtab("BLOCKED");
            }
        });

        chipSubtabWhitelist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRuleSubtab("WHITELIST");
            }
        });

        chipSubtabMappings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRuleSubtab("MAPPINGS");
            }
        });

        chipSubtabRemote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRuleSubtab("REMOTE");
            }
        });

        btnAddRuleAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("REMOTE".equals(currentRuleSubtab)) {
                    showAddSourceDialog();
                } else {
                    showAddRuleDialog();
                }
            }
        });

        btnSyncSources.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSyncSources.setEnabled(false);
                Toast.makeText(MainActivity.this, "Syncing remote blocklists in background…", Toast.LENGTH_SHORT).show();

                blocklistManager.syncRemoteBlocklists(new BlocklistManager.SyncCallback() {
                    @Override
                    public void onProgress(String message) {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onComplete(int totalRules) {
                        btnSyncSources.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Sync complete! Loaded " + totalRules + " rules.", Toast.LENGTH_LONG).show();
                        refreshRules();
                    }

                    @Override
                    public void onError(String error) {
                        btnSyncSources.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Sync error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        setRuleSubtab("BLOCKED");
    }

    private void setRuleSubtab(String subtab) {
        currentRuleSubtab = subtab;

        chipSubtabBlocked.setBackgroundResource("BLOCKED".equals(subtab) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipSubtabBlocked.setTextColor("BLOCKED".equals(subtab) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        chipSubtabWhitelist.setBackgroundResource("WHITELIST".equals(subtab) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipSubtabWhitelist.setTextColor("WHITELIST".equals(subtab) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        chipSubtabMappings.setBackgroundResource("MAPPINGS".equals(subtab) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipSubtabMappings.setTextColor("MAPPINGS".equals(subtab) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        chipSubtabRemote.setBackgroundResource("REMOTE".equals(subtab) ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chipSubtabRemote.setTextColor("REMOTE".equals(subtab) ? Color.parseColor("#031024") : ContextCompat.getColor(this, R.color.text_secondary));

        if ("REMOTE".equals(subtab)) {
            btnAddRuleAction.setText(R.string.btn_add_source);
            btnSyncSources.setVisibility(View.VISIBLE);
            rvRules.setAdapter(sourceAdapter);
        } else {
            btnAddRuleAction.setText(R.string.btn_add_rule);
            btnSyncSources.setVisibility(View.GONE);
            rvRules.setAdapter(ruleAdapter);
        }

        refreshRules();
    }

    private void refreshRules() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if ("REMOTE".equals(currentRuleSubtab)) {
                    final List<BlocklistSource> sources = dbHelper.getBlocklistSources();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            sourceAdapter.setSources(sources);
                            tvRulesCount.setText(sources.size() + " subscription sources configured");
                            layoutRulesEmpty.setVisibility(sources.isEmpty() ? View.VISIBLE : View.GONE);
                            tvRulesEmptyText.setText("No remote subscription sources");
                        }
                    });
                } else {
                    String ruleType = CustomRule.TYPE_BLOCK;
                    if ("WHITELIST".equals(currentRuleSubtab)) ruleType = CustomRule.TYPE_WHITE;
                    if ("MAPPINGS".equals(currentRuleSubtab)) ruleType = CustomRule.TYPE_MAPPING;

                    final List<CustomRule> rules = dbHelper.getRulesByType(ruleType);
                    final String typeLabel = currentRuleSubtab.toLowerCase();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ruleAdapter.setRules(rules);
                            tvRulesCount.setText(rules.size() + " " + typeLabel + " rules active");
                            layoutRulesEmpty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
                            tvRulesEmptyText.setText("No " + typeLabel + " rules defined");
                        }
                    });
                }
            }
        });
    }

    private void showAddRuleDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_rule, null);
        final EditText etDomain = view.findViewById(R.id.et_rule_domain);
        final RadioGroup rgType = view.findViewById(R.id.rg_rule_type);
        final RadioButton rbBlock = view.findViewById(R.id.rb_type_block);
        final RadioButton rbWhite = view.findViewById(R.id.rb_type_white);
        final RadioButton rbMapping = view.findViewById(R.id.rb_type_mapping);
        final LinearLayout layoutTarget = view.findViewById(R.id.layout_target_ip);
        final EditText etTarget = view.findViewById(R.id.et_target_ip);

        if ("WHITELIST".equals(currentRuleSubtab)) {
            rbWhite.setChecked(true);
        } else if ("MAPPINGS".equals(currentRuleSubtab)) {
            rbMapping.setChecked(true);
            layoutTarget.setVisibility(View.VISIBLE);
        } else {
            rbBlock.setChecked(true);
        }

        rgType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                layoutTarget.setVisibility(checkedId == R.id.rb_type_mapping ? View.VISIBLE : View.GONE);
            }
        });

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Add Rule", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String domain = etDomain.getText().toString().trim();
                        if (domain.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Domain cannot be empty", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String type = CustomRule.TYPE_BLOCK;
                        String targetIp = null;
                        if (rbWhite.isChecked()) {
                            type = CustomRule.TYPE_WHITE;
                        } else if (rbMapping.isChecked()) {
                            type = CustomRule.TYPE_MAPPING;
                            targetIp = etTarget.getText().toString().trim();
                        }
                        addRule(domain, type, targetIp);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addRule(final String domain, final String ruleType, final String targetIp) {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                CustomRule rule = new CustomRule(domain, ruleType, targetIp, true);
                dbHelper.insertRule(rule);
                blocklistManager.reloadRules();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Rule added for " + domain, Toast.LENGTH_SHORT).show();
                        refreshRules();
                    }
                });
            }
        });
    }

    private void showAddSourceDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_source, null);
        final EditText etName = view.findViewById(R.id.et_source_name);
        final EditText etUrl = view.findViewById(R.id.et_source_url);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Add Source", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = etName.getText().toString().trim();
                        final String url = etUrl.getText().toString().trim();
                        if (name.isEmpty() || url.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Name and URL are required", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        backgroundExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                BlocklistSource source = new BlocklistSource(name, url, true);
                                dbHelper.insertBlocklistSource(source);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Source added", Toast.LENGTH_SHORT).show();
                                        refreshRules();
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== SETTINGS ====================
    private void setupSettings() {
        rgUpstreamProtocol = findViewById(R.id.rg_upstream_protocol);
        rbUpstreamDoh = findViewById(R.id.rb_upstream_doh);
        rbUpstreamDot = findViewById(R.id.rb_upstream_dot);
        rbUpstreamUdp = findViewById(R.id.rb_upstream_udp);
        layoutDohSettings = findViewById(R.id.layout_doh_settings);
        layoutDotSettings = findViewById(R.id.layout_dot_settings);
        layoutUdpSettings = findViewById(R.id.layout_udp_settings);
        etDohUrl = findViewById(R.id.et_doh_url);
        etDotHost = findViewById(R.id.et_dot_host);
        etPrimaryDns = findViewById(R.id.et_primary_dns);
        etSecondaryDns = findViewById(R.id.et_secondary_dns);
        rgBlockAction = findViewById(R.id.rg_block_action);
        rbBlockZero = findViewById(R.id.rb_block_zero);
        rbBlockNxdomain = findViewById(R.id.rb_block_nxdomain);
        switchCacheEnable = findViewById(R.id.switch_cache_enable);
        btnClearCacheAction = findViewById(R.id.btn_clear_cache_action);
        btnConfigureSplitTunnel = findViewById(R.id.btn_configure_split_tunnel);
        switchAutoStart = findViewById(R.id.switch_auto_start);
        etServerPort = findViewById(R.id.et_server_port);
        btnExportBackup = findViewById(R.id.btn_export_backup);
        btnImportBackup = findViewById(R.id.btn_import_backup);

        TextView tvAboutVersion = findViewById(R.id.tv_about_version);
        if (tvAboutVersion != null) {
            tvAboutVersion.setText(getString(R.string.about_version_format, BuildConfig.VERSION_NAME));
        }

        // Load Upstream
        String upstreamMode = prefs.getString("upstream_mode", "DOH");
        if ("DOT".equalsIgnoreCase(upstreamMode)) {
            rbUpstreamDot.setChecked(true);
            layoutDohSettings.setVisibility(View.GONE);
            layoutDotSettings.setVisibility(View.VISIBLE);
            layoutUdpSettings.setVisibility(View.GONE);
        } else if ("UDP".equalsIgnoreCase(upstreamMode)) {
            rbUpstreamUdp.setChecked(true);
            layoutDohSettings.setVisibility(View.GONE);
            layoutDotSettings.setVisibility(View.GONE);
            layoutUdpSettings.setVisibility(View.VISIBLE);
        } else {
            rbUpstreamDoh.setChecked(true);
            layoutDohSettings.setVisibility(View.VISIBLE);
            layoutDotSettings.setVisibility(View.GONE);
            layoutUdpSettings.setVisibility(View.GONE);
        }

        etDohUrl.setText(prefs.getString("doh_url", "https://cloudflare-dns.com/dns-query"));
        etDotHost.setText(prefs.getString("dot_host", "dns.google"));
        etPrimaryDns.setText(prefs.getString("upstream_primary", "1.1.1.1"));
        etSecondaryDns.setText(prefs.getString("upstream_secondary", "8.8.8.8"));

        rgUpstreamProtocol.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_upstream_dot) {
                    prefs.edit().putString("upstream_mode", "DOT").apply();
                    layoutDohSettings.setVisibility(View.GONE);
                    layoutDotSettings.setVisibility(View.VISIBLE);
                    layoutUdpSettings.setVisibility(View.GONE);
                } else if (checkedId == R.id.rb_upstream_udp) {
                    prefs.edit().putString("upstream_mode", "UDP").apply();
                    layoutDohSettings.setVisibility(View.GONE);
                    layoutDotSettings.setVisibility(View.GONE);
                    layoutUdpSettings.setVisibility(View.VISIBLE);
                } else {
                    prefs.edit().putString("upstream_mode", "DOH").apply();
                    layoutDohSettings.setVisibility(View.VISIBLE);
                    layoutDotSettings.setVisibility(View.GONE);
                    layoutUdpSettings.setVisibility(View.GONE);
                }
            }
        });

        // Provider Presets
        View pCf = findViewById(R.id.preset_cloudflare);
        if (pCf != null) {
            pCf.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPresetSettings("Cloudflare", "https://cloudflare-dns.com/dns-query", "cloudflare-dns.com", "1.1.1.1", "1.0.0.1");
                }
            });
        }
        View pGoog = findViewById(R.id.preset_google);
        if (pGoog != null) {
            pGoog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPresetSettings("Google", "https://dns.google/dns-query", "dns.google", "8.8.8.8", "8.8.4.4");
                }
            });
        }
        View pAdg = findViewById(R.id.preset_adguard);
        if (pAdg != null) {
            pAdg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPresetSettings("AdGuard", "https://dns.adguard-dns.com/dns-query", "dns.adguard-dns.com", "94.140.14.14", "94.140.15.15");
                }
            });
        }
        View pQuad = findViewById(R.id.preset_quad9);
        if (pQuad != null) {
            pQuad.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPresetSettings("Quad9", "https://dns.quad9.net/dns-query", "dns.quad9.net", "9.9.9.9", "149.112.112.112");
                }
            });
        }

        // DoH Presets
        findViewById(R.id.preset_cf_doh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDohUrl.setText("https://cloudflare-dns.com/dns-query");
                prefs.edit().putString("doh_url", "https://cloudflare-dns.com/dns-query").apply();
            }
        });
        findViewById(R.id.preset_google_doh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDohUrl.setText("https://dns.google/dns-query");
                prefs.edit().putString("doh_url", "https://dns.google/dns-query").apply();
            }
        });
        findViewById(R.id.preset_adguard_doh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etDohUrl.setText("https://dns.adguard-dns.com/dns-query");
                prefs.edit().putString("doh_url", "https://dns.adguard-dns.com/dns-query").apply();
            }
        });

        etDohUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                prefs.edit().putString("doh_url", s.toString().trim()).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etDotHost.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                prefs.edit().putString("dot_host", s.toString().trim()).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etPrimaryDns.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                prefs.edit().putString("upstream_primary", s.toString().trim()).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etSecondaryDns.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                prefs.edit().putString("upstream_secondary", s.toString().trim()).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Block Action
        String blockAction = prefs.getString("block_action", "ZERO_IP");
        if ("NXDOMAIN".equalsIgnoreCase(blockAction)) {
            rbBlockNxdomain.setChecked(true);
        } else {
            rbBlockZero.setChecked(true);
        }

        rgBlockAction.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putString("block_action", checkedId == R.id.rb_block_nxdomain ? "NXDOMAIN" : "ZERO_IP").apply();
            }
        });

        // Cache
        switchCacheEnable.setChecked(prefs.getBoolean("cache_enabled", true));
        switchCacheEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean("cache_enabled", isChecked).apply();
            }
        });

        btnClearCacheAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.getCache().clear();
                Toast.makeText(MainActivity.this, "In-memory DNS cache cleared", Toast.LENGTH_SHORT).show();
                refreshStats();
            }
        });

        // Split Tunneling
        btnConfigureSplitTunnel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSplitTunnelDialog();
            }
        });

        // Auto Start
        switchAutoStart.setChecked(prefs.getBoolean("auto_start_on_boot", false));
        switchAutoStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean("auto_start_on_boot", isChecked).apply();
            }
        });

        // Server Port
        etServerPort.setText(String.valueOf(prefs.getInt("server_port", 5353)));
        etServerPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                try {
                    int p = Integer.parseInt(s.toString().trim());
                    if (p > 0 && p <= 65535) {
                        prefs.edit().putInt("server_port", p).apply();
                        tvServerAddress.setText("127.0.0.1:" + p);
                    }
                } catch (Exception ignored) {}
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Backup & Restore
        btnExportBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String json = ConfigBackupUtils.exportConfigJson(MainActivity.this);
                if (json != null) {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("DNS Config JSON", json));
                    Toast.makeText(MainActivity.this, "Configuration copied to clipboard!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to export config", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnImportBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText etJson = new EditText(MainActivity.this);
                etJson.setHint("Paste JSON configuration here…");
                etJson.setMinLines(5);
                etJson.setMaxLines(10);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Import Configuration")
                        .setView(etJson)
                        .setPositiveButton("Import", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String text = etJson.getText().toString().trim();
                                if (ConfigBackupUtils.importConfigJson(MainActivity.this, text)) {
                                    Toast.makeText(MainActivity.this, "Config imported successfully!", Toast.LENGTH_SHORT).show();
                                    setupSettings();
                                    refreshRules();
                                } else {
                                    Toast.makeText(MainActivity.this, "Invalid JSON configuration", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void showSplitTunnelDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_split_tunnel, null);
        final EditText etSearch = view.findViewById(R.id.et_search_apps);
        final ProgressBar pbLoading = view.findViewById(R.id.pb_loading_apps);
        final RecyclerView rvApps = view.findViewById(R.id.rv_split_apps);

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        final AppSelectAdapter appAdapter = new AppSelectAdapter();
        rvApps.setAdapter(appAdapter);

        pbLoading.setVisibility(View.VISIBLE);

        final Set<String> currentExcluded = prefs.getStringSet("split_tunnel_apps", new HashSet<String>());

        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                final List<AppSelectAdapter.AppItem> appItems = new ArrayList<>();

                for (ApplicationInfo app : apps) {
                    // Include all launchable or installed apps
                    try {
                        String label = pm.getApplicationLabel(app).toString();
                        Drawable icon = pm.getApplicationIcon(app);
                        appItems.add(new AppSelectAdapter.AppItem(label, app.packageName, icon, false));
                    } catch (Exception ignored) {}
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
                        appAdapter.setItems(appItems, currentExcluded);
                    }
                });
            }
        });

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Save Bypass List", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Set<String> selected = appAdapter.getSelectedPackages();
                        prefs.edit().putStringSet("split_tunnel_apps", selected).apply();
                        Toast.makeText(MainActivity.this, selected.size() + " apps set to bypass VPN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyPresetSettings(String name, String dohUrl, String dotHost, String primaryDns, String secondaryDns) {
        if (etDohUrl != null) etDohUrl.setText(dohUrl);
        if (etDotHost != null) etDotHost.setText(dotHost);
        if (etPrimaryDns != null) etPrimaryDns.setText(primaryDns);
        if (etSecondaryDns != null) etSecondaryDns.setText(secondaryDns);

        prefs.edit()
                .putString("doh_url", dohUrl)
                .putString("dot_host", dotHost)
                .putString("upstream_primary", primaryDns)
                .putString("upstream_secondary", secondaryDns)
                .apply();

        Toast.makeText(this, name + " settings preset applied", Toast.LENGTH_SHORT).show();
    }
}
