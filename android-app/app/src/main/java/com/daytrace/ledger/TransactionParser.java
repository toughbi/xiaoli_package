package com.daytrace.ledger;

import java.util.Locale;

public final class TransactionParser {
    private TransactionParser() {}

    public static final class Result {
        public final String type;
        public final double amount;
        public final String channel;
        public final String category;
        public final String note;
        public final String sourceLabel;
        public final String confidence;
        public final String explanation;

        Result(
                String type,
                double amount,
                String channel,
                String category,
                String note,
                String sourceLabel,
                String confidence,
                String explanation) {
            this.type = type;
            this.amount = amount;
            this.channel = channel;
            this.category = category;
            this.note = note;
            this.sourceLabel = sourceLabel;
            this.confidence = confidence;
            this.explanation = explanation;
        }
    }

    public static Result parse(
            String sourceKey,
            String sourceLabel,
            String title,
            String body,
            long capturedAt) {
        String text = ((title == null ? "" : title) + " " + (body == null ? "" : body)).trim();
        if (!isReliableSourceText(sourceKey, text)) return null;

        NotificationSemanticAnalyzer.Analysis semantic = NotificationSemanticAnalyzer.analyze(text);
        if (semantic == null) return null;

        String type = semantic.direction > 0 ? "income" : "expense";
        String channel = detectChannel(sourceKey, text);
        String confidence = semantic.confidence >= 82
                ? "high"
                : semantic.confidence >= 62 ? "medium" : "low";
        return new Result(
                type,
                semantic.amount,
                channel,
                semantic.category,
                buildNote(title, body),
                sourceLabel,
                confidence,
                semantic.reason);
    }

    public static boolean isPotentialTransaction(String title, String body) {
        return NotificationSemanticAnalyzer.hasTransactionSignal(textOf(title, body));
    }

    public static boolean containsAmount(String title, String body) {
        return NotificationSemanticAnalyzer.containsAmount(textOf(title, body));
    }

    public static boolean containsNumber(String text) {
        return NotificationSemanticAnalyzer.containsNumber(text);
    }

    public static String channelForPackage(String packageName) {
        if (packageName == null) return "other";
        String value = packageName.toLowerCase(Locale.ROOT);
        if (value.startsWith("sms:")) return "bank";
        if (value.equals("com.tencent.mm")) return "wechat";
        if (value.equals("com.eg.android.alipaygphone")) return "alipay";
        if (value.contains("unionpay")) return "unionpay";
        if (value.contains("paypal")) return "paypal";
        if (isBankPackage(value)) return "bank";
        return "other";
    }

    public static String sourceLabelForPackage(String packageName) {
        String channel = channelForPackage(packageName);
        switch (channel) {
            case "wechat":
                return "微信支付通知";
            case "alipay":
                return "支付宝通知";
            case "unionpay":
                return "云闪付通知";
            case "paypal":
                return "PayPal 通知";
            case "bank":
                return packageName != null && packageName.startsWith("sms:")
                        ? "银行短信"
                        : "银行 App 通知";
            default:
                return "支付通知";
        }
    }

    public static boolean isRelevantPackage(String packageName) {
        if (packageName == null) return false;
        String value = packageName.toLowerCase(Locale.ROOT);
        return value.equals("com.tencent.mm")
                || value.equals("com.eg.android.alipaygphone")
                || value.contains("unionpay")
                || value.contains("paypal")
                || value.contains("wallet")
                || isBankPackage(value);
    }

    private static boolean isBankPackage(String value) {
        String[] tokens = {
                "bank", ".boc", ".icbc", ".ccb", ".abc", ".cmb", ".spdb", ".cib", ".psbc",
                "pingan", "cebbank", "bankofbeijing", "bankofshanghai", "chinamworld"
        };
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private static boolean isReliableSourceText(String sourceKey, String text) {
        if ("com.tencent.mm".equals(sourceKey)) {
            return containsAny(
                    text, "微信支付", "财付通", "支付凭证", "付款成功", "支付成功",
                    "收款到账", "转账到账", "退款到账");
        }
        // Alipay's package is authoritative; the notification title is often just
        // "交易成功" and does not include the brand name.
        return true;
    }

    private static String detectChannel(String sourceKey, String text) {
        if (text.contains("微信") || text.contains("财付通")) return "wechat";
        if (text.contains("支付宝")) return "alipay";
        if (text.contains("云闪付")) return "unionpay";
        if (text.contains("Apple Pay") || text.contains("苹果支付")) return "applepay";
        if (text.contains("PayPal")) return "paypal";
        if (sourceKey != null && sourceKey.startsWith("sms:")) return "bank";
        return channelForPackage(sourceKey);
    }

    private static String buildNote(String title, String body) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanBody = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (cleanTitle.matches("微信支付|支付宝|云闪付|PayPal|.+银行|.*短信")) cleanTitle = "";
        String note = (cleanTitle + (cleanTitle.isEmpty() || cleanBody.isEmpty() ? "" : " · ") + cleanBody)
                .trim();
        return note.length() > 40 ? note.substring(0, 40) : note;
    }

    private static String textOf(String title, String body) {
        return ((title == null ? "" : title) + " " + (body == null ? "" : body)).trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
