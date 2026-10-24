package com.patterns.behavioral.chainofresponsibility.discount;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 会员折扣：按<b>当前应付金额</b>打一个百分比折扣，例如 9 折。
 *
 * <p><b>为什么它必须排在最后？</b>
 * 因为它算的是"百分比"，作用基数越大，减掉的钱越多：
 * <pre>
 *   先会员后满减：1000 × 0.9 = 900，再满 200 减 50 → 850
 *   先满减后会员：1000 - 50 = 950，再 9 折        → 855
 * </pre>
 * 两种顺序差 5 元。<b>顺序不是实现细节，是钱。</b>
 * 所以管道必须把顺序写死在组装处，而且只能有一处——
 * 原项目用 {@code getPriority()} 把顺序拆散在三个类里，
 * 想回答"为什么顺序是这样"就得打开三个文件比对数字。
 *
 * <p><b>为什么不直接返回"打几折"而是返回"减多少钱"？</b>
 * 因为管道里所有规则统一返回"减多少"，后续的 {@link DiscountOrder#withDiscount}
 * 只需要一种形态的输入。如果每条规则各返回各的（折扣率、减额、赠品……），
 * 管道里就会长出一堆 {@code instanceof} 判断。
 * <b>把差异收敛在规则内部，把统一留给管道。</b>
 *
 * @author design-patterns-pro
 */
public final class MemberRule implements DiscountRule {

    /** 折扣率，例如 0.90 表示 9 折。 */
    private final BigDecimal rate;

    /**
     * @param rate 折扣率，必须大于 0 且小于 1（等于 1 表示不打折，没有意义）
     */
    public MemberRule(String rate) {
        BigDecimal value = Money.of(rate);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "会员折扣率必须在 (0, 1) 之间，例如 0.90 表示 9 折，实际传入：" + value);
        }
        this.rate = value;
    }

    @Override
    public String name() {
        return "会员折扣（" + Money.plain(rate.multiply(BigDecimal.TEN)) + " 折）";
    }

    @Override
    public Outcome evaluate(DiscountOrder order) {
        if (!order.member()) {
            return Outcome.skip("非会员");
        }
        BigDecimal payable = order.payableAmount();
        // 减掉的金额由"当前应付 × (1 - 折扣率)"算出来，而不是再乘一次折扣率，
        // 因为管道只认"减多少"这一种表达。
        BigDecimal off = payable.multiply(BigDecimal.ONE.subtract(rate))
                .setScale(2, RoundingMode.HALF_UP);
        if (off.signum() <= 0) {
            return Outcome.skip("当前应付 " + Money.yuan(payable) + " 按 " + name() + " 计算不足 0.01 元");
        }
        return Outcome.hit(off,
                "当前应付 " + Money.yuan(payable) + " 按 " + Money.plain(rate) + " 计算，减 " + Money.yuan(off));
    }
}
