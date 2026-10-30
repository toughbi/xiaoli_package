package com.daytrace.ledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationDiagnostics {
    private static final String PREFERENCES = "daytrace_notification_diagnostics";
    private static final String EVENTS_KEY = "recent_events";
    private static final int MAX_EVENTS = 10;

    private final SharedPreferences preferences;

    public NotificationDiagnostics(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized void record(
            String packageName,
            String title,
            String body,
            boolean recognized,
            boolean containedAmount,
            String semanticReason,
            NotificationIconFingerprint.Snapshot icon,
            long capturedAt) {
        JSONArray events = read();
        try {
            JSONObject event = new JSONObject();
            event.put("packageName", packageName);
            event.put("title", truncate(title, 160));
            event.put("body", truncate(body, 400));
            event.put("recognized", recognized);
            event.put("containedAmount", containedAmount);
            event.put("semanticReason", truncate(semanticReason, 240));
            if (icon != null) {
                event.put("appLabel", truncate(icon.appLabel, 60));
                event.put("icon", icon.toJson());
            }
            event.put(
                    "capturedAt",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                            .format(new Date(capturedAt)));
            events.put(event);

            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, events.length() - MAX_EVENTS);
            for (int index = start; index < events.length(); index++) {
                trimmed.put(events.get(index));
            }
            preferences.edit().putString(EVENTS_KEY, trimmed.toString()).apply();
        } catch (JSONException ignored) {
            // Diagnostics must never interrupt transaction capture.
        }
    }

    public String getAllJson() {
        return read().toString();
    }

    public synchronized void clearAll() {
        preferences.edit().remove(EVENTS_KEY).apply();
    }

    private JSONArray read() {
        try {
            return new JSONArray(preferences.getString(EVENTS_KEY, "[]"));
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
