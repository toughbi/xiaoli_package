package com.daytrace.ledger;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import org.json.JSONException;
import org.json.JSONObject;

final class NotificationIconFingerprint {
    private static final int HASH_WIDTH = 9;
    private static final int HASH_HEIGHT = 8;

    private NotificationIconFingerprint() {}

    static final class Snapshot {
        final String appLabel;
        final int largeSimilarity;
        final int smallSimilarity;
        final String verdict;

        Snapshot(String appLabel, int largeSimilarity, int smallSimilarity, String verdict) {
            this.appLabel = appLabel;
            this.largeSimilarity = largeSimilarity;
            this.smallSimilarity = smallSimilarity;
            this.verdict = verdict;
        }

        JSONObject toJson() throws JSONException {
            JSONObject value = new JSONObject();
            value.put("appLabel", appLabel);
            value.put("largeIconSimilarity", largeSimilarity);
            value.put("smallIconSimilarity", smallSimilarity);
            value.put("iconVerdict", verdict);
            return value;
        }
    }

    static Snapshot inspect(Context context, String packageName, Notification notification) {
        PackageManager manager = context.getPackageManager();
        String appLabel = packageName;
        long appHash = 0;
        try {
            CharSequence label = manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0));
            if (label != null && label.length() > 0) appLabel = label.toString();
            Drawable appIcon = manager.getApplicationIcon(packageName);
            appHash = hashOf(drawableToBitmap(appIcon, Color.WHITE));
        } catch (Exception ignored) {
            // The package can disappear during an uninstall; keep the package name.
        }

        long largeHash = notification.getLargeIcon() == null
                ? 0
                : hashOf(iconToBitmap(context, notification.getLargeIcon(), Color.WHITE));
        long smallHash = notification.getSmallIcon() == null
                ? 0
                : hashOf(iconToBitmap(context, notification.getSmallIcon(), Color.BLACK));
        int largeSimilarity = similarity(appHash, largeHash);
        int smallSimilarity = similarity(appHash, smallHash);
        String verdict;
        int best = Math.max(largeSimilarity, smallSimilarity);
        if (best >= 78) verdict = "图标高度相似";
        else if (best >= 60) verdict = "图标部分相似";
        else if (largeSimilarity < 0 && smallSimilarity < 0) verdict = "通知未提供可比较图标";
        else verdict = "小图标可能为单色，包名作为主判断";
        return new Snapshot(appLabel, largeSimilarity, smallSimilarity, verdict);
    }

    private static Bitmap iconToBitmap(Context context, Icon icon, int background) {
        try {
            Drawable drawable = icon.loadDrawable(context);
            return drawableToBitmap(drawable, background);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int background) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(HASH_WIDTH, HASH_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(background);
        drawable.setBounds(0, 0, HASH_WIDTH, HASH_HEIGHT);
        drawable.draw(canvas);
        return bitmap;
    }

    private static long hashOf(Bitmap bitmap) {
        if (bitmap == null) return 0;
        int[] pixels = new int[HASH_WIDTH * HASH_HEIGHT];
        bitmap.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT);
        long hash = 0;
        int bit = 0;
        for (int y = 0; y < HASH_HEIGHT; y++) {
            for (int x = 0; x < HASH_WIDTH - 1; x++) {
                int left = pixels[y * HASH_WIDTH + x];
                int right = pixels[y * HASH_WIDTH + x + 1];
                if (luminance(left) > luminance(right)) hash |= 1L << bit;
                bit++;
            }
        }
        return hash;
    }

    private static int luminance(int color) {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
    }

    private static int similarity(long left, long right) {
        if (left == 0 || right == 0) return -1;
        return 100 - Long.bitCount(left ^ right) * 100 / 64;
    }
}
