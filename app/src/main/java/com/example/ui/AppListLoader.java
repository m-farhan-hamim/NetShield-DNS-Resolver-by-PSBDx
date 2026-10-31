package com.example.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Builds the list for the app pickers, as fast as Android allows.
 *
 * What made it slow: for every installed app it loaded the label AND the icon, one app after the other,
 * before showing anything. Now the icon is NOT loaded here at all (the adapter loads icons only for the
 * rows on screen), labels are read on several threads at once, and labels are remembered for the whole
 * run of the app, so opening a picker a second time is almost instant.
 */
public final class AppListLoader {

    /** package + the time its apk last changed -> label. An updated app gets a fresh key. */
    private static final Map<String, String> LABELS = new ConcurrentHashMap<>();

    private AppListLoader() {
    }

    /** Blocking: call it from a background thread. Sorted by name. Icons are left empty on purpose. */
    public static List<AppSelectAdapter.AppItem> loadItems(Context context) {
        final PackageManager pm = context.getPackageManager();
        // No GET_META_DATA: nothing here needs the manifest metadata, and it makes the query slower.
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<AppSelectAdapter.AppItem>> futures = new ArrayList<>(apps.size());
        for (final ApplicationInfo app : apps) {
            futures.add(pool.submit(new Callable<AppSelectAdapter.AppItem>() {
                @Override
                public AppSelectAdapter.AppItem call() {
                    String key = app.packageName + ":" + (app.sourceDir == null ? 0L : new File(app.sourceDir).lastModified());
                    String label = LABELS.get(key);
                    if (label == null) {
                        label = String.valueOf(pm.getApplicationLabel(app));
                        LABELS.put(key, label);
                    }
                    return new AppSelectAdapter.AppItem(label, app.packageName, null, false, isPureSystem(app));
                }
            }));
        }
        List<AppSelectAdapter.AppItem> items = new ArrayList<>(apps.size());
        for (Future<AppSelectAdapter.AppItem> f : futures) {
            try {
                items.add(f.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // one app that cannot be read must not hide all the others
            }
        }
        pool.shutdown();
        Collections.sort(items, new Comparator<AppSelectAdapter.AppItem>() {
            @Override
            public int compare(AppSelectAdapter.AppItem a, AppSelectAdapter.AppItem b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return items;
    }

    /** Built-in system apps that were never updated; an updated one (Chrome, Play Store) counts as a normal app. */
    private static boolean isPureSystem(ApplicationInfo app) {
        return (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }
}
