package com.daytrace.ledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class PendingAiNotificationStore {
    private static final String PREFERENCES = "daytrace_pending_ai_notifications";
    private static final String EVENTS_KEY = "pending_notifications";
    private static final Object LOCK = new Object();
    static final long AI_TASK_TIMEOUT_MS = 5 * 60_000L;
    private static final long WORKER_LEASE_MS = 10 * 60_000L;
    private static final long[] RETRY_DELAYS_MS = {
            60_000L,
            5 * 60_000L,
            15 * 60_000L,
            60 * 60_000L,
            6 * 60 * 60_000L,
            24 * 60 * 60_000L
    };

    private final SharedPreferences preferences;

    PendingAiNotificationStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    AddResult add(
            String packageName,
            String title,
            String body,
            long postTime,
            NotificationIconFingerprint.Snapshot icon) {
        return add(packageName, title, body, postTime, icon, null);
    }

    AddResult add(
            String packageName,
            String title,
            String body,
            long postTime,
            NotificationIconFingerprint.Snapshot icon,
            String eventId) {
        synchronized (LOCK) {
            JSONArray events = read();
            String id = eventId == null || eventId.isEmpty()
                    ? shortHash(packageName + "|" + postTime + "|" + title + "|" + body)
                    : eventId;
            if (contains(events, id)) return AddResult.DUPLICATE;

            try {
                JSONObject event = new JSONObject();
                event.put("id", id);
                event.put("packageName", packageName);
                event.put("title", title);
                event.put("body", body);
                event.put("postTime", postTime);
                event.put("retryCount", 0);
                event.put("nextAttemptAt", 0L);
                event.put("inProgressUntil", 0L);
                event.put("processingStartedAt", 0L);
                if (icon != null) {
                    event.put("appLabel", icon.appLabel);
                    event.put("largeIconSimilarity", icon.largeSimilarity);
                    event.put("smallIconSimilarity", icon.smallSimilarity);
                    event.put("iconVerdict", icon.verdict);
                }
                events.put(event);
                boolean saved = preferences.edit().putString(EVENTS_KEY, events.toString()).commit();
                return saved ? AddResult.ADDED : AddResult.FAILED;
            } catch (JSONException error) {
                return AddResult.FAILED;
            }
        }
    }

    Item claimNextReady(long now) {
        synchronized (LOCK) {
            JSONArray events = read();
            for (int index = 0; index < events.length(); index++) {
                Item item = itemAt(events, index);
                if (item == null) continue;
                long processingDeadline = item.processingStartedAt > 0
                        ? item.processingStartedAt + AI_TASK_TIMEOUT_MS
                        : Long.MAX_VALUE;
                boolean deadlineReached = now >= processingDeadline;
                if (item.inProgressUntil > now
                        || (!deadlineReached && item.nextAttemptAt > now)) continue;
                JSONObject event = events.optJSONObject(index);
                if (event == null) continue;
                try {
                    if (event.optLong("processingStartedAt", 0L) <= 0) {
                        event.put("processingStartedAt", now);
                    }
                    event.put("inProgressUntil", now + WORKER_LEASE_MS);
                    if (!preferences.edit()
                            .putString(EVENTS_KEY, events.toString())
                            .commit()) {
                        return null;
                    }
                    return itemAt(events, index);
                } catch (JSONException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    long nextAvailableAt() {
        synchronized (LOCK) {
            JSONArray events = read();
            long next = Long.MAX_VALUE;
            for (int index = 0; index < events.length(); index++) {
                Item item = itemAt(events, index);
                if (item != null) {
                    long availableAt = Math.max(item.nextAttemptAt, item.inProgressUntil);
                    if (item.inProgressUntil <= System.currentTimeMillis()
                            && item.processingStartedAt > 0) {
                        availableAt = Math.min(
                                availableAt,
                                item.processingStartedAt + AI_TASK_TIMEOUT_MS);
                    }
                    next = Math.min(next, availableAt);
                }
            }
            return next == Long.MAX_VALUE ? -1 : next;
        }
    }

    void markFailure(String id, String error) {
        synchronized (LOCK) {
            JSONArray events = read();
            for (int index = 0; index < events.length(); index++) {
                JSONObject event = events.optJSONObject(index);
                if (event == null || !id.equals(event.optString("id"))) continue;
                int retryCount = event.optInt("retryCount", 0) + 1;
                long delay = RETRY_DELAYS_MS[
                        Math.min(retryCount - 1, RETRY_DELAYS_MS.length - 1)];
                try {
                    event.put("retryCount", retryCount);
                    event.put("nextAttemptAt", System.currentTimeMillis() + delay);
                    event.put("inProgressUntil", 0L);
                    event.put("processingStartedAt", 0L);
                    event.put("lastError", truncate(error, 240));
                    preferences.edit().putString(EVENTS_KEY, events.toString()).apply();
                } catch (JSONException ignored) {
                    // Keep the original pending record if this metadata update fails.
                }
                return;
            }
        }
    }

    void remove(String id) {
        synchronized (LOCK) {
            JSONArray events = read();
            JSONArray remaining = new JSONArray();
            for (int index = 0; index < events.length(); index++) {
                JSONObject event = events.optJSONObject(index);
                if (event != null && !id.equals(event.optString("id"))) {
                    remaining.put(event);
                }
            }
            preferences.edit().putString(EVENTS_KEY, remaining.toString()).commit();
        }
    }

    void retryNow() {
        synchronized (LOCK) {
            JSONArray events = read();
            for (int index = 0; index < events.length(); index++) {
                JSONObject event = events.optJSONObject(index);
                if (event != null) {
                    try {
                        event.put("nextAttemptAt", 0L);
                        event.put("processingStartedAt", 0L);
                    } catch (JSONException ignored) {
                        // A malformed entry will be skipped by itemAt().
                    }
                }
            }
            preferences.edit().putString(EVENTS_KEY, events.toString()).apply();
        }
    }

    int count() {
        synchronized (LOCK) {
            return read().length();
        }
    }

    String lastError() {
        synchronized (LOCK) {
            JSONArray events = read();
            for (int index = events.length() - 1; index >= 0; index--) {
                JSONObject event = events.optJSONObject(index);
                if (event != null && !event.optString("lastError").isEmpty()) {
                    return event.optString("lastError");
                }
            }
            return "";
        }
    }

    private Item itemAt(JSONArray events, int index) {
        JSONObject event = events.optJSONObject(index);
        if (event == null) return null;
        String id = event.optString("id");
        String packageName = event.optString("packageName");
        if (id.isEmpty() || packageName.isEmpty()) return null;
        return new Item(
                id,
                packageName,
                event.optString("title"),
                event.optString("body"),
                event.optLong("postTime", System.currentTimeMillis()),
                event.optString("appLabel", packageName),
                event.optInt("largeIconSimilarity", -1),
                event.optInt("smallIconSimilarity", -1),
                event.optString("iconVerdict", "等待重新分析"),
                event.optInt("retryCount", 0),
                event.optLong("nextAttemptAt", 0L),
                event.optLong("inProgressUntil", 0L),
                event.optLong("processingStartedAt", 0L));
    }

    private JSONArray read() {
        try {
            return new JSONArray(preferences.getString(EVENTS_KEY, "[]"));
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private static boolean contains(JSONArray events, String id) {
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.optJSONObject(index);
            if (event != null && id.equals(event.optString("id"))) return true;
        }
        return false;
    }

    private static String shortHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.US, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    enum AddResult {
        ADDED,
        DUPLICATE,
        FAILED
    }

    static final class Item {
        final String id;
        final String packageName;
        final String title;
        final String body;
        final long postTime;
        final NotificationIconFingerprint.Snapshot icon;
        final int retryCount;
        final long nextAttemptAt;
        final long inProgressUntil;
        final long processingStartedAt;

        Item(
                String id,
                String packageName,
                String title,
                String body,
                long postTime,
                String appLabel,
                int largeSimilarity,
                int smallSimilarity,
                String verdict,
                int retryCount,
                long nextAttemptAt,
                long inProgressUntil,
                long processingStartedAt) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.postTime = postTime;
            this.icon = new NotificationIconFingerprint.Snapshot(
                    appLabel, largeSimilarity, smallSimilarity, verdict);
            this.retryCount = retryCount;
            this.nextAttemptAt = nextAttemptAt;
            this.inProgressUntil = inProgressUntil;
            this.processingStartedAt = processingStartedAt;
        }
    }
}
