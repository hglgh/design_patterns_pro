package com.patterns.structural.adapter.beginner;

/**
 * 适配器入门演示：新代码用「元」下单，老支付 SDK 只认「分 + orderId」。
 *
 * <p>改造前：每个调用方自己换算单位、自己拼参数，换 SDK 时全都要改；
 * 改造后：适配器把老接口转换成新代码期望的接口，调用方不再认识老 SDK。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：每个调用方直接面对老 SDK ==========");

        OldPaySdk sdk = new OldPaySdk();

        // 下单支付：自己把元换成分
        int cents = (int) Math.round(199.00 * 100);
        sdk.doPay(cents, "ORDER-1001");

        // 退款：同一个换算，又写了一遍
        int refundCents = (int) Math.round(59.90 * 100);
        sdk.doPay(refundCents, "ORDER-1002");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 业务代码要懂老 SDK 的「分」和参数顺序，单位换算重复两遍；");
        System.out.println("  2. 老 SDK 升级换方法名，所有调用点都要改；");
        System.out.println("  3. 想再接一个微信支付，业务方法里又要加 if/else。");
    }

    /** 厂商提供的老 SDK：不能改它的源码。 */
    static class OldPaySdk {

        /** 参数顺序和业务代码的直觉相反：先金额（分），再订单号。 */
        void doPay(int amountInCents, String orderId) {
            System.out.println("  [老 SDK] 扣款 " + amountInCents + " 分，订单 " + orderId);
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：业务只认识 PaymentGateway ==========");

        PaymentGateway legacy = new LegacyPayAdapter(new OldPaySdk());
        PaymentGateway wechat = new WechatPayAdapter();

        checkout(legacy, "ORDER-1001", 199.00);
        checkout(wechat, "ORDER-1002", 59.90);

        System.out.println("  —— 新增支付宝：只加一个适配器，checkout 不改 ——");
        checkout(new AlipayAdapter(), "ORDER-1003", 88.80);

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每个外部接口多一个适配器类；");
        System.out.println("  2. 适配器只做转换，不要塞业务规则，否则会变成第二个业务层；");
        System.out.println("  3. 如果本来就能改厂商代码或统一接口，就不需要适配器。");
    }

    /** 业务代码期望的接口：金额用元，参数顺序符合业务直觉。 */
    interface PaymentGateway {
        void pay(String orderNo, double amountYuan);
    }

    private static void checkout(PaymentGateway gateway, String orderNo, double amountYuan) {
        gateway.pay(orderNo, amountYuan);
    }

    /** 适配器：持有老 SDK，把「元 + 订单号」翻译成「分 + 订单号」。 */
    static class LegacyPayAdapter implements PaymentGateway {

        private final OldPaySdk sdk;

        LegacyPayAdapter(OldPaySdk sdk) {
            this.sdk = sdk;
        }

        @Override
        public void pay(String orderNo, double amountYuan) {
            int cents = (int) Math.round(amountYuan * 100);
            sdk.doPay(cents, orderNo);
        }
    }

    /** 另一个实现：假设微信 SDK 的接口正好接近业务接口，也需要统一到 Gateway。 */
    static class WechatPayAdapter implements PaymentGateway {
        @Override
        public void pay(String orderNo, double amountYuan) {
            System.out.println("  [微信支付] 收款 " + amountYuan + " 元，订单 " + orderNo);
        }
    }

    // ---- 新渠道：只新增一个适配器 ----

    static class AlipayAdapter implements PaymentGateway {
        @Override
        public void pay(String orderNo, double amountYuan) {
            System.out.println("  [支付宝] 收款 " + amountYuan + " 元，订单 " + orderNo);
        }
    }
}
