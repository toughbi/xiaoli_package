package com.daytrace.ledger;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A private, deterministic semantic scorer for short Chinese payment messages.
 *
 * <p>It extracts an amount role, checks whether the transaction actually completed,
 * compares income and expense action words, and assigns a confidence score. No
 * notification text leaves the device.
 */
final class NotificationSemanticAnalyzer {
    private static final String AMOUNT =
            "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})(?:\\.[0-9]{1,2})?";
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?i)(?:人民币|RMB|CNY|[¥￥])\\s*(" + AMOUNT + ")");
    private static final Pattern YUAN_AMOUNT = Pattern.compile("(" + AMOUNT + ")\\s*元");
    private static final Pattern ANY_AMOUNT = Pattern.compile(
            "(?<![0-9A-Za-z])(" + AMOUNT + ")(?![0-9])");

    private static final Pattern TRANSFER_OUT = Pattern.compile("(?:向|给)[^。；;]{0,12}(?:转账|付款)");
    private static final Pattern TRANSFER_IN = Pattern.compile("(?:收到|来自)[^。；;]{0,16}(?:转账|红包)");

    private static final String[] PENDING_OR_NOISE = {
            "支付失败", "交易失败", "退款失败", "转账失败", "未支付", "待支付", "等待支付",
            "支付处理中", "交易处理中", "退款处理中", "退款申请", "申请退款", "已提交申请",
            "预计到账", "预计扣款", "即将到账", "即将扣款", "应还", "待还", "还款日",
            "验证码", "安全提醒", "登录提醒", "请勿泄露", "可领取", "优惠券", "立减券",
            "抽奖", "防骗", "开通会员", "开通服务"
    };

    private static final String[] COMPLETION_WORDS = {
            "成功", "已完成", "已支付", "已付款", "已扣款", "已到账", "已入账", "已退款",
            "消费", "支付", "付款", "到账", "入账", "退款", "退回", "转入", "转出", "收款", "收入",
            "支出", "存入", "取出", "扣款", "交易金额"
    };

    private static final WeightedWord[] INCOME_WORDS = {
            new WeightedWord("已到账", 95), new WeightedWord("已入账", 95),
            new WeightedWord("退款成功", 90), new WeightedWord("已退款", 88),
            new WeightedWord("工资", 85), new WeightedWord("薪资", 85),
            new WeightedWord("奖金", 82), new WeightedWord("到账", 78),
            new WeightedWord("入账", 76), new WeightedWord("收入", 72),
            new WeightedWord("退回", 68), new WeightedWord("退款", 64),
            new WeightedWord("转入", 62), new WeightedWord("存入", 62),
            new WeightedWord("收款", 58), new WeightedWord("报销", 58),
            new WeightedWord("红包", 48), new WeightedWord("利息", 48),
            new WeightedWord("分红", 48)
    };

    private static final WeightedWord[] EXPENSE_WORDS = {
            new WeightedWord("消费", 82), new WeightedWord("支付成功", 80),
            new WeightedWord("付款成功", 80), new WeightedWord("已扣款", 82),
            new WeightedWord("已付款", 80), new WeightedWord("已支付", 80),
            new WeightedWord("扣款", 74), new WeightedWord("支出", 72),
            new WeightedWord("转出", 66), new WeightedWord("取款", 62),
            new WeightedWord("取出", 60), new WeightedWord("还款成功", 62),
            new WeightedWord("付款", 48), new WeightedWord("支付", 36),
            new WeightedWord("交易成功", 24)
    };

    private static final String EXPLICIT_AMOUNT_LABELS =
            "实付|实际支付|实际付款|付款金额|支付金额|消费金额|交易金额|入账金额|到账金额|退款金额|收款金额|扣款金额|金额";
    private static final String BALANCE_LABELS =
            "余额|可用额度|可用余额|剩余额度|剩余金额|原价|优惠|立减|积分|尾号|卡号|订单号|手机号|电话";

    private NotificationSemanticAnalyzer() {}

    static final class Analysis {
        final double amount;
        final int direction;
        final String category;
        final int confidence;
        final String reason;

        Analysis(double amount, int direction, String category, int confidence, String reason) {
            this.amount = amount;
            this.direction = direction;
            this.category = category;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    static Analysis analyze(String text) {
        String normalized = normalize(text);
        if (normalized.length() < 3
                || AdvertisementDetector.isAdvertisement(normalized)
                || containsAny(normalized, PENDING_OR_NOISE)) {
            return null;
        }
        if (!containsAny(normalized, COMPLETION_WORDS)
                && !normalized.contains("交易成功")
                && !TRANSFER_OUT.matcher(normalized).find()
                && !TRANSFER_IN.matcher(normalized).find()) {
            return null;
        }

        AmountCandidate amount = findBestAmount(normalized);
        if (amount == null) return null;

        int incomeScore = directionScore(normalized, amount, INCOME_WORDS);
        int expenseScore = directionScore(normalized, amount, EXPENSE_WORDS);
        if (TRANSFER_OUT.matcher(normalized).find()) expenseScore += 75;
        if (TRANSFER_IN.matcher(normalized).find()) incomeScore += 75;

        int direction = 0;
        if (incomeScore > expenseScore + 8) direction = 1;
        else if (expenseScore > incomeScore + 8) direction = -1;
        else if (incomeScore > 0 || expenseScore > 0) {
            // "交易成功" alone is common in Alipay payment receipts but is weak evidence.
            direction = expenseScore >= 20 ? -1 : incomeScore >= 20 ? 1 : 0;
        }
        if (direction == 0) return null;

        int amountBonus = amount.score >= 110 ? 30 : amount.score >= 75 ? 22 : 12;
        int gap = Math.abs(incomeScore - expenseScore);
        int directionBonus = gap >= 45 ? 28 : gap >= 24 ? 20 : gap >= 10 ? 12 : 6;
        int confidence = Math.min(97, 38 + amountBonus + directionBonus);
        String directionText = direction > 0 ? "收入" : "支出";
        String reason = String.format(
                Locale.CHINA,
                "本地语义：金额 %.2f（%s），%s（收入分 %d / 支出分 %d），置信度 %d%%",
                amount.value,
                amount.role,
                directionText,
                incomeScore,
                expenseScore,
                confidence);
        return new Analysis(
                amount.value,
                direction,
                guessCategory(normalized, direction),
                confidence,
                reason);
    }

    static boolean containsAmount(String text) {
        return findBestAmount(normalize(text)) != null;
    }

    static boolean containsNumber(String text) {
        String normalized = normalize(text);
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isDigit(normalized.charAt(index))) return true;
        }
        return false;
    }

    static Double bestAmountValue(String text) {
        AmountCandidate candidate = findBestAmount(normalize(text));
        return candidate == null ? null : candidate.value;
    }

    static boolean hasTransactionSignal(String text) {
        String normalized = normalize(text);
        if (!containsAny(normalized, COMPLETION_WORDS) && !normalized.contains("交易成功")) {
            return false;
        }
        return findBestAmount(normalized) != null;
    }

    private static AmountCandidate findBestAmount(String text) {
        Map<String, AmountCandidate> candidates = new LinkedHashMap<>();
        collectCandidates(CURRENCY_AMOUNT, text, 76, "货币金额", candidates);
        collectCandidates(YUAN_AMOUNT, text, 48, "元金额", candidates);
        collectCandidates(ANY_AMOUNT, text, 8, "数值", candidates);

        AmountCandidate best = null;
        for (AmountCandidate candidate : candidates.values()) {
            scoreCandidate(text, candidate);
            if (candidate.value >= 0.01
                    && candidate.value < 100_000_000
                    && candidate.score >= 44
                    && (best == null || candidate.score > best.score)) {
                best = candidate;
            }
        }
        return best;
    }

    private static void collectCandidates(
            Pattern pattern,
            String text,
            int baseScore,
            String role,
            Map<String, AmountCandidate> candidates) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            String key = start + ":" + end;
            double value;
            try {
                value = Double.parseDouble(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                continue;
            }
            AmountCandidate existing = candidates.get(key);
            if (existing == null || baseScore > existing.score) {
                candidates.put(key, new AmountCandidate(value, start, end, baseScore, role));
            }
        }
    }

    private static void scoreCandidate(String text, AmountCandidate candidate) {
        String before = text.substring(Math.max(0, candidate.start - 22), candidate.start);
        String after = text.substring(candidate.end, Math.min(text.length(), candidate.end + 18));
        String compactBefore = before.replaceAll("[\\s:：]", "");

        if (compactBefore.matches(".*(?:" + EXPLICIT_AMOUNT_LABELS + ")$")) {
            candidate.score += 90;
            candidate.role = "明确金额字段";
        } else if (containsAny(compactBefore, EXPLICIT_AMOUNT_LABELS.split("\\|"))) {
            candidate.score += 38;
        }
        if (containsAny(compactBefore, "消费", "支付", "付款", "扣款", "转账", "收入", "退款",
                "收款", "到账", "入账", "取出", "存入")) {
            candidate.score += 48;
        }
        if (compactBefore.matches(".*(?:" + BALANCE_LABELS + ")(?:为|是)?$")) {
            candidate.score -= 130;
            candidate.role = "余额或非交易数值";
        }
        if (compactBefore.matches(".*(?:原价|优惠|立减|积分|订单金额|商品金额)$")) {
            candidate.score -= 70;
            candidate.role = "价格或优惠数值";
        }
        if (after.trim().matches("^(?:年|月|日|时|分|秒|期|号).*")) {
            candidate.score -= 150;
            candidate.role = "日期或编号";
        }
        if (compactBefore.matches(".*(?:尾号|卡号|订单号|单号|手机号|电话)$")) {
            candidate.score -= 150;
            candidate.role = "日期或编号";
        }
    }

    private static int directionScore(String text, AmountCandidate amount, WeightedWord[] words) {
        int score = 0;
        int localStart = Math.max(0, amount.start - 26);
        int localEnd = Math.min(text.length(), amount.end + 26);
        String local = text.substring(localStart, localEnd);
        for (WeightedWord word : words) {
            if (text.contains(word.text)) score += word.weight;
            if (local.contains(word.text)) score += word.weight;
        }
        return score;
    }

    private static String guessCategory(String text, int direction) {
        if (direction > 0) {
            if (containsAny(text, "工资", "薪资")) return "工资";
            if (containsAny(text, "奖金", "年终奖")) return "奖金";
            if (containsAny(text, "退款", "退回")) return "退款";
            if (containsAny(text, "红包")) return "红包";
            if (containsAny(text, "利息", "分红", "理财")) return "理财";
            if (containsAny(text, "报销")) return "报销";
            return "其他";
        }
        if (containsAny(text, "餐", "饭", "外卖", "咖啡", "奶茶", "美团", "饿了么", "麦当劳", "肯德基")) {
            return "餐饮";
        }
        if (containsAny(text, "地铁", "公交", "打车", "滴滴", "加油", "高铁", "火车", "机票", "充电", "停车")) {
            return "交通";
        }
        if (containsAny(text, "淘宝", "京东", "拼多多", "超市", "商城", "购物", "便利店", "商场", "药店")) {
            return "购物";
        }
        if (containsAny(text, "房租", "水费", "电费", "燃气", "物业", "宽带")) return "居住";
        if (containsAny(text, "电影", "游戏", "视频", "音乐", "演出", "会员")) return "娱乐";
        if (containsAny(text, "医院", "药房", "挂号", "体检", "诊所")) return "医疗";
        if (containsAny(text, "课程", "书店", "培训", "学习")) return "学习";
        if (containsAny(text, "话费", "流量", "通讯")) return "通讯";
        return "其他";
    }

    private static String normalize(String text) {
        if (text == null) return "";
        StringBuilder normalized = new StringBuilder();
        for (char value : text.toCharArray()) {
            if (value >= '\uFF10' && value <= '\uFF19') normalized.append((char) (value - '\uFF10' + '0'));
            else if (value != '\u200B' && value != '\u200C' && value != '\u200D') normalized.append(value);
        }
        return normalized.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private static final class WeightedWord {
        final String text;
        final int weight;

        WeightedWord(String text, int weight) {
            this.text = text;
            this.weight = weight;
        }
    }

    private static final class AmountCandidate {
        final double value;
        final int start;
        final int end;
        int score;
        String role;

        AmountCandidate(double value, int start, int end, int score, String role) {
            this.value = value;
            this.start = start;
            this.end = end;
            this.score = score;
            this.role = role;
        }
    }
}
