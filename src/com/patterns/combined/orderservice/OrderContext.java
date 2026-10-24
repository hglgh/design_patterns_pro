package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 订单上下文：<b>折扣计算需要的、不属于「价格」本身的那部分信息</b>。
 *
 * <p>策略需要知道的不只是「当前多少钱」，还有「谁在买」：
 * 会员等级、可用积分、是否新客、手里有没有券、今天是不是会员日。
 * 这些字段既不属于价格，也不属于某一个策略 ——
 * 于是它们被收进一个对象，沿着整条链传递，谁需要谁自己读。</p>
 *
 * <p><b>为什么不用 {@code Map<String, Object>}？</b></p>
 * Map 看起来更「灵活」，加字段不用改类。代价是：
 * <ul>
 *   <li>拼错 key 时编译器不管，运行期返回 {@code null}，然后 NPE 或静默当成 0 ——
 *       这正是解释器模式原项目「未知字段静默返回 false」的同类问题；</li>
 *   <li>没有类型：{@code get("points")} 拿到 {@code Object}，每个调用点都要强转，
 *       转错了是运行期 {@code ClassCastException}；</li>
 *   <li>没有文档：读代码的人必须翻遍所有策略才知道有哪些 key、各自什么含义。</li>
 * </ul>
 * <p>用显式字段换来的是：IDE 能补全、重构能改名、编译器能检查。
 * 上下文字段是<b>业务模型</b>，不是任意字典，值得为它写一个类。</p>
 *
 * <p><b>为什么是不可变的？</b></p>
 * 链上有多个节点，每个都会读上下文。如果允许某个策略中途
 * 把 {@code availablePoints} 改成 0（比如「我先扣了」），
 * 后面的策略看到的就不是订单的原始状态，而是被前一个节点污染过的状态。
 * 不可变对象让「每个节点看到的上下文完全一致」成为一条无法违反的约定，
 * 副作用只能由订单服务在链外统一处理。</p>
 *
 * <p><b>为什么 {@code couponCode} 允许为空串而不是 null？</b></p>
 * 空串表示「没有填券」这个已知状态；null 表示「不知道有没有」。
 * 业务上不存在「不知道」，所以构造时把 null 统一收敛成空串，
 * 让调用方永远不需要写 {@code if (coupon != null)}。这类
 * 「null 收敛」是本项目所有值对象的统一做法。</p>
 *
 * @author design-patterns-pro
 */
public final class OrderContext {

    /** 订单原价。注意它和「当前价格」不是一回事：链上传递的价格会逐步减少。 */
    private final BigDecimal orderAmount;

    /** 会员等级 1-5。等级越高，可用的折扣种类越多。 */
    private final int userLevel;

    /** 可用积分。策略只读它，扣减由订单服务在链外完成。 */
    private final int availablePoints;

    /** 是否新客。新客专项折扣只看这一个字段。 */
    private final boolean newCustomer;

    /** 券码，空串表示未使用券。 */
    private final String couponCode;

    /** 是否会员日。会员日可以叠加额外折扣。 */
    private final boolean memberDay;

    private OrderContext(Builder builder) {
        this.orderAmount = builder.orderAmount;
        this.userLevel = builder.userLevel;
        this.availablePoints = builder.availablePoints;
        this.newCustomer = builder.newCustomer;
        this.couponCode = builder.couponCode;
        this.memberDay = builder.memberDay;
    }

    /**
     * 以订单金额创建一个最朴素的上下文：等级 1、无积分、非新客、无券、非会员日。
     *
     * @param orderAmount 订单原价，例如 {@code "199.00"}
     * @return 新上下文
     * @throws IllegalArgumentException 金额为 null、非数字或不为正
     */
    public static OrderContext of(String orderAmount) {
        return builder(orderAmount).build();
    }

    /**
     * 从订单金额开始逐步构造。
     *
     * @param orderAmount 订单原价
     * @return 构造器
     * @throws IllegalArgumentException 金额为 null、非数字或不为正
     */
    public static Builder builder(String orderAmount) {
        if (orderAmount == null || orderAmount.isBlank()) {
            throw new IllegalArgumentException("订单金额不能为空");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(orderAmount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("订单金额不是合法数字：" + orderAmount, e);
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("订单金额必须为正：" + orderAmount);
        }
        return new Builder(DiscountPolicy.money(amount));
    }

    /** 订单原价。 */
    public BigDecimal orderAmount() {
        return orderAmount;
    }

    /** 会员等级。 */
    public int userLevel() {
        return userLevel;
    }

    /** 可用积分。 */
    public int availablePoints() {
        return availablePoints;
    }

    /** 是否新客。 */
    public boolean newCustomer() {
        return newCustomer;
    }

    /** 券码，未使用时为空串。 */
    public String couponCode() {
        return couponCode;
    }

    /** 是否会员日。 */
    public boolean memberDay() {
        return memberDay;
    }

    /**
     * 用于输出的摘要，例如 {@code ￥199.00 / Lv.3 / 积分 800 / 非新客 / 无券 / 非会员日}。
     *
     * <p>演示输出要能逐字节复现，所以这里的拼接顺序固定，
     * 不依赖任何集合的迭代顺序。</p>
     *
     * @return 单行摘要
     */
    public String describe() {
        return com.patterns.util.Money.yuan(orderAmount)
                + " / Lv." + userLevel
                + " / 积分 " + availablePoints
                + (newCustomer ? " / 新客" : " / 非新客")
                + (couponCode.isEmpty() ? " / 无券" : " / 券 " + couponCode)
                + (memberDay ? " / 会员日" : " / 非会员日");
    }

    @Override
    public String toString() {
        return describe();
    }

    /**
     * 上下文构造器。
     *
     * <p><b>为什么不用「with 链」而要一个 Builder？</b>
     * 上一节的解释器 {@code Context} 用 {@code with(name, value)}
     * 返回新实例，是因为它的字段本质是一个 Map，逐个追加很自然。
     * 订单上下文有 6 个<b>类型各不相同</b>的字段，如果也用
     * {@code withX(...).withY(...)}，每加一个字段就要多一个方法、
     * 多一个复制构造函数，6 个字段会产生 21 个组合方法。
     * 构造器模式让「一次设置多个字段再一次性校验」成为可能，
     * 也避免了「构造到一半的对象」在半成品状态下被传递出去。</p>
     */
    public static final class Builder {

        private final BigDecimal orderAmount;
        private int userLevel = 1;
        private int availablePoints;
        private boolean newCustomer;
        private String couponCode = "";
        private boolean memberDay;

        private Builder(BigDecimal orderAmount) {
            this.orderAmount = orderAmount;
        }

        /**
         * 设置会员等级。
         *
         * @param level 1-5
         * @return 本构造器
         * @throws IllegalArgumentException 等级越界
         */
        public Builder userLevel(int level) {
            if (level < 1 || level > 5) {
                throw new IllegalArgumentException("会员等级必须在 1-5 之间：" + level);
            }
            this.userLevel = level;
            return this;
        }

        /**
         * 设置可用积分。
         *
         * @param points 非负整数
         * @return 本构造器
         * @throws IllegalArgumentException 积分为负
         */
        public Builder availablePoints(int points) {
            if (points < 0) {
                throw new IllegalArgumentException("可用积分不能为负：" + points);
            }
            this.availablePoints = points;
            return this;
        }

        /**
         * 设置是否新客。
         *
         * @param value true 表示新客
         * @return 本构造器
         */
        public Builder newCustomer(boolean value) {
            this.newCustomer = value;
            return this;
        }

        /**
         * 设置券码；null 视为「无券」。
         *
         * @param code 券码，可为 null
         * @return 本构造器
         */
        public Builder coupon(String code) {
            this.couponCode = code == null ? "" : code.trim();
            return this;
        }

        /**
         * 设置是否会员日。
         *
         * @param value true 表示会员日
         * @return 本构造器
         */
        public Builder memberDay(boolean value) {
            this.memberDay = value;
            return this;
        }

        /**
         * 构造上下文。
         *
         * @return 不可变上下文
         */
        public OrderContext build() {
            return new OrderContext(this);
        }
    }
}
