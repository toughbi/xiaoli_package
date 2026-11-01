package com.daytrace.ledger;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PaymentNotificationService extends NotificationListenerService {
    public static final String ACTION_NOTIFICATION_LISTENER_STATE_CHANGED =
            "com.daytrace.ledger.ACTION_NOTIFICATION_LISTENER_STATE_CHANGED";
    private static final int MAX_RECONNECT_ATTEMPTS = 6;
    private static final int MAX_AI_WORKERS = AiWorkPool.MAX_WORKERS;
    private static final long AI_TASK_TIMEOUT_MS =
            PendingAiNotificationStore.AI_TASK_TIMEOUT_MS;
    private static final AtomicInteger AI_WORKERS = new AtomicInteger();
    private static final ScheduledExecutorService AI_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> new Thread(runnable, "daytrace-ai-timeout"));
    private static volatile boolean listenerConnected;
    private static volatile PaymentNotificationService instance;

    private AutoCaptureStore store;
    private NotificationDiagnostics diagnostics;
    private AiSettingsStore aiSettingsStore;
    private PendingAiNotificationStore pendingAiStore;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Handler aiRetryHandler = new Handler(Looper.getMainLooper());
    private final Runnable aiDrainRunnable = this::runQueuedAiDrain;
    private int reconnectAttempts;
    private boolean reconnectScheduled;
    private boolean aiDrainQueued;
    private long aiDrainScheduledAt = Long.MAX_VALUE;
    private volatile boolean active;

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    public static void requestListenerRebind(Context context) {
        try {
            requestRebind(new ComponentName(
                    context.getApplicationContext(),
                    PaymentNotificationService.class));
        } catch (SecurityException ignored) {
            // The system settings page is the only recovery path after access is revoked.
        } catch (RuntimeException ignored) {
            // A later activity resume or listener disconnect callback will retry.
        }
    }

    public static void reconnectNow(Context context) {
        PaymentNotificationService service = instance;
        if (service != null) {
            service.forceListenerReconnect();
        } else {
            requestListenerRebind(context);
        }
    }

    public static void wakePendingAnalysis(Context context) {
        PaymentNotificationService service = instance;
        if (service != null) {
            service.retryPendingNow();
        } else {
            requestListenerRebind(context);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        instance = this;
        listenerConnected = false;
        store = new AutoCaptureStore(this);
        diagnostics = new NotificationDiagnostics(this);
        aiSettingsStore = new AiSettingsStore(this);
        pendingAiStore = new PendingAiNotificationStore(this);
        scheduleAiDrain(0);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        listenerConnected = true;
        reconnectAttempts = 0;
        reconnectScheduled = false;
        reconnectHandler.removeCallbacksAndMessages(null);
        notifyListenerStateChanged();
        scheduleAiDrain(0);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        listenerConnected = false;
        reconnectAttempts = 0;
        notifyListenerStateChanged();
        scheduleReconnect();
    }

    @Override
    public void onDestroy() {
        active = false;
        listenerConnected = false;
        reconnectHandler.removeCallbacksAndMessages(null);
        aiRetryHandler.removeCallbacks(aiDrainRunnable);
        if (instance == this) instance = null;
        notifyListenerStateChanged();
        super.onDestroy();
    }

    private void scheduleReconnect() {
        if (listenerConnected
                || reconnectScheduled
                || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            return;
        }
        reconnectScheduled = true;
        long delayMs = Math.min(30_000L, 1_000L << reconnectAttempts);
        reconnectAttempts++;
        reconnectHandler.postDelayed(() -> {
            reconnectScheduled = false;
            if (listenerConnected) return;
            requestListenerRebind(this);
            scheduleReconnect();
        }, delayMs);
    }

    private void forceListenerReconnect() {
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectAttempts = 0;
        reconnectScheduled = false;
        listenerConnected = false;
        notifyListenerStateChanged();
        try {
            requestUnbind();
        } catch (RuntimeException ignored) {
            // Rebind below is still useful when the service has no active binding.
        }
        reconnectHandler.postDelayed(() -> {
            if (listenerConnected) return;
            requestListenerRebind(this);
            scheduleReconnect();
        }, 500);
    }

    private void notifyListenerStateChanged() {
        Intent changed = new Intent(ACTION_NOTIFICATION_LISTENER_STATE_CHANGED)
                .setPackage(getPackageName());
        sendBroadcast(changed);
    }

    private void retryPendingNow() {
        pendingAiStore.retryNow();
        scheduleAiDrain(0);
    }

    private synchronized void scheduleAiDrain(long delayMs) {
        if (!active || pendingAiStore == null || pendingAiStore.count() == 0) return;
        long targetAt = System.currentTimeMillis() + Math.max(0, delayMs);
        if (aiDrainQueued && aiDrainScheduledAt <= targetAt) return;
        if (aiDrainQueued) aiRetryHandler.removeCallbacks(aiDrainRunnable);
        aiDrainQueued = true;
        aiDrainScheduledAt = targetAt;
        aiRetryHandler.postDelayed(aiDrainRunnable, Math.max(0, delayMs));
    }

    private synchronized void runQueuedAiDrain() {
        aiDrainQueued = false;
        aiDrainScheduledAt = Long.MAX_VALUE;
        if (active) pumpAiWorkers();
    }

    private void pumpAiWorkers() {
        if (!active) return;
        while (AI_WORKERS.get() < MAX_AI_WORKERS) {
            PendingAiNotificationStore.Item item =
                    pendingAiStore.claimNextReady(System.currentTimeMillis());
            if (item == null) break;
            AI_WORKERS.incrementAndGet();
            AiWorkPool.submit(() -> {
                try {
                    processPendingCandidate(item);
                } catch (RuntimeException error) {
                    markPendingFailure(item, error.getMessage() == null
                            ? "AI 工作线程异常"
                            : error.getMessage());
                } finally {
                    AI_WORKERS.decrementAndGet();
                    scheduleAiDrain(0);
                }
            });
        }
        scheduleNextAiDrain();
    }

    private void scheduleNextAiDrain() {
        if (!active
                || AI_WORKERS.get() >= MAX_AI_WORKERS
                || pendingAiStore.count() == 0) {
            return;
        }
        long nextAvailableAt = pendingAiStore.nextAvailableAt();
        if (nextAvailableAt < 0) return;
        scheduleAiDrain(Math.max(0, nextAvailableAt - System.currentTimeMillis()));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null || notification.getNotification() == null) return;
        String packageName = notification.getPackageName();
        Notification source = notification.getNotification();
        Bundle extras = source.extras;
        String title = "";
        String text = "";
        String bigText = "";
        String subText = "";
        String summaryText = "";
        CharSequence[] lines = null;
        if (extras != null) {
            title = asText(extras.getCharSequence(Notification.EXTRA_TITLE));
            text = asText(extras.getCharSequence(Notification.EXTRA_TEXT));
            bigText = asText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            subText = asText(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            summaryText = asText(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        }
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
        long postTime = notification.getPostTime();
        String eventId = NotificationDiagnostics.eventId(
                packageName, postTime, title, bodyText);
        NotificationIconFingerprint.Snapshot icon = NotificationIconFingerprint.inspect(
                this, packageName, source);
        boolean containedAmount = TransactionParser.containsAmount(title, bodyText);
        boolean containsNumber = TransactionParser.containsNumber(bodyText);
        boolean advertisement = AdvertisementDetector.isAdvertisement(title + " " + bodyText);
        boolean preliminaryCandidate = containsNumber && !advertisement;
        if (diagnostics == null) diagnostics = new NotificationDiagnostics(this);
        if (store == null) store = new AutoCaptureStore(this);
        if (aiSettingsStore == null) aiSettingsStore = new AiSettingsStore(this);

        if (!preliminaryCandidate) {
            String basis;
            if (advertisement && !containsNumber) {
                basis = "本地初筛识别为明显广告，并且通知正文没有数字。";
            } else if (advertisement) {
                basis = AdvertisementDetector.reason(title + " " + bodyText);
            } else {
                basis = "本地初筛发现通知正文没有数字；标题不参与该判断。";
            }
            diagnostics.record(
                    eventId,
                    packageName,
                    title,
                    bodyText,
                    false,
                    false,
                    false,
                    false,
                    containedAmount,
                    basis,
                    "执行本地初步筛选：明显广告和完全无数字的通知不交给 AI。",
                    "初筛未通过，不进入 AI，不生成账目。",
                    "PRELIMINARY_REJECTED",
                    icon,
                    postTime);
            return;
        }

        PendingAiNotificationStore.AddResult addResult = pendingAiStore.add(
                packageName,
                title,
                bodyText,
                postTime,
                icon,
                eventId);
        if (addResult == PendingAiNotificationStore.AddResult.ADDED) {
            diagnostics.record(
                    eventId,
                    packageName,
                    title,
                    bodyText,
                    true,
                    true,
                    true,
                    false,
                    containedAmount,
                    "本地初筛通过：不是明显广告，并且通知包含数字。",
                    "通知已持久化，并分配给最多 3 个 AI 工人中的空闲工人。",
                    "等待 DeepSeek 判断是否属于已完成交易；AI 未返回前不会生成账目。",
                    "AI_QUEUED",
                    icon,
                    postTime);
            scheduleAiDrain(0);
        } else if (addResult == PendingAiNotificationStore.AddResult.FAILED) {
            PendingAiNotificationStore.Item transientItem =
                    new PendingAiNotificationStore.Item(
                            eventId,
                            packageName,
                            title,
                            bodyText,
                            postTime,
                            icon == null ? packageName : icon.appLabel,
                            icon == null ? -1 : icon.largeSimilarity,
                            icon == null ? -1 : icon.smallSimilarity,
                            icon == null ? "等待准确识别" : icon.verdict,
                            0,
                            0,
                            0,
                            0);
            diagnostics.record(
                    eventId,
                    packageName,
                    title,
                    bodyText,
                    true,
                    true,
                    false,
                    false,
                    containedAmount,
                    "本地初筛通过：不是明显广告，并且通知包含数字。",
                    "持久队列写入失败，直接提交 AI 工作池。",
                    "等待 DeepSeek 判断；本次通知无法跨进程重启恢复。",
                    "AI_DIRECT",
                    icon,
                    postTime);
            AiWorkPool.submit(() -> processPendingCandidate(transientItem));
        }
    }

    private boolean processPendingCandidate(PendingAiNotificationStore.Item item) {
        AiSettingsStore.Settings settings = aiSettingsStore.load();
        if (!settings.canCallModel()) {
            markPendingFailure(item, "AI 未启用或配置不完整，通知已保留并等待 AI 配置");
            return false;
        }

        long processingStartedAt = item.processingStartedAt > 0
                ? item.processingStartedAt
                : System.currentTimeMillis();
        long deadlineAt = processingStartedAt + AI_TASK_TIMEOUT_MS;
        if (System.currentTimeMillis() >= deadlineAt) {
            markPendingFailure(item, "本轮 AI 超过 5 分钟，未生成账目，保留通知稍后重试");
            return false;
        }

        String appLabel = item.icon == null ? item.packageName : item.icon.appLabel;
        Exception lastError = null;
        AtomicBoolean finished = new AtomicBoolean();
        DeepSeekFinancialAnalyst.Cancellation cancellation =
                new DeepSeekFinancialAnalyst.Cancellation();
        Thread workerThread = Thread.currentThread();
        ScheduledFuture<?> timeout = AI_TIMEOUT_EXECUTOR.schedule(() -> {
            if (finished.compareAndSet(false, true)) {
                cancellation.cancel();
                workerThread.interrupt();
                markPendingFailure(item, "本轮 AI 超过 5 分钟，未生成账目，保留通知稍后重试");
            }
        }, Math.max(0, deadlineAt - System.currentTimeMillis()), TimeUnit.MILLISECONDS);

        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                if (System.currentTimeMillis() >= deadlineAt) break;
                try {
                    DeepSeekFinancialAnalyst.Decision decision =
                            DeepSeekFinancialAnalyst.analyze(
                                    settings,
                                    item.packageName,
                                    appLabel,
                                    item.title,
                                    item.body,
                                    deadlineAt,
                                    cancellation);
                    if (finished.get()) return true;
                    if (!decision.transaction) {
                        if (!finished.compareAndSet(false, true)) return true;
                        timeout.cancel(false);
                        diagnostics.record(
                                item.id,
                                item.packageName,
                                item.title,
                                item.body,
                                true,
                                true,
                                true,
                                false,
                                TransactionParser.containsAmount(item.title, item.body),
                                "DeepSeek 财务专家第 " + attempt
                                        + " 次判定为非交易：" + decision.reason,
                                "调用 DeepSeek 语义模型判断通知是否代表已完成交易。",
                                "模型判定为非交易，没有生成账目；通知已移出 AI 队列。",
                                "AI_COMPLETED",
                                item.icon,
                                item.postTime);
                        removePending(item);
                        return true;
                    }

                    TransactionParser.Result aiResult = decision.toResult(
                            item.packageName, item.title, item.body);
                    if (!finished.compareAndSet(false, true)) return true;
                    timeout.cancel(false);
                    recordAndCapture(
                            item.id,
                            item.packageName,
                            item.title,
                            item.body,
                            aiResult,
                            true,
                            item.icon,
                            item.postTime,
                            true,
                            true,
                            aiResult.explanation,
                            "调用 DeepSeek 并在返回后校验收支类型、金额和原文一致性。",
                            "已生成" + directionLabel(aiResult) + "账目并移出 AI 队列。",
                            "AI_COMPLETED");
                    removePending(item);
                    return true;
                } catch (Exception error) {
                    if (finished.get()) return true;
                    lastError = error;
                }
            }
        } finally {
            timeout.cancel(false);
            cancellation.cancel();
        }

        if (finished.get()) return true;
        if (System.currentTimeMillis() >= deadlineAt) {
            markPendingFailure(item, "本轮 AI 超过 5 分钟，未生成账目，保留通知稍后重试");
            return false;
        }

        String failure = lastError == null || lastError.getMessage() == null
                ? "未返回可靠结果"
                : lastError.getMessage();
        markPendingFailure(item, failure);
        return false;
    }

    private void removePending(PendingAiNotificationStore.Item item) {
        if (item.id != null) pendingAiStore.remove(item.id);
    }

    private void markPendingFailure(
            PendingAiNotificationStore.Item item,
            String failure) {
        if (item.id != null) {
            pendingAiStore.markFailure(item.id, failure);
            diagnostics.record(
                    item.id,
                    item.packageName,
                    item.title,
                    item.body,
                    true,
                    true,
                    true,
                    false,
                    TransactionParser.containsAmount(item.title, item.body),
                    "本次 AI 调用失败：" + failure,
                    "通知仍保留在持久化 AI 队列，没有降级为本地规则。",
                    "本次未生成账目；将按退避时间自动重试。",
                    "AI_RETRY",
                    item.icon,
                    item.postTime);
            return;
        }
        PendingAiNotificationStore.AddResult retryAdd = pendingAiStore.add(
                item.packageName,
                item.title,
                item.body,
                item.postTime,
                item.icon);
        if (retryAdd == PendingAiNotificationStore.AddResult.ADDED) scheduleAiDrain(0);
    }

    private void recordAndCapture(
            String eventId,
            String packageName,
            String title,
            String body,
            TransactionParser.Result result,
            boolean containedAmount,
            NotificationIconFingerprint.Snapshot icon,
            long postTime,
            boolean paymentCandidate,
            boolean aiQueued,
            String decisionBasis,
            String operation,
            String outcome,
            String processingStage) {
        diagnostics.record(
                eventId,
                packageName,
                title,
                body,
                true,
                paymentCandidate,
                aiQueued,
                result != null,
                containedAmount,
                containedAmount
                        ? decisionBasis
                        : decisionBasis + "；没有找到可确认的交易金额。",
                operation,
                outcome,
                processingStage,
                icon,
                postTime);
        if (result != null) {
            store.capture(packageName, result, title + "|" + body, postTime);
        }
    }

    private static String directionLabel(TransactionParser.Result result) {
        return result != null && "income".equals(result.type) ? "收入" : "支出";
    }

    private static String asText(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
