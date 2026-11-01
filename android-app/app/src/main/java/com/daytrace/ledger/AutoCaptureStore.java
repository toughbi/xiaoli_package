package com.daytrace.ledger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AutoCaptureStore {
    public static final String ACTION_TRANSACTION_CAPTURED =
            "com.daytrace.ledger.ACTION_TRANSACTION_CAPTURED";

    private static final String PREFERENCES = "daytrace_auto_capture";
    private static final String TRANSACTIONS_KEY = "captured_transactions";
    private static final int MAX_TRANSACTIONS = 500;
    private static final long SIMILAR_WINDOW_MS = 15_000;

    private final Context context;
    private final SharedPreferences preferences;

    public AutoCaptureStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized boolean capture(
            String sourceKey,
            TransactionParser.Result parsed,
            String rawText,
            long capturedAt) {
        JSONArray existing = readArray();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(new Date(capturedAt));
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(capturedAt));
        String id = shortHash(sourceKey + "|" + rawText + "|" + (capturedAt / 30_000));

        for (int index = 0; index < existing.length(); index++) {
            JSONObject item = existing.optJSONObject(index);
            if (item == null) continue;
            if (id.equals(item.optString("id"))) return false;
            if (isSimilar(item, parsed, sourceKey, capturedAt)) return false;
        }

        try {
            JSONObject transaction = new JSONObject();
            transaction.put("id", "auto-" + id);
            transaction.put("type", parsed.type);
            transaction.put("amount", parsed.amount);
            transaction.put("date", date);
            transaction.put("channel", parsed.channel);
            transaction.put("category", parsed.category);
            transaction.put("note", parsed.note);
            transaction.put("createdAt", timestamp);
            transaction.put("autoCaptured", true);
            transaction.put("sourceLabel", parsed.sourceLabel);
            transaction.put("sourceKey", sourceKey);
            transaction.put(
                    "sourcePackage",
                    sourceKey != null && !sourceKey.startsWith("sms:") ? sourceKey : "");
            transaction.put("confidence", parsed.confidence);
            transaction.put("explanation", parsed.explanation);
            existing.put(transaction);

            JSONArray trimmed = new JSONArray();
            int start = Math.max(0, existing.length() - MAX_TRANSACTIONS);
            for (int index = start; index < existing.length(); index++) {
                trimmed.put(existing.get(index));
            }
            preferences.edit().putString(TRANSACTIONS_KEY, trimmed.toString()).apply();
        } catch (JSONException error) {
            return false;
        }

        Intent changed = new Intent(ACTION_TRANSACTION_CAPTURED).setPackage(context.getPackageName());
        context.sendBroadcast(changed);
        return true;
    }

    public String getAllJson() {
        return readArray().toString();
    }

    public int count() {
        return readArray().length();
    }

    public synchronized void clearAll() {
        preferences.edit().remove(TRANSACTIONS_KEY).apply();
        Intent changed = new Intent(ACTION_TRANSACTION_CAPTURED).setPackage(context.getPackageName());
        context.sendBroadcast(changed);
    }

    private JSONArray readArray() {
        String raw = preferences.getString(TRANSACTIONS_KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private boolean isSimilar(
            JSONObject item,
            TransactionParser.Result parsed,
            String sourceKey,
            long capturedAt) {
        if (!parsed.type.equals(item.optString("type"))) return false;
        if (Math.abs(parsed.amount - item.optDouble("amount", -1)) > 0.001) return false;
        try {
            long previous = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                    .parse(item.optString("createdAt"))
                    .getTime();
            boolean sameSource = sourceKey.equals(item.optString("sourceKey"));
            long window = sameSource ? SIMILAR_WINDOW_MS : SIMILAR_WINDOW_MS * 2;
            return Math.abs(capturedAt - previous) <= window;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.US, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

}
