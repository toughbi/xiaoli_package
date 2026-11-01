package com.daytrace.ledger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int REQUEST_SMS_PERMISSION = 41;
    private static final int REQUEST_IMPORT_CSV = 42;
    private static final int REQUEST_RECORD_AUDIO = 44;
    private static final ExecutorService VOICE_EXECUTOR = Executors.newFixedThreadPool(2);

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private volatile CloudVoiceRecorder cloudVoiceRecorder;
    private boolean voiceHoldActive;
    private volatile boolean voiceResultDelivered;
    private volatile int activeVoiceRequestId;
    private final AtomicInteger voiceRequestSequence = new AtomicInteger();
    private int listenerReconnectAttempt;
    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            notifyWebApp();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getColor(R.color.paper));
        getWindow().setNavigationBarColor(getColor(R.color.paper));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        webView = new WebView(this);
        setContentView(webView);
        applyAndroid15WindowInsets();
        configureWebView();
        registerCaptureReceiver();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void applyAndroid15WindowInsets() {
        if (Build.VERSION.SDK_INT < 35) return;
        webView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            Insets cutout = windowInsets.getInsets(WindowInsets.Type.displayCutout());
            int top = Math.max(bars.top, cutout.top);
            int bottom = Math.max(bars.bottom, cutout.bottom);
            if (view.getPaddingTop() != top || view.getPaddingBottom() != bottom) {
                view.setPadding(0, top, 0, bottom);
            }
            return windowInsets;
        });
        webView.post(webView::requestApplyInsets);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(getColor(R.color.paper));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidLedger");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                return !("file".equals(scheme) || "about".equals(scheme) || "blob".equals(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                notifyWebApp();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "选择账单 CSV"), REQUEST_IMPORT_CSV);
                return true;
            }
        });
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);
    }

    private void registerCaptureReceiver() {
        IntentFilter filter = new IntentFilter(AutoCaptureStore.ACTION_TRANSACTION_CAPTURED);
        filter.addAction(PaymentNotificationService.ACTION_NOTIFICATION_LISTENER_STATE_CHANGED);
        registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void notifyWebApp() {
        if (webView == null) return;
        webView.evaluateJavascript(
                "window.onAndroidPermissionChanged && window.onAndroidPermissionChanged();", null);
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, PaymentNotificationService.class);
        return enabled.contains(component.flattenToString());
    }

    private boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private JSONObject permissionStatus() throws JSONException {
        JSONObject status = new JSONObject();
        status.put("notificationAccess", hasNotificationAccess());
        status.put(
                "listenerConnected",
                hasNotificationAccess() && PaymentNotificationService.isListenerConnected());
        PendingAiNotificationStore pendingAiStore =
                new PendingAiNotificationStore(this);
        status.put("pendingAnalysisCount", pendingAiStore.count());
        status.put("pendingAnalysisError", pendingAiStore.lastError());
        status.put("smsGranted", hasSmsPermission());
        return status;
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开通知读取设置，请在系统设置中搜索「通知使用权」", Toast.LENGTH_LONG).show();
        }
    }

    private void reconnectNotificationListener() {
        int attempt = ++listenerReconnectAttempt;
        PaymentNotificationService.reconnectNow(this);
        Toast.makeText(this, "正在重新连接通知监听", Toast.LENGTH_SHORT).show();
        if (webView == null) return;
        webView.postDelayed(() -> {
            if (attempt == listenerReconnectAttempt
                    && !PaymentNotificationService.isListenerConnected()) {
                PaymentNotificationService.requestListenerRebind(this);
            }
        }, 1_000);
        webView.postDelayed(() -> {
            if (attempt == listenerReconnectAttempt
                    && !PaymentNotificationService.isListenerConnected()) {
                PaymentNotificationService.requestListenerRebind(this);
            }
        }, 2_200);
        webView.postDelayed(() -> {
            if (attempt != listenerReconnectAttempt) return;
            if (PaymentNotificationService.isListenerConnected()) {
                Toast.makeText(this, "通知监听已重新连接", Toast.LENGTH_SHORT).show();
                notifyWebApp();
                return;
            }
            openNotificationSettings();
            Toast.makeText(
                    this,
                    "自动重连未成功，请在通知使用权页面关闭再打开「日迹账本」",
                    Toast.LENGTH_LONG).show();
        }, 3_500);
    }

    private void requestSmsPermission() {
        if (hasSmsPermission()) {
            Toast.makeText(this, "短信识别权限已开启", Toast.LENGTH_SHORT).show();
            notifyWebApp();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_SMS_PERMISSION);
    }

    private String saveCsv(String filename, String content) {
        String safeFilename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeFilename);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/日迹账本");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "无法创建导出文件";
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) return "无法写入导出文件";
                    output.write(content.getBytes(StandardCharsets.UTF_8));
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                return "已保存到下载/日迹账本";
            }

            File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "日迹账本");
            if (!directory.exists() && !directory.mkdirs()) return "无法创建导出目录";
            File file = new File(directory, safeFilename);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return "已导出到 " + file.getAbsolutePath();
        } catch (Exception error) {
            return "导出失败：" + error.getMessage();
        }
    }

    private void testAiConnection() {
        AiSettingsStore settingsStore = new AiSettingsStore(this);
        AiSettingsStore.Settings settings = settingsStore.load();
        JSONObject result = new JSONObject();
        if (!settings.canCallModel()) {
            try {
                result.put("ok", false);
                result.put("message", "请先启用 AI、确认隐私说明并保存 API Key");
            } catch (JSONException ignored) {
                // The fallback response below remains valid JSON.
            }
            sendAiTestResult(result);
            return;
        }
        AiWorkPool.submit(() -> {
            try {
                result.put("ok", true);
                result.put("message", DeepSeekFinancialAnalyst.test(settings));
            } catch (Exception error) {
                try {
                    result.put("ok", false);
                    result.put("message", error.getMessage() == null
                            ? "连接失败"
                            : error.getMessage());
                } catch (JSONException ignored) {
                    // The fallback response below remains valid JSON.
                }
            }
            sendAiTestResult(result);
        });
    }

    private void sendAiTestResult(JSONObject result) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.onAiTestResult && window.onAiTestResult("
                            + JSONObject.quote(result.toString()) + ");",
                    null);
        });
    }

    private void listDeepSeekModels() {
        AiSettingsStore.Settings settings = new AiSettingsStore(this).load();
        JSONObject result = new JSONObject();
        if (settings.apiKey.isEmpty()) {
            try {
                result.put("ok", false);
                result.put("message", "请先填写并保存 DeepSeek API Key");
            } catch (JSONException ignored) {
                // The fallback response below remains valid JSON.
            }
            sendDeepSeekModelsResult(result);
            return;
        }
        AiWorkPool.submit(() -> {
            try {
                JSONArray models = DeepSeekFinancialAnalyst.listModels(settings);
                result.put("ok", true);
                result.put("message", models.length() == 0
                        ? "接口没有返回可用模型"
                        : "已获取 " + models.length() + " 个可用模型");
                result.put("models", models);
            } catch (Exception error) {
                try {
                    result.put("ok", false);
                    result.put("message", error.getMessage() == null
                            ? "获取模型列表失败"
                            : error.getMessage());
                } catch (JSONException ignored) {
                    // The fallback response below remains valid JSON.
                }
            }
            sendDeepSeekModelsResult(result);
        });
    }

    private void sendDeepSeekModelsResult(JSONObject result) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.onDeepSeekModelsResult && window.onDeepSeekModelsResult("
                            + JSONObject.quote(result.toString()) + ");",
                    null);
        });
    }

    private void startVoiceRecognition() {
        CloudSpeechSettingsStore.Settings settings =
                new CloudSpeechSettingsStore(this).load();
        if (!settings.canRecognize()) {
            JSONObject result = new JSONObject();
            try {
                result.put("ok", false);
                result.put("message", "请先在数据页配置腾讯云语音识别");
                result.put("needsCloudSettings", true);
            } catch (JSONException ignored) {
                // The helper below still produces a valid UI response.
            }
            sendVoiceResult(result);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (voiceHoldActive) return;

        int requestId = voiceRequestSequence.incrementAndGet();
        activeVoiceRequestId = requestId;
        voiceHoldActive = true;
        voiceResultDelivered = false;
        CloudVoiceRecorder recorder = new CloudVoiceRecorder(this);
        cloudVoiceRecorder = recorder;
        sendVoicePartial("正在聆听 · 松开后由腾讯云识别");
        VOICE_EXECUTOR.execute(() -> transcribeCloudVoice(requestId, recorder, settings));
    }

    private void transcribeCloudVoice(
            int requestId,
            CloudVoiceRecorder recorder,
            CloudSpeechSettingsStore.Settings settings) {
        try {
            byte[] wav = recorder.recordUntilStopped();
            if (!isVoiceRequestActive(requestId)) return;
            sendVoicePartial("录音完成，正在通过腾讯云识别…");
            String text = TencentCloudSpeechRecognizer.recognize(wav, settings);
            completeVoiceRequest(requestId, true, "识别完成", text);
        } catch (Exception error) {
            String detail = error.getMessage() == null
                    ? "未知错误"
                    : error.getMessage();
            completeVoiceRequest(requestId, false, detail, "");
        } finally {
            if (cloudVoiceRecorder == recorder) cloudVoiceRecorder = null;
        }
    }

    private boolean isVoiceRequestActive(int requestId) {
        return activeVoiceRequestId == requestId && !voiceResultDelivered;
    }

    private void completeVoiceRequest(
            int requestId,
            boolean ok,
            String message,
            String text) {
        if (!isVoiceRequestActive(requestId)) return;
        voiceResultDelivered = true;
        activeVoiceRequestId = 0;
        voiceHoldActive = false;
        sendVoiceResult(ok, message, text);
    }

    private void cancelVoiceRecognition() {
        activeVoiceRequestId = 0;
        voiceHoldActive = false;
        voiceResultDelivered = true;
        CloudVoiceRecorder recorder = cloudVoiceRecorder;
        cloudVoiceRecorder = null;
        if (recorder != null) {
            recorder.cancel();
        }
    }

    private void sendVoiceResult(boolean ok, String message, String text) {
        JSONObject result = new JSONObject();
        try {
            result.put("ok", ok);
            result.put("message", message);
            result.put("text", text);
        } catch (JSONException ignored) {
            return;
        }
        sendVoiceResult(result);
    }

    private void sendVoiceResult(JSONObject result) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.onVoiceResult && window.onVoiceResult("
                            + JSONObject.quote(result.toString()) + ");",
                    null);
        });
    }

    private void sendVoicePartial(String text) {
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.onVoicePartial && window.onVoicePartial("
                            + JSONObject.quote(text) + ");",
                    null);
        });
    }

    private void stopVoiceRecognition() {
        if (!voiceHoldActive) return;
        voiceHoldActive = false;
        CloudVoiceRecorder recorder = cloudVoiceRecorder;
        if (recorder != null) {
            recorder.stop();
        }
        sendVoicePartial("录音完成，正在通过腾讯云识别…");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(
                    this,
                    granted ? "银行短信自动识别已开启" : "未获得短信权限，可稍后在数据管理中开启",
                    Toast.LENGTH_SHORT).show();
            notifyWebApp();
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                sendVoiceResult(false, "麦克风权限已开启，请再次按住按钮说话", "");
            } else {
                sendVoiceResult(false, "未获得麦克风权限，请使用手动记账", "");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_CSV && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenerReconnectAttempt++;
        if (hasNotificationAccess() && !PaymentNotificationService.isListenerConnected()) {
            PaymentNotificationService.requestListenerRebind(this);
        }
        PaymentNotificationService.wakePendingAnalysis(this);
        notifyWebApp();
    }

    @Override
    protected void onDestroy() {
        cancelVoiceRecognition();
        try {
            unregisterReceiver(captureReceiver);
        } catch (IllegalArgumentException ignored) {
            // The receiver may already have been detached during activity recreation.
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidLedger");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){var d=document.querySelector('dialog[open]');"
                        + "if(d){d.close();return 'dialog';}return 'none';})()",
                value -> {
                    if (value != null && value.contains("dialog")) {
                        notifyWebApp();
                    } else {
                        MainActivity.super.onBackPressed();
                    }
                });
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public String getPermissionStatus() {
            try {
                return permissionStatus().toString();
            } catch (JSONException error) {
                return "{\"notificationAccess\":false,\"listenerConnected\":false,"
                        + "\"pendingAnalysisCount\":0,\"pendingAnalysisError\":\"\","
                        + "\"smsGranted\":false}";
            }
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(MainActivity.this::openNotificationSettings);
        }

        @JavascriptInterface
        public void reconnectNotificationListener() {
            runOnUiThread(MainActivity.this::reconnectNotificationListener);
        }

        @JavascriptInterface
        public String recheckNotificationPipeline() {
            String status;
            try {
                status = permissionStatus().toString();
            } catch (JSONException error) {
                status = "{\"notificationAccess\":false,\"listenerConnected\":false,"
                        + "\"pendingAnalysisCount\":0,\"pendingAnalysisError\":\"\","
                        + "\"smsGranted\":false}";
            }
            runOnUiThread(() -> {
                if (hasNotificationAccess() && !PaymentNotificationService.isListenerConnected()) {
                    PaymentNotificationService.requestListenerRebind(MainActivity.this);
                }
                PaymentNotificationService.wakePendingAnalysis(MainActivity.this);
                if (webView != null) {
                    webView.postDelayed(MainActivity.this::notifyWebApp, 1_200);
                }
            });
            return status;
        }

        @JavascriptInterface
        public void requestSmsPermission() {
            runOnUiThread(MainActivity.this::requestSmsPermission);
        }

        @JavascriptInterface
        public String getCapturedTransactions() {
            return new AutoCaptureStore(MainActivity.this).getAllJson();
        }

        @JavascriptInterface
        public void clearCapturedTransactions() {
            new AutoCaptureStore(MainActivity.this).clearAll();
        }

        @JavascriptInterface
        public String getNotificationDiagnostics() {
            return new NotificationDiagnostics(MainActivity.this).getAllJson();
        }

        @JavascriptInterface
        public void clearNotificationDiagnostics() {
            new NotificationDiagnostics(MainActivity.this).clearAll();
        }

        @JavascriptInterface
        public String getAiSettings() {
            return new AiSettingsStore(MainActivity.this).toUiJson().toString();
        }

        @JavascriptInterface
        public String saveAiSettings(String payload) {
            String result = new AiSettingsStore(MainActivity.this)
                    .saveFromJson(payload)
                    .toString();
            PaymentNotificationService.wakePendingAnalysis(MainActivity.this);
            return result;
        }

        @JavascriptInterface
        public String getCloudSpeechSettings() {
            return new CloudSpeechSettingsStore(MainActivity.this).toUiJson().toString();
        }

        @JavascriptInterface
        public String saveCloudSpeechSettings(String payload) {
            return new CloudSpeechSettingsStore(MainActivity.this)
                    .saveFromJson(payload)
                    .toString();
        }

        @JavascriptInterface
        public void testAiConnection() {
            MainActivity.this.testAiConnection();
        }

        @JavascriptInterface
        public void listDeepSeekModels() {
            MainActivity.this.listDeepSeekModels();
        }

        @JavascriptInterface
        public void startVoiceRecognition() {
            runOnUiThread(MainActivity.this::startVoiceRecognition);
        }

        @JavascriptInterface
        public void stopVoiceRecognition() {
            runOnUiThread(MainActivity.this::stopVoiceRecognition);
        }

        @JavascriptInterface
        public void cancelVoiceRecognition() {
            runOnUiThread(MainActivity.this::cancelVoiceRecognition);
        }

        @JavascriptInterface
        public String saveCsv(String filename, String content) {
            return MainActivity.this.saveCsv(filename, content);
        }

        @JavascriptInterface
        public String getAppIcon(String packageName) {
            return AppIconProvider.dataUri(MainActivity.this, packageName);
        }
    }
}
