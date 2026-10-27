package com.patterns.behavioral.strategy.beginner;

/**
 * 策略入门演示：超市收银台的多种计价方式。
 *
 * <p>改造前：所有折扣规则塞在一个 total() 里，用 type 字符串分支；
 * 新增活动要改方法，type 写错还会静默按原价收钱。
 *
 * <p>改造后：每种折扣一个策略对象，收银台只负责调用 payable()。
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
        System.out.println("========== 改造前：一个方法里堆所有折扣 ==========");

        OldCashier cashier = new OldCashier();
        int[] amounts = {100, 300, 1000, 2000};
        for (int amount : amounts) {
            System.out.println("  " + amount + " 元："
                    + "normal=" + cashier.total(amount, "normal") + "，"
                    + "weekend=" + cashier.total(amount, "weekend") + "，"
                    + "member=" + cashier.total(amount, "member") + "，"
                    + "coupon=" + cashier.total(amount, "coupon"));
        }

        System.out.println("  未知类型「member 」（末尾多个空格）："
                + cashier.total(1000, "member ") + " 元");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 新增「新人立减 15」要再往 total() 里加一个 if；");
        System.out.println("  2. 会员封顶 200 的规则埋在方法中间，改错很难发现；");
        System.out.println("  3. type 拼错不会报错，直接按原价收钱。");
    }

    static class OldCashier {

        int total(int amount, String type) {
            if ("normal".equals(type)) {
                return amount;
            }
            if ("weekend".equals(type)) {
                return amount * 90 / 100;
            }
            if ("member".equals(type)) {
                int payable = amount * 80 / 100;
                int saved = amount - payable;
                if (saved > 200) {
                    payable = amount - 200;
                }
                return payable;
            }
            if ("coupon".equals(type)) {
                if (amount >= 300) {
                    return amount - 80;
                }
                if (amount >= 100) {
                    return amount - 20;
                }
                return amount;
            }
            return amount;             // 未知类型：静默按原价
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：每种折扣一个策略对象 ==========");

        DiscountStrategy[] strategies = {
                new NormalPrice(),
                new WeekendDiscount(),
                new MemberDiscount(),
                new CouponDiscount(),
                new NewcomerDiscount()
        };

        int[] amounts = {100, 300, 1000, 2000};
        for (int amount : amounts) {
            StringBuilder line = new StringBuilder("  " + amount + " 元：");
            for (DiscountStrategy strategy : strategies) {
                line.append(strategy.name())
                        .append("=")
                        .append(strategy.payable(amount))
                        .append("  ");
            }
            System.out.println(line);
        }

        System.out.println();
        System.out.println("  —— 收银台只依赖 DiscountStrategy，换策略不用改收银台 ——");
        Cashier cashier = new Cashier(new NormalPrice());
        System.out.println("  正常价 2000 → " + cashier.checkout(2000));
        cashier.setStrategy(new MemberDiscount());
        System.out.println("  会员价 2000 → " + cashier.checkout(2000));
        cashier.setStrategy(new NewcomerDiscount());
        System.out.println("  新人价 2000 → " + cashier.checkout(2000));

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每种算法一个类，规则很少时反而更啰嗦；");
        System.out.println("  2. 选哪个策略由客户端/配置决定，模式本身不替你选；");
        System.out.println("  3. 策略需要数据（门槛、折扣率）时，要通过构造器传进去。");
    }

    /** 策略接口：所有折扣都回答「应付多少」。 */
    interface DiscountStrategy {

        int payable(int amount);

        String name();
    }

    static class NormalPrice implements DiscountStrategy {

        @Override
        public int payable(int amount) {
            return amount;
        }

        @Override
        public String name() {
            return "原价";
        }
    }

    static class WeekendDiscount implements DiscountStrategy {

        @Override
        public int payable(int amount) {
            return amount * 90 / 100;
        }

        @Override
        public String name() {
            return "九折";
        }
    }

    static class MemberDiscount implements DiscountStrategy {

        private static final int MAX_SAVING = 200;

        @Override
        public int payable(int amount) {
            int payable = amount * 80 / 100;
            int saved = amount - payable;
            return saved > MAX_SAVING ? amount - MAX_SAVING : payable;
        }

        @Override
        public String name() {
            return "会员八折";
        }
    }

    static class CouponDiscount implements DiscountStrategy {

        @Override
        public int payable(int amount) {
            if (amount >= 300) {
                return amount - 80;
            }
            if (amount >= 100) {
                return amount - 20;
            }
            return amount;
        }

        @Override
        public String name() {
            return "满减";
        }
    }

    /** 新活动：只新增一个类，收银台和已有策略都不改。 */
    static class NewcomerDiscount implements DiscountStrategy {

        @Override
        public int payable(int amount) {
            return Math.max(0, amount - 15);
        }

        @Override
        public String name() {
            return "新人立减";
        }
    }

    /** Context：持有策略并调用它，不关心具体算法。 */
    static class Cashier {

        private DiscountStrategy strategy;

        Cashier(DiscountStrategy strategy) {
            this.strategy = strategy;
        }

        void setStrategy(DiscountStrategy strategy) {
            this.strategy = strategy;
        }

        int checkout(int amount) {
            return strategy.payable(amount);
        }
    }
}
