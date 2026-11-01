package com.daytrace.ledger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class BankSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        StringBuilder body = new StringBuilder();
        String sender = "";
        for (SmsMessage message : messages) {
            if (message == null) continue;
            if (sender.isEmpty() && message.getDisplayOriginatingAddress() != null) {
                sender = message.getDisplayOriginatingAddress();
            }
            if (message.getMessageBody() != null) body.append(message.getMessageBody());
        }
        if (body.length() == 0) return;

        String messageBody = body.toString();
        long receivedAt = System.currentTimeMillis();
        String sourceKey = "sms:" + sender;
        String title = sender.isEmpty() ? "银行短信" : sender;
        String eventId = NotificationDiagnostics.eventId(
                sourceKey, receivedAt, title, messageBody);
        NotificationIconFingerprint.Snapshot icon =
                new NotificationIconFingerprint.Snapshot(
                        "银行短信", -1, -1, "短信来源");
        boolean containedAmount = TransactionParser.containsAmount(title, messageBody);
        boolean containsNumber = TransactionParser.containsNumber(messageBody);
        boolean advertisement = AdvertisementDetector.isAdvertisement(
                title + " " + messageBody);
        NotificationDiagnostics diagnostics = new NotificationDiagnostics(context);

        if (!containsNumber || advertisement) {
            diagnostics.record(
                    eventId,
                    sourceKey,
                    title,
                    messageBody,
                    false,
                    false,
                    false,
                    false,
                    containedAmount,
                    advertisement
                            ? AdvertisementDetector.reason(title + " " + messageBody)
                            : "本地初筛发现短信正文没有数字。",
                    "执行本地初步筛选：明显广告和完全无数字的短信不交给 AI。",
                    "初筛未通过，不进入 AI，不生成账目。",
                    "PRELIMINARY_REJECTED",
                    icon,
                    receivedAt);
            return;
        }

        PendingAiNotificationStore.AddResult result =
                new PendingAiNotificationStore(context).add(
                        sourceKey,
                        title,
                        messageBody,
                        receivedAt,
                        icon,
                        eventId);
        diagnostics.record(
                eventId,
                sourceKey,
                title,
                messageBody,
                true,
                true,
                result == PendingAiNotificationStore.AddResult.ADDED,
                false,
                containedAmount,
                "本地初筛通过：不是明显广告，并且短信包含数字。",
                result == PendingAiNotificationStore.AddResult.ADDED
                        ? "短信已持久化并进入 AI 工作池。"
                        : "短信初筛通过，但未能写入 AI 持久队列。",
                result == PendingAiNotificationStore.AddResult.ADDED
                        ? "等待 DeepSeek 判断是否属于已完成交易；AI 返回前不生成账目。"
                        : "本次没有生成账目。",
                result == PendingAiNotificationStore.AddResult.ADDED
                        ? "AI_QUEUED"
                        : "AI_QUEUE_FAILED",
                icon,
                receivedAt);
        if (result == PendingAiNotificationStore.AddResult.ADDED) {
            PaymentNotificationService.wakePendingAnalysis(context);
        }
    }
}
