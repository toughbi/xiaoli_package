import com.daytrace.ledger.TransactionParser;
import com.daytrace.ledger.AdvertisementDetector;

public final class TransactionParserSelfTest {
    private TransactionParserSelfTest() {}

    public static void main(String[] args) {
        verifyExpense(
                "微信支付",
                "微信支付凭证￥28.60",
                "wechat",
                28.60,
                "其他");
        verifyExpense(
                "支付宝",
                "你已成功付款15.00元，订单已完成",
                "alipay",
                15.00,
                "其他");
        verifyExpense(
                "com.eg.android.AlipayGphone",
                "交易成功，支出 20.00元",
                "alipay",
                20.00,
                "其他");
        verifyExpense(
                "com.eg.android.AlipayGphone",
                "交易成功，实付18.50元，账户余额100.00元",
                "alipay",
                18.50,
                "其他");
        verifyExpense(
                "com.eg.android.AlipayGphone",
                "交易成功，订单金额20.00元，实付18.00元",
                "alipay",
                18.00,
                "其他");
        verifyExpense(
                "sms:95588",
                "您尾号8899卡POS消费人民币88.00元，可用额度12000.00元。",
                "bank",
                88.00,
                "其他");
        verifyIncome(
                "com.eg.android.AlipayGphone",
                "收款到账 5.00元",
                "alipay",
                5.00,
                "其他");
        verifyExpense(
                "sms:95588",
                "您尾号1234卡10月27日消费人民币45.80元，余额1000元。",
                "bank",
                45.80,
                "其他");
        verifyIncome(
                "sms:95588",
                "工资到账人民币12,800.00元，请注意查收。",
                "bank",
                12_800.00,
                "工资");
        verifyIncome(
                "支付宝",
                "退款到账￥19.90，已退回原支付账户。",
                "alipay",
                19.90,
                "退款");

        TransactionParser.Result ignored = TransactionParser.parse(
                "com.tencent.mm",
                "微信支付通知",
                "微信支付",
                "微信支付安全提醒，请妥善保管验证码。",
                System.currentTimeMillis());
        require(ignored == null, "security reminder must not become a transaction");
        TransactionParser.Result chatMessage = TransactionParser.parse(
                "com.tencent.mm",
                "微信支付通知",
                "张三",
                "今天消费了10元",
                System.currentTimeMillis());
        require(chatMessage == null, "ordinary WeChat chat must not become a transaction");
        require(
                parse("支付宝退款处理中，金额20.00元") == null,
                "pending refund must not become completed income");
        require(
                parse("支付宝支付失败，金额20.00元") == null,
                "failed payment must not become expense");
        require(
                parse("支付宝可用额度20000.00元") == null,
                "available credit must not become a transaction");
        require(
                parse("支付宝预计到账100.00元") == null,
                "future receipt must not become completed income");

        String advertisement =
                "限时抽大疆pocket4！首次申请可抽大疆，用款消费更可享最高5000元。"
                        + "【支付宝】2027年重庆渝快保启动参保，最高可享保障。";
        require(
                AdvertisementDetector.isAdvertisement(advertisement),
                "promotional notification must be recognized as advertisement");
        require(
                TransactionParser.parse(
                        "sms:10686366251201",
                        "银行短信",
                        "10686366251201",
                        advertisement,
                        System.currentTimeMillis()) == null,
                "promotional amount must not become an expense");
        require(
                !AdvertisementDetector.isAdvertisement(
                        "您尾号1234卡消费人民币45.80元，可用额度12000元。"),
                "completed bank transaction must not be rejected as advertisement");
        require(
                TransactionParser.containsNumber("实付 18.50 元"),
                "numeric notification must pass the local number check");
        require(
                !TransactionParser.containsNumber("请查收本次到账"),
                "notification without digits must be rejected by the local number check");
        require(
                "bank".equals(TransactionParser.channelForPackage("sms:95588")),
                "SMS source must be mapped to the bank channel");
        System.out.println("TransactionParserSelfTest: all checks passed");
    }

    private static TransactionParser.Result parse(String text) {
        return TransactionParser.parse(
                "com.eg.android.AlipayGphone",
                "支付宝通知",
                "支付宝",
                text,
                System.currentTimeMillis());
    }

    private static void verifyExpense(
            String source,
            String text,
            String channel,
            double amount,
            String category) {
        TransactionParser.Result result = TransactionParser.parse(
                source,
                "测试来源",
                source,
                text,
                System.currentTimeMillis());
        require(result != null, "expected expense: " + text);
        require(result.type.equals("expense"), "wrong type: " + text);
        require(result.channel.equals(channel), "wrong channel: " + text);
        require(Math.abs(result.amount - amount) < 0.001, "wrong amount: " + text);
        require(result.category.equals(category), "wrong category: " + text + " -> " + result.category);
    }

    private static void verifyIncome(
            String source,
            String text,
            String channel,
            double amount,
            String category) {
        TransactionParser.Result result = TransactionParser.parse(
                source,
                "测试来源",
                source,
                text,
                System.currentTimeMillis());
        require(result != null, "expected income: " + text);
        require(result.type.equals("income"), "wrong type: " + text);
        require(result.channel.equals(channel), "wrong channel: " + text);
        require(Math.abs(result.amount - amount) < 0.001, "wrong amount: " + text);
        require(result.category.equals(category), "wrong category: " + text + " -> " + result.category);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
