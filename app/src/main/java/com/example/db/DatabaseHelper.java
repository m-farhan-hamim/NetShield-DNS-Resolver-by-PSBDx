package com.example.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "dns_resolver.db";
    private static final int DATABASE_VERSION = 1;

    // Table: query_logs
    public static final String TABLE_LOGS = "query_logs";
    public static final String COL_LOG_ID = "id";
    public static final String COL_LOG_TIME = "timestamp";
    public static final String COL_LOG_DOMAIN = "domain";
    public static final String COL_LOG_TYPE = "query_type";
    public static final String COL_LOG_STATUS = "status";
    public static final String COL_LOG_RESPONSE_TIME = "response_time";
    public static final String COL_LOG_UPSTREAM = "upstream";
    public static final String COL_LOG_CLIENT_IP = "client_ip";

    // Table: custom_rules
    public static final String TABLE_RULES = "custom_rules";
    public static final String COL_RULE_ID = "id";
    public static final String COL_RULE_DOMAIN = "domain";
    public static final String COL_RULE_TARGET_IP = "target_ip";
    public static final String COL_RULE_TYPE = "rule_type";
    public static final String COL_RULE_ENABLED = "enabled";

    // Table: blocklist_sources
    public static final String TABLE_SOURCES = "blocklist_sources";
    public static final String COL_SOURCE_ID = "id";
    public static final String COL_SOURCE_NAME = "name";
    public static final String COL_SOURCE_URL = "url";
    public static final String COL_SOURCE_ENABLED = "enabled";
    public static final String COL_SOURCE_LAST_UPDATED = "last_updated";
    public static final String COL_SOURCE_RULE_COUNT = "rule_count";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_LOGS + " ("
                + COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LOG_TIME + " INTEGER, "
                + COL_LOG_DOMAIN + " TEXT, "
                + COL_LOG_TYPE + " TEXT, "
                + COL_LOG_STATUS + " TEXT, "
                + COL_LOG_RESPONSE_TIME + " INTEGER, "
                + COL_LOG_UPSTREAM + " TEXT, "
                + COL_LOG_CLIENT_IP + " TEXT)");

        db.execSQL("CREATE INDEX idx_logs_time ON " + TABLE_LOGS + " (" + COL_LOG_TIME + " DESC)");

        db.execSQL("CREATE TABLE " + TABLE_RULES + " ("
                + COL_RULE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_RULE_DOMAIN + " TEXT UNIQUE, "
                + COL_RULE_TARGET_IP + " TEXT, "
                + COL_RULE_TYPE + " TEXT, "
                + COL_RULE_ENABLED + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_SOURCES + " ("
                + COL_SOURCE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SOURCE_NAME + " TEXT, "
                + COL_SOURCE_URL + " TEXT UNIQUE, "
                + COL_SOURCE_ENABLED + " INTEGER, "
                + COL_SOURCE_LAST_UPDATED + " INTEGER, "
                + COL_SOURCE_RULE_COUNT + " INTEGER)");

        // Seed default blocklists
        seedDefaultSources(db);
        // Seed some sample custom rules
        seedDefaultRules(db);
    }

    private void seedDefaultSources(SQLiteDatabase db) {
        ContentValues cv1 = new ContentValues();
        cv1.put(COL_SOURCE_NAME, "AdAway Default Blocklist");
        cv1.put(COL_SOURCE_URL, "https://adaway.org/hosts.txt");
        cv1.put(COL_SOURCE_ENABLED, 1);
        cv1.put(COL_SOURCE_LAST_UPDATED, 0);
        cv1.put(COL_SOURCE_RULE_COUNT, 0);
        db.insert(TABLE_SOURCES, null, cv1);

        ContentValues cv2 = new ContentValues();
        cv2.put(COL_SOURCE_NAME, "StevenBlack Unified Hosts");
        cv2.put(COL_SOURCE_URL, "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts");
        cv2.put(COL_SOURCE_ENABLED, 1);
        cv2.put(COL_SOURCE_LAST_UPDATED, 0);
        cv2.put(COL_SOURCE_RULE_COUNT, 0);
        db.insert(TABLE_SOURCES, null, cv2);
    }

    private void seedDefaultRules(SQLiteDatabase db) {
        // Block sample telemetry
        addRuleInternal(db, "telemetry.badtracker.com", "", CustomRule.TYPE_BLOCK, true);
        addRuleInternal(db, "adservice.google.com", "", CustomRule.TYPE_BLOCK, true);
        addRuleInternal(db, "doubleclick.net", "", CustomRule.TYPE_BLOCK, true);

        // Whitelist sample essential services
        addRuleInternal(db, "connectivitycheck.gstatic.com", "", CustomRule.TYPE_WHITE, true);
        addRuleInternal(db, "clients3.google.com", "", CustomRule.TYPE_WHITE, true);

        // Custom mapping sample
        addRuleInternal(db, "router.local", "192.168.1.1", CustomRule.TYPE_MAPPING, true);
        addRuleInternal(db, "nas.home", "192.168.1.100", CustomRule.TYPE_MAPPING, true);
    }

    private void addRuleInternal(SQLiteDatabase db, String domain, String targetIp, String type, boolean enabled) {
        ContentValues cv = new ContentValues();
        cv.put(COL_RULE_DOMAIN, domain);
        cv.put(COL_RULE_TARGET_IP, targetIp);
        cv.put(COL_RULE_TYPE, type);
        cv.put(COL_RULE_ENABLED, enabled ? 1 : 0);
        db.insertWithOnConflict(TABLE_RULES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RULES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SOURCES);
        onCreate(db);
    }

    // --- Log operations ---
    public synchronized void insertLog(DnsLog log) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_TIME, log.getTimestamp());
        cv.put(COL_LOG_DOMAIN, log.getDomain());
        cv.put(COL_LOG_TYPE, log.getQueryType());
        cv.put(COL_LOG_STATUS, log.getStatus());
        cv.put(COL_LOG_RESPONSE_TIME, log.getResponseTimeMs());
        cv.put(COL_LOG_UPSTREAM, log.getUpstream());
        cv.put(COL_LOG_CLIENT_IP, log.getClientIp());
        db.insert(TABLE_LOGS, null, cv);

        // Limit logs to keep DB fast and compact (keep last 2000 entries)
        db.execSQL("DELETE FROM " + TABLE_LOGS + " WHERE id NOT IN (SELECT id FROM " + TABLE_LOGS + " ORDER BY id DESC LIMIT 2000)");
    }

    public synchronized List<DnsLog> getRecentLogs(int limit, String search, String statusFilter) {
        List<DnsLog> logs = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        StringBuilder query = new StringBuilder("SELECT * FROM " + TABLE_LOGS + " WHERE 1=1");
        List<String> args = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            query.append(" AND " + COL_LOG_DOMAIN + " LIKE ?");
            args.add("%" + search.trim() + "%");
        }

        if (statusFilter != null && !statusFilter.equals("ALL")) {
            query.append(" AND " + COL_LOG_STATUS + " = ?");
            args.add(statusFilter);
        }

        query.append(" ORDER BY " + COL_LOG_TIME + " DESC LIMIT ?");
        args.add(String.valueOf(limit));

        Cursor cursor = db.rawQuery(query.toString(), args.toArray(new String[0]));
        if (cursor != null) {
            while (cursor.moveToNext()) {
                DnsLog log = new DnsLog();
                log.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_ID)));
                log.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_TIME)));
                log.setDomain(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_DOMAIN)));
                log.setQueryType(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_TYPE)));
                log.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_STATUS)));
                log.setResponseTimeMs(cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_RESPONSE_TIME)));
                log.setUpstream(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_UPSTREAM)));
                log.setClientIp(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_CLIENT_IP)));
                logs.add(log);
            }
            cursor.close();
        }
        return logs;
    }

    public synchronized List<DnsLog> getFilteredLogs(String statusFilter, String search) {
        return getRecentLogs(500, search, statusFilter);
    }

    public synchronized void clearLogs() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOGS, null, null);
    }

    public synchronized long[] getStats() {
        long[] stats = new long[4]; // [0]=total, [1]=blocked, [2]=cached, [3]=allowed
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_LOG_STATUS + ", COUNT(*) FROM " + TABLE_LOGS + " GROUP BY " + COL_LOG_STATUS, null);
        long total = 0;
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String status = cursor.getString(0);
                long count = cursor.getLong(1);
                total += count;
                if ("BLOCKED".equalsIgnoreCase(status)) {
                    stats[1] += count;
                } else if ("CACHED".equalsIgnoreCase(status)) {
                    stats[2] += count;
                } else {
                    stats[3] += count;
                }
            }
            cursor.close();
        }
        stats[0] = total;
        return stats;
    }

    public synchronized List<DomainStat> getTopDomains(int limit, boolean blockedOnly) {
        List<DomainStat> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = blockedOnly
                ? "SELECT " + COL_LOG_DOMAIN + ", COUNT(*) as cnt, " + COL_LOG_STATUS + " FROM " + TABLE_LOGS + " WHERE " + COL_LOG_STATUS + " = 'BLOCKED' GROUP BY " + COL_LOG_DOMAIN + " ORDER BY cnt DESC LIMIT ?"
                : "SELECT " + COL_LOG_DOMAIN + ", COUNT(*) as cnt, " + COL_LOG_STATUS + " FROM " + TABLE_LOGS + " WHERE " + COL_LOG_STATUS + " != 'BLOCKED' GROUP BY " + COL_LOG_DOMAIN + " ORDER BY cnt DESC LIMIT ?";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit)});
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new DomainStat(cursor.getString(0), cursor.getInt(1), cursor.getString(2)));
            }
            cursor.close();
        }
        return list;
    }

    public synchronized double getAverageResponseTimeMs() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT AVG(" + COL_LOG_RESPONSE_TIME + ") FROM " + TABLE_LOGS + " WHERE " + COL_LOG_STATUS + " != 'BLOCKED'", null);
        double avg = 0;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                avg = cursor.getDouble(0);
            }
            cursor.close();
        }
        return avg;
    }

    /** Average response time over only the last {@code windowMs}, for a "live" reading. */
    public synchronized double getAverageResponseTimeMs(long windowMs) {
        long since = System.currentTimeMillis() - windowMs;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT AVG(" + COL_LOG_RESPONSE_TIME + ") FROM " + TABLE_LOGS
                        + " WHERE " + COL_LOG_STATUS + " != 'BLOCKED' AND " + COL_LOG_TIME + " >= ?",
                new String[]{String.valueOf(since)});
        double avg = 0;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                avg = cursor.getDouble(0);
            }
            cursor.close();
        }
        return avg;
    }

    public synchronized int getRecentWindowQueryCount(long durationMs) {
        long since = System.currentTimeMillis() - durationMs;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOGS + " WHERE " + COL_LOG_TIME + " >= ?", new String[]{String.valueOf(since)});
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public synchronized Map<String, Integer> getQueryTypeCounts() {
        Map<String, Integer> map = new LinkedHashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_LOG_TYPE + ", COUNT(*) FROM " + TABLE_LOGS + " GROUP BY " + COL_LOG_TYPE + " ORDER BY COUNT(*) DESC", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String type = cursor.getString(0);
                if (type == null || type.isEmpty()) type = "A";
                map.put(type, cursor.getInt(1));
            }
            cursor.close();
        }
        return map;
    }

    public synchronized List<TrafficBucket> getRecentTrafficBuckets(int bucketCount) {
        List<TrafficBucket> buckets = new ArrayList<>();
        long now = System.currentTimeMillis();
        long bucketSpan = (2 * 3600 * 1000L) / bucketCount; // 2 hours window divided into buckets
        if (bucketSpan <= 0) bucketSpan = 15 * 60 * 1000L;

        SQLiteDatabase db = getReadableDatabase();
        for (int i = bucketCount - 1; i >= 0; i--) {
            long start = now - (long)(i + 1) * bucketSpan;
            long end = now - (long) i * bucketSpan;
            String label = (i == 0) ? "Now" : "-" + ((i * (bucketSpan / 60000))) + "m";

            int allowed = 0;
            int blocked = 0;
            Cursor cursor = db.rawQuery("SELECT " + COL_LOG_STATUS + ", COUNT(*) FROM " + TABLE_LOGS + " WHERE " + COL_LOG_TIME + " >= ? AND " + COL_LOG_TIME + " < ? GROUP BY " + COL_LOG_STATUS,
                    new String[]{String.valueOf(start), String.valueOf(end)});
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String status = cursor.getString(0);
                    int count = cursor.getInt(1);
                    if ("BLOCKED".equalsIgnoreCase(status)) {
                        blocked += count;
                    } else {
                        allowed += count;
                    }
                }
                cursor.close();
            }
            buckets.add(new TrafficBucket(label, allowed, blocked));
        }
        return buckets;
    }

    public synchronized String exportLogsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,FormattedTime,Domain,RecordType,Status,ResponseTimeMs,Upstream,ClientIp\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        List<DnsLog> logs = getRecentLogs(2000, null, "ALL");
        for (DnsLog log : logs) {
            sb.append(log.getTimestamp()).append(",");
            sb.append("\"").append(sdf.format(new Date(log.getTimestamp()))).append("\",");
            sb.append("\"").append(log.getDomain()).append("\",");
            sb.append(log.getQueryType()).append(",");
            sb.append(log.getStatus()).append(",");
            sb.append(log.getResponseTimeMs()).append(",");
            sb.append("\"").append(log.getUpstream() != null ? log.getUpstream() : "").append("\",");
            sb.append("\"").append(log.getClientIp() != null ? log.getClientIp() : "").append("\"\n");
        }
        return sb.toString();
    }

    // --- Rule operations ---
    public synchronized long insertRule(CustomRule rule) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_RULE_DOMAIN, rule.getDomain().trim().toLowerCase());
        cv.put(COL_RULE_TARGET_IP, rule.getTargetIp() != null ? rule.getTargetIp().trim() : "");
        cv.put(COL_RULE_TYPE, rule.getRuleType());
        cv.put(COL_RULE_ENABLED, rule.isEnabled() ? 1 : 0);
        return db.insertWithOnConflict(TABLE_RULES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void deleteRule(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RULES, COL_RULE_ID + "=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateRuleEnabled(long id, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_RULE_ENABLED, enabled ? 1 : 0);
        db.update(TABLE_RULES, cv, COL_RULE_ID + "=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateRule(CustomRule rule) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_RULE_DOMAIN, rule.getDomain().trim().toLowerCase());
        cv.put(COL_RULE_TARGET_IP, rule.getTargetIp() != null ? rule.getTargetIp().trim() : "");
        cv.put(COL_RULE_TYPE, rule.getRuleType());
        cv.put(COL_RULE_ENABLED, rule.isEnabled() ? 1 : 0);
        db.update(TABLE_RULES, cv, COL_RULE_ID + "=?", new String[]{String.valueOf(rule.getId())});
    }

    public synchronized List<CustomRule> getRulesByType(String type) {
        List<CustomRule> rules = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RULES, null, COL_RULE_TYPE + "=?", new String[]{type}, null, null, COL_RULE_DOMAIN + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                CustomRule rule = new CustomRule();
                rule.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_RULE_ID)));
                rule.setDomain(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_DOMAIN)));
                rule.setTargetIp(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_TARGET_IP)));
                rule.setRuleType(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_TYPE)));
                rule.setEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COL_RULE_ENABLED)) == 1);
                rules.add(rule);
            }
            cursor.close();
        }
        return rules;
    }

    public synchronized List<CustomRule> getAllRules() {
        List<CustomRule> rules = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RULES, null, null, null, null, null, COL_RULE_DOMAIN + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                CustomRule rule = new CustomRule();
                rule.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_RULE_ID)));
                rule.setDomain(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_DOMAIN)));
                rule.setTargetIp(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_TARGET_IP)));
                rule.setRuleType(cursor.getString(cursor.getColumnIndexOrThrow(COL_RULE_TYPE)));
                rule.setEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COL_RULE_ENABLED)) == 1);
                rules.add(rule);
            }
            cursor.close();
        }
        return rules;
    }

    // --- Blocklist Source operations ---
    public synchronized List<BlocklistSource> getBlocklistSources() {
        List<BlocklistSource> sources = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SOURCES, null, null, null, null, null, COL_SOURCE_ID + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                BlocklistSource s = new BlocklistSource();
                s.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_SOURCE_ID)));
                s.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE_NAME)));
                s.setUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE_URL)));
                s.setEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COL_SOURCE_ENABLED)) == 1);
                s.setLastUpdated(cursor.getLong(cursor.getColumnIndexOrThrow(COL_SOURCE_LAST_UPDATED)));
                s.setRuleCount(cursor.getInt(cursor.getColumnIndexOrThrow(COL_SOURCE_RULE_COUNT)));
                sources.add(s);
            }
            cursor.close();
        }
        return sources;
    }

    public synchronized long insertBlocklistSource(BlocklistSource source) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SOURCE_NAME, source.getName());
        cv.put(COL_SOURCE_URL, source.getUrl());
        cv.put(COL_SOURCE_ENABLED, source.isEnabled() ? 1 : 0);
        cv.put(COL_SOURCE_LAST_UPDATED, source.getLastUpdated());
        cv.put(COL_SOURCE_RULE_COUNT, source.getRuleCount());
        return db.insertWithOnConflict(TABLE_SOURCES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void updateBlocklistSource(BlocklistSource source) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SOURCE_NAME, source.getName());
        cv.put(COL_SOURCE_URL, source.getUrl());
        cv.put(COL_SOURCE_ENABLED, source.isEnabled() ? 1 : 0);
        cv.put(COL_SOURCE_LAST_UPDATED, source.getLastUpdated());
        cv.put(COL_SOURCE_RULE_COUNT, source.getRuleCount());
        db.update(TABLE_SOURCES, cv, COL_SOURCE_ID + "=?", new String[]{String.valueOf(source.getId())});
    }

    public synchronized void deleteBlocklistSource(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SOURCES, COL_SOURCE_ID + "=?", new String[]{String.valueOf(id)});
    }
}
