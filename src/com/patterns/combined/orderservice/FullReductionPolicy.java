package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 满减策略：<b>订单金额达到门槛，就减固定金额</b>。
 *
 * <p>这是最古老也最常见的促销形式：「满 100 减 20」。
 * 它只有两个参数，但原项目把它写死了 ——
 * {@code FullReductionStrategy} 里直接写着 {@code if (price >= 100) return price - 20;}，
 * 于是运营想改成「满 200 减 30」只能改代码、走发版流程。
 * 本项目把门槛和额度变成<b>构造参数</b>，同一个类能表达所有满减规则。</p>
 *
 * <p><b>为什么用 record？</b></p>
 * 策略的全部状态就是两个参数，没有可变部分。record 自动提供
 * 构造器、访问器、{@code equals}、{@code hashCode}、{@code toString}，
 * 并且<b>字段天然 final</b> —— 这正好符合「策略必须无状态、可共享」的要求。
 * 如果哪天需要「同一个策略实例被一千个线程同时调用」，record 不会有任何风险。</p>
 *
 * <p><b>为什么构造时要拒绝「减免额度 ≥ 门槛」？</b></p>
 * 「满 100 减 120」在技术上能跑：{@code legal()} 会把减免截到当前价，
 * 结果是不倒找钱，但用户花 100 元买到 100 元的东西只付 0 元 ——
 * 这显然不是运营想表达的意思，而是配置写错了。
 * <b>这类错误应该在装配阶段（构造对象时）就炸掉，而不是等真实订单跑出来才发现。</b>
 * 这就是「快速失败」原则：错误暴露得越早，修复成本越低。</p>
 *
 * <p><b>为什么门槛判断用 {@code compareTo} 而不是 {@code equals}？</b></p>
 * {@code BigDecimal} 的 {@code equals} 连小数位数一起比较：
 * {@code new BigDecimal("100").equals(new BigDecimal("100.00"))} 是 {@code false}。
 * 金额比较永远用 {@link BigDecimal#compareTo}，这是本项目所有比较的统一做法。</p>
 *
 * @param name      策略名称，例如 {@code "满 100 减 20"}
 * @param threshold 门槛金额，必须为正
 * @param reduction 减免金额，必须为正且小于门槛
 * @author design-patterns-pro
 */
public record FullReductionPolicy(String name, BigDecimal threshold, BigDecimal reduction)
        implements DiscountPolicy {

    /**
     * 校验参数。
     *
     * @throws IllegalArgumentException 名称为空、门槛或额度非正、额度不小于门槛
     */
    public FullReductionPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("满减策略必须有名称");
        }
        if (threshold == null || threshold.signum() <= 0) {
            throw new IllegalArgumentException("满减门槛必须为正：" + threshold);
        }
        if (reduction == null || reduction.signum() <= 0) {
            throw new IllegalArgumentException("满减额度必须为正：" + reduction);
        }
        if (reduction.compareTo(threshold) >= 0) {
            throw new IllegalArgumentException(
                    "满减额度 " + reduction.toPlainString() + " 不能大于等于门槛 "
                            + threshold.toPlainString() + "：这会让订单直接变成 0 元");
        }
        threshold = DiscountPolicy.money(threshold);
        reduction = DiscountPolicy.money(reduction);
    }

    /**
     * 达到门槛就减固定额度。
     *
     * <p>注意最后一步 {@link DiscountPolicy#legal}：
     * 它保证「门槛 100、额度 20、当前价 10」这种不可能的场景
     * （链前面的折扣已经把价格减到 10）也不会算出负数。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文（满减不看上下文，但接口要求传）
     * @return 减免金额，未达门槛时为 0
     */
    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        DiscountPolicy.requireValidPrice(price);
        if (price.compareTo(threshold) < 0) {
            return ZERO;
        }
        return DiscountPolicy.legal(reduction, price);
    }

    /**
     * 没生效时，直接说清楚差多少钱。
     *
     * <p>「未满 ￥100.00（当前 ￥80.00）」比「未达门槛」有用得多：
     * 前者能让运营一眼算出「再买 20 元就能用」，后者什么信息都没给。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @return 跳过原因
     */
    @Override
    public String unavailableReason(BigDecimal price, OrderContext context) {
        return "未满 " + com.patterns.util.Money.yuan(threshold)
                + "（当前 " + com.patterns.util.Money.yuan(price) + "）";
    }
}
