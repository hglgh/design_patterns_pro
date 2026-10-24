package com.patterns.behavioral.chainofresponsibility.discount;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 满减规则：例如"满 200 减 50"。
 *
 * <p><b>两个改动值得单独说。</b>
 *
 * <p><b>改动一：门槛和减额变成构造参数。</b>
 * 原项目写成 {@code if (order.getAmount() > 200) { ... - 50; }}，
 * 于是"618 满 300 减 80"就需要再写一个类。参数化之后，
 * 一个类能表达任意档位，而且不同档位可以<b>同时存在</b>、互不干扰。
 *
 * <p><b>改动二：{@code >} 改成 {@code >=}（用 {@code compareTo} 表达）。</b>
 * 这是一个真实的差一错误。原项目写的是：
 * <pre>
 *   if (order.getAmount() &gt; 200) {   // 恰好 200 元 → 不满足
 * </pre>
 * 而运营的口径永远是"<b>满</b> 200 减 50"，200 元是含在内的。
 * 于是恰好 200 元的订单一分钱优惠都拿不到，客服每天都会收到这类工单。
 * 本项目用原代码真实跑过这个用例，输出见 README §2.5。
 *
 * <p><b>为什么判断的是 {@code payableAmount} 而不是 {@code originalAmount}？</b>
 * 因为规则是按顺序叠加的，"满 200"应该看用户<b>此刻</b>还要付多少。
 * 如果看原价，就会出现"原价 250、已经被前一条规则减到 180，却仍然能享受满 200 减 50"——
 * 那是重复补贴。
 * <b>"按哪个金额算"是业务规则，不是实现细节，必须写进文档并由业务确认。</b>
 *
 * @author design-patterns-pro
 */
public final class FullReductionRule implements DiscountRule {

    /** 触发满减的门槛（含）。 */
    private final BigDecimal threshold;

    /** 达到门槛后减掉的金额。 */
    private final BigDecimal reduction;

    /**
     * @param threshold 门槛金额，必须为正数
     * @param reduction 减免金额，必须为正数且不大于门槛
     * @throws IllegalArgumentException 参数不合法
     */
    public FullReductionRule(String threshold, String reduction) {
        BigDecimal limit = Money.of(threshold);
        BigDecimal off = Money.of(reduction);
        if (limit.signum() <= 0) {
            throw new IllegalArgumentException("满减门槛必须为正数，实际传入：" + limit);
        }
        if (off.signum() <= 0) {
            throw new IllegalArgumentException("减免金额必须为正数，实际传入：" + off);
        }
        if (off.compareTo(limit) > 0) {
            throw new IllegalArgumentException(
                    "减免金额(" + off + ")不应大于门槛(" + limit + ")：满 30 减 50 这种配置只会制造纠纷");
        }
        this.threshold = limit.setScale(2, RoundingMode.HALF_UP);
        this.reduction = off.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "满减（满 " + Money.plain(threshold) + " 减 " + Money.plain(reduction) + "）";
    }

    @Override
    public Outcome evaluate(DiscountOrder order) {
        BigDecimal payable = order.payableAmount();
        if (payable.compareTo(threshold) < 0) {
            return Outcome.skip("当前应付 " + Money.yuan(payable) + " 未满 " + Money.yuan(threshold));
        }
        return Outcome.hit(reduction,
                "当前应付 " + Money.yuan(payable) + " 已满 " + Money.yuan(threshold)
                        + "，减 " + Money.yuan(reduction));
    }
}
