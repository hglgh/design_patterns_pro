package com.patterns.behavioral.strategy;

import java.math.BigDecimal;

/**
 * 具体策略：按比例打折。例如九折、七五折、五折。
 *
 * <p><b>本次重构的改动：折扣率从硬编码变成构造参数。</b>
 * 原项目写死了 {@code return originalPrice * 0.9;}，于是"九折"这个类只能打九折。
 * 想做七折就得再写一个 {@code SeventyPercentStrategy}——这就是类爆炸的开端。
 *
 * <p>参数化之后：
 * <pre>
 *   new PercentageDiscountStrategy(new BigDecimal("0.90"))   // 九折
 *   new PercentageDiscountStrategy(new BigDecimal("0.75"))   // 七五折
 *   new PercentageDiscountStrategy(new BigDecimal("0.50"))   // 五折清仓
 * </pre>
 *
 * <p><b>为什么折扣率用 {@link BigDecimal} 而不用 {@code double}？</b>
 * 两个理由：
 * <ol>
 *   <li>{@code BigDecimal.multiply(double)} 这个方法<b>根本不存在</b>，
 *       必须先把 double 转成 BigDecimal，而 {@code new BigDecimal(0.9)} 会得到
 *       {@code 0.90000000000000002220446049250313...}，误差被永久固化。</li>
 *   <li>类型一致性：整个计算链路上都是 BigDecimal，
 *       一旦中间出现一个 double，精度就在那里丢失，而且<b>丢得无声无息</b>。</li>
 * </ol>
 *
 * <p><b>这个类和 {@link VipDiscountStrategy} 有什么区别？</b>
 * 看原项目的话，二者只差一个数字（0.9 vs 0.8），会让人怀疑"为什么要写两个类"。
 * 本次重构给 VIP 策略加了<b>封顶金额</b>（最多减 X 元）这一真实业务规则，
 * 两者就有了本质区别。如果 VIP 策略真的只是"换个数字的打折"，
 * 那正确做法确实是<b>删掉它</b>，直接 {@code new PercentageDiscountStrategy(new BigDecimal("0.8"))}——
 * 这一点在 README 第 8 节"过度设计的判断标准"里详细讨论。
 *
 * @author design-patterns-pro
 * @see VipDiscountStrategy 带封顶的折扣策略
 */
public class PercentageDiscountStrategy implements DiscountStrategy {

    /** 折扣率，例如 0.90 表示九折。取值范围 (0, 1]。 */
    private final BigDecimal rate;

    /**
     * @param rate 折扣率，必须在 (0, 1] 区间内
     * @throws IllegalArgumentException 折扣率越界时抛出
     */
    public PercentageDiscountStrategy(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("折扣率不能为 null");
        }
        // 为什么下界是"大于 0"而不是"大于等于 0"？
        // rate = 0 意味着"全部免费"，这在业务上是"赠品/全额券"而不是"打折"，
        // 应该用一个专门的策略类表达，而不是让打折策略退化到 rate=0。
        // 上界为什么允许等于 1？rate = 1 表示"不打折"，是合法的边界（虽然更应该用 NoDiscountStrategy）。
        if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "折扣率必须在 (0, 1] 区间内，实际传入：" + rate
                            + "。若想表达'不打折'请使用 NoDiscountStrategy");
        }
        this.rate = rate;
    }

    @Override
    public String name() {
        // 把 0.90 显示成"九折"、0.75 显示成"七五折"，这是运营和用户的语言。
        // 做法：rate × 10 = 几折。0.90 × 10 = 9.0 → "9 折"；0.75 × 10 = 7.5 → "7.5 折"。
        BigDecimal zhe = rate.multiply(BigDecimal.TEN).stripTrailingZeros();
        return "打折（" + zhe.toPlainString() + " 折，折扣率 " + rate.toPlainString() + "）";
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        return DiscountStrategy.normalize(originalPrice.multiply(rate));
    }

    /** 暴露折扣率，方便调用方计算"省了多少钱"（见 Client.demo2）。 */
    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public String toString() {
        return "PercentageDiscountStrategy[" + name() + "]";
    }
}
