package com.patterns.behavioral.chainofresponsibility.discount;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠券规则：例如"券 SAVE30，满 100 减 30"。
 *
 * <p><b>本次重构把"有没有券"和"券长什么样"分开了。</b>
 * 原项目只有 {@code Order.hasCoupon()} 一个 boolean，
 * 面额 {@code 30} 硬编码在 {@code CouponDiscountHandler} 里。
 * 后果是：一个用户同时有"满 100 减 30"和"满 300 减 80"两张券时，
 * 系统<b>无法表达</b>"这一单用的是哪一张"——这不是代码风格问题，是建模缺了一整块。
 *
 * <p>本实现把券建模成"一张带门槛和面额的规则"：
 * <pre>
 *   new CouponRule("SAVE30", "30", "100")   // 满 100 减 30
 *   new CouponRule("SAVE80", "80", "300")   // 满 300 减 80
 * </pre>
 * 订单上只保留"本单是否用了券"这个事实，具体的券由管道持有哪些规则来决定。
 *
 * <p><b>为什么"未达门槛"要单独给一条 reason？</b>
 * 因为这是电商客诉排名前三的问题："我明明有券，为什么没抵扣？"
 * 有了 {@code reason}，客服系统能直接把"券 SAVE30 需满 ￥100.00 可用，当前应付 ￥85.00"
 * 展示给用户，一次沟通就能解决；没有的话只能升级到技术排查。
 *
 * @author design-patterns-pro
 */
public final class CouponRule implements DiscountRule {

    /** 券码，用于在账单和日志里指认是哪一张券。 */
    private final String code;

    /** 券面额。 */
    private final BigDecimal faceValue;

    /** 使用门槛（含）。 */
    private final BigDecimal minSpend;

    /**
     * @param code      券码，不能为空白
     * @param faceValue 面额，必须为正数
     * @param minSpend  使用门槛，必须为正数
     */
    public CouponRule(String code, String faceValue, String minSpend) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("券码不能为空：账单和日志都要靠它指认具体是哪张券");
        }
        BigDecimal face = Money.of(faceValue);
        BigDecimal min = Money.of(minSpend);
        if (face.signum() <= 0) {
            throw new IllegalArgumentException("券面额必须为正数，实际传入：" + face);
        }
        if (min.signum() <= 0) {
            throw new IllegalArgumentException("券使用门槛必须为正数，实际传入：" + min);
        }
        this.code = code;
        this.faceValue = face.setScale(2, RoundingMode.HALF_UP);
        this.minSpend = min.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "优惠券 " + code + "（满 " + Money.plain(minSpend) + " 减 " + Money.plain(faceValue) + "）";
    }

    @Override
    public Outcome evaluate(DiscountOrder order) {
        if (!order.hasCoupon()) {
            return Outcome.skip("本单未勾选优惠券");
        }
        BigDecimal payable = order.payableAmount();
        if (payable.compareTo(minSpend) < 0) {
            return Outcome.skip("券 " + code + " 需满 " + Money.yuan(minSpend)
                    + " 可用，当前应付 " + Money.yuan(payable));
        }
        return Outcome.hit(faceValue, "券 " + code + " 生效，减 " + Money.yuan(faceValue));
    }
}
