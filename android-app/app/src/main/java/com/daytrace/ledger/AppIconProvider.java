package com.daytrace.ledger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AppIconProvider {
    private static final int ICON_SIZE = 64;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private AppIconProvider() {}

    static String dataUri(Context context, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9._]+")) return "";
        String normalizedPackage = packageName.toLowerCase(Locale.ROOT);
        if ("com.tencent.mm".equals(normalizedPackage)
                || "com.eg.android.alipaygphone".equals(normalizedPackage)) {
            return "";
        }
        String cached = CACHE.get(normalizedPackage);
        if (cached != null) return cached;
        String result = "";
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
            drawable.draw(canvas);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            result = "data:image/png;base64,"
                    + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            bitmap.recycle();
        } catch (Exception ignored) {
            // Fall back to the category icon in the web interface.
        }
        CACHE.put(normalizedPackage, result);
        return result;
    }
}
