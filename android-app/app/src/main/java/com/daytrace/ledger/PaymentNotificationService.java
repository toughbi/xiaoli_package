package com.daytrace.ledger;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PaymentNotificationService extends NotificationListenerService {
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(2);

    private AutoCaptureStore store;
    private NotificationDiagnostics diagnostics;
    private AiSettingsStore aiSettingsStore;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new AutoCaptureStore(this);
        diagnostics = new NotificationDiagnostics(this);
        aiSettingsStore = new AiSettingsStore(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null || notification.getNotification() == null) return;
        String packageName = notification.getPackageName();
        if (!TransactionParser.isRelevantPackage(packageName)) return;

        Notification source = notification.getNotification();
        Bundle extras = source.extras;
        if (extras == null) return;

        String title = asText(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = asText(extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = asText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String subText = asText(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String summaryText = asText(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        StringBuilder body = new StringBuilder();
        if (!text.isEmpty()) body.append(text);
        if (!bigText.isEmpty() && !bigText.equals(text)) body.append(' ').append(bigText);
        if (!subText.isEmpty()) body.append(' ').append(subText);
        if (!summaryText.isEmpty() && !summaryText.equals(text)) body.append(' ').append(summaryText);
        if (lines != null) {
            for (CharSequence line : lines) {
                if (line != null) body.append(' ').append(line);
            }
        }

        String bodyText = body.toString().trim();
        NotificationIconFingerprint.Snapshot icon = NotificationIconFingerprint.inspect(
                this, packageName, source);
        final TransactionParser.Result localResult = TransactionParser.parse(
                packageName,
                TransactionParser.sourceLabelForPackage(packageName),
                title,
                bodyText,
                notification.getPostTime());
        final boolean containedAmount = TransactionParser.containsAmount(title, bodyText);
        final String localReason = AdvertisementDetector.isAdvertisement(title + " " + bodyText)
                ? AdvertisementDetector.reason(title + " " + bodyText)
                : localResult == null
                        ? "未通过本地语义识别"
                        : localResult.explanation;
        if (diagnostics == null) diagnostics = new NotificationDiagnostics(this);
        if (store == null) store = new AutoCaptureStore(this);
        if (aiSettingsStore == null) aiSettingsStore = new AiSettingsStore(this);
        AiSettingsStore.Settings aiSettings = aiSettingsStore.load();
        boolean useAi = aiSettings.canCallModel()
                && !AiSettingsStore.MODE_LOCAL.equals(aiSettings.mode);
        final String finalTitle = title;
        final String finalBody = bodyText;
        final long postTime = notification.getPostTime();
        if (useAi) {
            AI_EXECUTOR.execute(() -> analyzeWithAi(
                    aiSettings,
                    packageName,
                    finalTitle,
                    finalBody,
                    localResult,
                    containedAmount,
                    icon,
                    postTime));
        } else {
            recordAndCapture(
                    packageName,
                    finalTitle,
                    finalBody,
                    localResult,
                    containedAmount,
                    icon,
                    postTime,
                    localReason);
        }
    }

    private void analyzeWithAi(
            AiSettingsStore.Settings settings,
            String packageName,
            String title,
            String body,
            TransactionParser.Result localResult,
            boolean containedAmount,
            NotificationIconFingerprint.Snapshot icon,
            long postTime) {
        String appLabel = icon == null ? packageName : icon.appLabel;
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                DeepSeekFinancialAnalyst.Decision decision = DeepSeekFinancialAnalyst.analyze(
                        settings, packageName, appLabel, title, body);
                if (decision.transaction && AdvertisementDetector.isAdvertisement(title + " " + body)) {
                    lastError = new IllegalStateException("模型将广告或营销金额判定为交易");
                    continue;
                }
                if (!decision.transaction) {
                    diagnostics.record(
                            packageName,
                            title,
                            body,
                            false,
                            containedAmount,
                            "DeepSeek 财务专家第 " + attempt
                                    + " 次判定为非交易：" + decision.reason,
                            icon,
                            postTime);
                    return;
                }
                TransactionParser.Result aiResult = decision.toResult(packageName, title, body);
                recordAndCapture(
                        packageName,
                        title,
                        body,
                        aiResult,
                        containedAmount,
                        icon,
                        postTime,
                        aiResult.explanation);
                return;
            } catch (Exception error) {
                lastError = error;
            }
        }
        String failure = lastError == null || lastError.getMessage() == null
                ? "未返回可靠结果"
                : lastError.getMessage();
        String localReason = AdvertisementDetector.isAdvertisement(title + " " + body)
                ? AdvertisementDetector.reason(title + " " + body)
                : localResult == null ? "本地规则未发现真实交易" : localResult.explanation;
        String reason = "DeepSeek 连续 2 次未返回可靠结果，已回退本地规则："
                + localReason + "（AI 错误：" + failure + "）";
        recordAndCapture(
                packageName,
                title,
                body,
                localResult,
                containedAmount,
                icon,
                postTime,
                reason);
    }

    private void recordAndCapture(
            String packageName,
            String title,
            String body,
            TransactionParser.Result result,
            boolean containedAmount,
            NotificationIconFingerprint.Snapshot icon,
            long postTime,
            String reason) {
        diagnostics.record(
                packageName,
                title,
                body,
                result != null,
                containedAmount,
                result != null
                        ? reason
                        : containedAmount ? reason : "通知中没有可识别的交易金额",
                icon,
                postTime);
        if (result != null) {
            store.capture(packageName, result, title + "|" + body, postTime);
        }
    }

    private static String asText(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
