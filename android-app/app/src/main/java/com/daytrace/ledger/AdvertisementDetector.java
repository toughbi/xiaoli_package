package com.daytrace.ledger;

import java.util.regex.Pattern;

public final class AdvertisementDetector {
    private static final String[] PROMOTION_PHRASES = {
            "限时", "抽大疆", "可抽", "首次申请", "最高可享", "最高可得", "最高可抽",
            "领取福利", "立即领取", "优惠活动", "参与活动", "点击了解", "了解详情",
            "推广", "广告", "参保", "年化利率", "贷款额度", "低至"
    };

    private static final Pattern CLEAR_TRANSACTION = Pattern.compile(
            "(?:尾号|卡号).{0,30}(?:消费|支出|扣款|入账|到账|存入|取出)"
                    + "|(?:交易|支付|付款|收款|退款|扣款|消费)(?:成功|完成|到账)"
                    + "|支付凭证|入账通知");

    private AdvertisementDetector() {}

    public static boolean isAdvertisement(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return false;

        int score = 0;
        for (String phrase : PROMOTION_PHRASES) {
            if (normalized.contains(phrase)) score++;
        }
        if (normalized.contains("抽大疆") || normalized.contains("参保")
                || normalized.contains("推广") || normalized.contains("广告")) {
            return true;
        }
        if (score >= 2) return true;
        return score >= 1 && !CLEAR_TRANSACTION.matcher(normalized).find();
    }

    static String reason(String text) {
        return isAdvertisement(text) ? "广告或营销信息，不包含明确的已完成收支" : "";
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
