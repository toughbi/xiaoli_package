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
        TransactionParser.Result parsed = TransactionParser.parse(
                "sms:" + sender,
                "银行短信",
                sender,
                messageBody,
                System.currentTimeMillis());
        if (parsed == null) return;

        new AutoCaptureStore(context).capture(
                "sms:" + sender,
                parsed,
                sender + "|" + messageBody,
                System.currentTimeMillis());
    }
}
