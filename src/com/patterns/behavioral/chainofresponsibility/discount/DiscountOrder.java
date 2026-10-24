package com.patterns.behavioral.chainofresponsibility.discount;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一笔订单的折扣计算结果：<b>不可变</b>的订单状态。
 *
 * <p>原项目的 {@code Order} 是一个可变的普通类，{@code setAmount()} 谁都能调：
 * <pre>
 *   // 原项目 CouponDiscountHandler
 *   order.setAmount(order.getAmount() - 30);
 * </pre>
 * 这段代码有三个问题，而且它们会互相放大：
 * <ol>
 *   <li><b>金额可能是 {@code double}</b>，减着减着就差几分钱；</li>
 *   <li><b>没有下限</b>——25 元的订单用 30 元的券，金额直接变成 -5；</li>
 *   <li><b>谁都能改</b>——出了问题只能看到"最终金额"，看不到"是哪一步减过头的"。</li>
 * </ol>
 *
 * <p>改成 record + {@link #withDiscount} 之后，三个问题一起消失：
 * <ul>
 *   <li>金额是 {@link BigDecimal}；</li>
 *   <li>每次扣减都走 {@code min(payableAmount)}，最多减到 0，不可能为负；</li>
 *   <li>每一次扣减都<b>顺手记下"哪条规则减了多少"</b>，最终金额自带一份账单。</li>
 * </ul>
 *
 * <p><b>为什么 {@code appliedRules} 存的是拼好的字符串？</b>
 * 这是本类唯一"妥协"的地方——它不是审计轨迹那样的核心数据，只是给人看的一行账单。
 * 如果哪天真要按规则做统计，就应该换成 {@code List<AppliedRule>} 这样的结构。
 * <b>妥协可以，但要写清楚妥协的边界在哪。</b>
 *
 * @param orderNo        订单号
 * @param originalAmount 原价
 * @param payableAmount  当前应付（每命中一条规则就减少一点）
 * @param member         是否会员
 * @param hasCoupon      本单是否使用了优惠券
 * @param appliedRules   已经生效的规则明细，形如 {@code "满减（满 200 减 50） → -￥50.00"}
 * @author design-patterns-pro
 */
public record DiscountOrder(String orderNo, BigDecimal originalAmount, BigDecimal payableAmount,
                            boolean member, boolean hasCoupon, List<String> appliedRules) {

    public DiscountOrder {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (originalAmount == null || originalAmount.signum() <= 0) {
            throw new IllegalArgumentException("原价必须为正数");
        }
        if (payableAmount == null || payableAmount.signum() < 0) {
            throw new IllegalArgumentException("应付金额不能为负数");
        }
        appliedRules = List.copyOf(appliedRules);
    }

    /**
     * 新建一笔待计算折扣的订单。
     *
     * @param orderNo   订单号
     * @param amount    原价
     * @param member    是否会员
     * @param hasCoupon 是否使用优惠券
     * @return 应付金额等于原价的初始订单
     */
    public static DiscountOrder of(String orderNo, String amount, boolean member, boolean hasCoupon) {
        BigDecimal price = Money.of(amount);
        return new DiscountOrder(orderNo, price, price, member, hasCoupon, List.of());
    }

    /**
     * 应用一条折扣，返回<b>新的订单对象</b>。
     *
     * <p><b>这一行是本类存在的全部理由：</b>
     * <pre>
     *   BigDecimal actual = discount.min(payableAmount);
     * </pre>
     * 原项目的 {@code setAmount(getAmount() - 30)} 会让 25 元的订单变成 -5 元，
     * 然后再减 20 变成 -25 元，最后打印出"最终订单金额: -25.0"。
     * 这不是理论风险，是本项目用原代码真实跑出来的结果（详见 README §2.4）。
     *
     * <p>{@code min} 的语义是"这次最多也只能减到 0"。
     * 它把"券面额大于应付金额"这种配置/业务问题的影响限制在"免单"，
     * 而不是变成"倒找钱"——<b>错误应该被限制在可解释的范围内。</b>
     *
     * @param ruleName 规则名称，会记进账单
     * @param discount 本条规则想减掉的钱
     * @return 扣减后的新订单；本单已经 0 元时原样返回
     */
    public DiscountOrder withDiscount(String ruleName, BigDecimal discount) {
        Objects.requireNonNull(ruleName, "规则名称不能为 null");
        if (discount == null || discount.signum() <= 0) {
            return this;
        }
        BigDecimal actual = discount.min(payableAmount);
        if (actual.signum() <= 0) {
            // 已经 0 元。再记一条"-0.00"只会让账单变长，不会让用户更便宜。
            return this;
        }
        List<String> lines = new ArrayList<>(appliedRules);
        lines.add(ruleName + " → -" + Money.yuan(actual));
        return new DiscountOrder(orderNo, originalAmount,
                payableAmount.subtract(actual).setScale(2, RoundingMode.HALF_UP),
                member, hasCoupon, lines);
    }

    /** 累计优惠了多少。 */
    public BigDecimal totalDiscount() {
        return originalAmount.subtract(payableAmount).setScale(2, RoundingMode.HALF_UP);
    }
}
