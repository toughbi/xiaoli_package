package com.daytrace.ledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationDiagnostics {
    private static final String PREFERENCES = "daytrace_notification_diagnostics";
    private static final String EVENTS_KEY = "recent_events";
    private static final int MAX_EVENTS = 500;
    private static final Object LOCK = new Object();

    private final SharedPreferences preferences;

    public NotificationDiagnostics(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public void record(
            String eventId,
            String packageName,
            String title,
            String body,
            boolean preliminaryPassed,
            boolean paymentCandidate,
            boolean aiQueued,
            boolean recognized,
            boolean containedAmount,
            String decisionBasis,
            String operation,
            String outcome,
            String processingStage,
            NotificationIconFingerprint.Snapshot icon,
            long capturedAt) {
        synchronized (LOCK) {
            JSONArray events = read();
            try {
                JSONObject event = findByEventId(events, eventId);
                if (event == null) {
                    event = new JSONObject();
                    events.put(event);
                }
                event.put("id", eventId);
                event.put("packageName", packageName == null ? "" : packageName);
                event.put("title", truncate(title, 240));
                event.put("body", truncate(body, 1200));
                event.put("preliminaryPassed", preliminaryPassed);
                event.put("sourceRelevant", preliminaryPassed);
                event.put("paymentCandidate", paymentCandidate);
                event.put("aiQueued", aiQueued);
                event.put("recognized", recognized);
                event.put("containedAmount", containedAmount);
                event.put("decisionBasis", truncate(decisionBasis, 400));
                event.put("semanticReason", truncate(decisionBasis, 400));
                event.put("operation", truncate(operation, 300));
                event.put("outcome", truncate(outcome, 300));
                event.put("processingStage", processingStage);
                if (icon != null) {
                    event.put("appLabel", truncate(icon.appLabel, 80));
                    event.put("icon", icon.toJson());
                }
                if (!event.has("capturedAt")) {
                    event.put(
                            "capturedAt",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                                    .format(new Date(capturedAt)));
                }
                event.put(
                        "updatedAt",
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                                .format(new Date()));
                preferences.edit()
                        .putString(EVENTS_KEY, trim(events).toString())
                        .apply();
            } catch (JSONException ignored) {
                // Diagnostics must never interrupt notification capture.
            }
        }
    }

    public static String eventId(
            String packageName,
            long postTime,
            String title,
            String body) {
        String raw = (packageName == null ? "" : packageName)
                + "|" + postTime
                + "|" + (title == null ? "" : title)
                + "|" + (body == null ? "" : body);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.US, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public String getAllJson() {
        synchronized (LOCK) {
            return read().toString();
        }
    }

    public void clearAll() {
        synchronized (LOCK) {
            preferences.edit().remove(EVENTS_KEY).apply();
        }
    }

    private static JSONObject findByEventId(JSONArray events, String eventId) {
        for (int index = events.length() - 1; index >= 0; index--) {
            JSONObject event = events.optJSONObject(index);
            if (event != null && eventId.equals(event.optString("id"))) return event;
        }
        return null;
    }

    private static JSONArray trim(JSONArray events) {
        JSONArray trimmed = new JSONArray();
        int start = Math.max(0, events.length() - MAX_EVENTS);
        for (int index = start; index < events.length(); index++) {
            trimmed.put(events.opt(index));
        }
        return trimmed;
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
