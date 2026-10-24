package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 打折策略：<b>按比例减免</b>，例如「九折」。
 *
 * <p>参数只有一个：折扣率。本项目约定用「实付比例」而不是「优惠比例」：
 * {@code 0.9} 表示付九成（九折），{@code 0.75} 表示付七五成。
 * 这么选的理由是：<b>它与日常说法一一对应</b>。「九折」对应 {@code 0.9}，
 * 「八五折」对应 {@code 0.85}，运营人员不容易填错。
 * 如果反过来用「优惠 10%」，代码里就会出现 {@code price * (1 - 0.1)}，
 * 多一步减法就多一次解释成本。</p>
 *
 * <p><b>为什么构造时要求 {@code 0 < rate < 1}？</b></p>
 * 三条边界的含义：
 * <ul>
 *   <li>{@code rate = 1}：等于没打折，那就不该出现在链上 —— 让它构造失败，
 *       比让它在链上占一个位置然后什么都不做要好；</li>
 *   <li>{@code rate = 0}：免费送，这属于「赠品」而不是「折扣」，
 *       业务上需要走完全不同的风控流程；</li>
 *   <li>{@code rate > 1} 或 {@code rate < 0}：纯粹是配置错误，
 *       会让价格变高或变成负数。</li>
 * </ul>
 *
 * <p><b>为什么减免额要四舍五入到分，而不是向下取整？</b></p>
 * 这是需要明确写下来的业务决定，两种做法都有人在用：
 * <ul>
 *   <li><b>四舍五入</b>：{@code 99.99 × 0.9} 减免 {@code 10.00}，实付 {@code 89.99}。
 *       对用户有利，且符合「金额计算四舍五入」的通行会计惯例；</li>
 *   <li><b>向下取整</b>：减免 {@code 9.99}，实付 {@code 90.00}。多收用户 1 分。</li>
 * </ul>
 * 本项目选四舍五入，并在 {@link DiscountPolicy#legal} 里统一执行。
 * <b>重要的不是选哪一种，而是把它写下来并全系统一致</b> ——
 * 如果 A 策略四舍五入、B 策略向下取整，对账时就会出现「每一单都差几分」
 * 且永远查不出原因的账目。</p>
 *
 * @param name 策略名称，例如 {@code "九折"}
 * @param rate 实付比例，必须严格在 {@code (0, 1)} 之间
 * @author design-patterns-pro
 */
public record PercentagePolicy(String name, BigDecimal rate) implements DiscountPolicy {

    /**
     * 校验参数。
     *
     * @throws IllegalArgumentException 名称为空、比例越界
     */
    public PercentagePolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("折扣策略必须有名称");
        }
        if (rate == null) {
            throw new IllegalArgumentException("折扣比例不能为 null");
        }
        if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "折扣比例必须在 (0, 1) 之间（0.9 表示九折）：" + rate.toPlainString());
        }
    }

    /**
     * 按比例计算减免额。
     *
     * <p>算式是 {@code price × (1 - rate)}。这里的减法用
     * {@link BigDecimal#subtract}，结果精确（{@code 1 - 0.9 = 0.1}），
     * 不会出现 {@code double} 下 {@code 1 - 0.9 = 0.09999999999999998} 的误差。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文（折扣不看上下文，但接口要求传）
     * @return 减免金额
     */
    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        DiscountPolicy.requireValidPrice(price);
        if (price.signum() == 0) {
            // 价格已经是 0，再打折也只能减 0。提前返回可以避免无意义的乘法，
            // 也让审计轨迹里的「减免 0」有明确的原因。
            return ZERO;
        }
        BigDecimal reduction = price.multiply(BigDecimal.ONE.subtract(rate));
        return DiscountPolicy.legal(reduction, price);
    }
}
