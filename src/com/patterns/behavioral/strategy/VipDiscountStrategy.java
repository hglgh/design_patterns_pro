package com.patterns.behavioral.strategy;

import java.math.BigDecimal;

/**
 * 具体策略：VIP 折扣 = 打折 + <b>封顶</b>。
 *
 * <p><b>为什么这个类必须独立存在，而不是复用 {@link PercentageDiscountStrategy}？</b>
 *
 * <p>原项目的 VIP 策略是这样的：
 * <pre>
 *   public class VipDiscountStrategy implements DiscountStrategy {
 *       public double calculate(double originalPrice) {
 *           return originalPrice * 0.8;   // VIP 打八折
 *       }
 *   }
 * </pre>
 * 它和 {@code PercentageDiscountStrategy}（{@code * 0.9}）<b>只差一个数字</b>。
 * 这会让读者产生一个非常合理的质疑：
 * <blockquote>
 *   「既然 {@code PercentageDiscountStrategy} 已经能表达任意折扣率了，
 *   为什么还要单独写一个 VIP 类？这不是重复代码吗？
 *   直接 {@code new PercentageDiscountStrategy(new BigDecimal("0.8"))} 不就行了？」
 * </blockquote>
 *
 * <p><b>这个质疑是对的。</b>如果 VIP 折扣真的只是"换个数字的打折"，那它就该被删掉。
 * 判断一个类是否值得独立存在，标准是：<b>它是否承载了其他类无法表达的业务规则。</b>
 *
 * <p>现实中 VIP 折扣<b>确实有独立规则</b>——最典型的就是<b>封顶（cap）</b>：
 * <blockquote>
 *   「VIP 八折，但单笔订单最多优惠 200 元。」
 * </blockquote>
 * 这条规则在电商里非常普遍，原因是<b>风控</b>：
 * 一台 20000 元的相机打八折要优惠 4000 元，
 * 如果 VIP 等级是靠"消费满 1 万"就能拿到的，那用户完全可能
 * 「充 1 万成为 VIP → 买 2 万商品省 4000 → 退款」，套利 4000 元。
 * 封顶把单笔损失限制在可控范围内。
 *
 * <p>加上封顶之后，VIP 策略就有了<b>两条</b>规则（折扣率 + 封顶金额），
 * {@link PercentageDiscountStrategy} 无法表达，独立成类才是合理的。
 *
 * <p><b>这正是"不要为了套模式而增加类"的反面教材</b>：
 * 原项目的 VIP 类是多余的（应该删），重构后的 VIP 类是必要的（有独立规则）。
 * 类该不该存在，看的不是"模式需要几个角色"，而是"业务上有几条不同的规则"。
 *
 * @author design-patterns-pro
 * @see PercentageDiscountStrategy 不带封顶的纯打折策略
 */
public class VipDiscountStrategy implements DiscountStrategy {

    /** VIP 折扣率，例如 0.80 表示八折。 */
    private final BigDecimal rate;

    /**
     * 单笔订单最多可优惠的金额（封顶）。
     *
     * <p>用 {@code null} 表示"不封顶"，而不是用 {@code BigDecimal.MAX_VALUE} 之类的哨兵值——
     * 因为 {@code null} 在 {@link #name()} 里能直接判断出"该不该显示封顶文案"，
     * 而哨兵值会打印出"最多优惠 999999... 元"这种荒谬的文字。
     */
    private final BigDecimal maxDiscount;

    /**
     * 创建不封顶的 VIP 折扣。
     *
     * @param rate 折扣率，必须在 (0, 1) 区间内
     */
    public VipDiscountStrategy(BigDecimal rate) {
        this(rate, null);
    }

    /**
     * 创建带封顶的 VIP 折扣。
     *
     * @param rate        折扣率，必须在 (0, 1) 区间内
     * @param maxDiscount 单笔最多优惠金额，传 {@code null} 表示不封顶
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public VipDiscountStrategy(BigDecimal rate, BigDecimal maxDiscount) {
        if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            // 注意上界是"小于 1"而不是"小于等于 1"：VIP 折扣率等于 1 意味着"VIP 没有任何优惠"，
            // 这在业务上是配置错误（既然没优惠，为什么要给这个用户挂 VIP 策略？），应该快速失败。
            throw new IllegalArgumentException(
                    "VIP 折扣率必须在 (0, 1) 区间内，实际传入：" + rate);
        }
        if (maxDiscount != null && maxDiscount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "封顶金额必须为正数，或传 null 表示不封顶，实际传入：" + maxDiscount);
        }
        this.rate = rate;
        this.maxDiscount = maxDiscount;
    }

    @Override
    public String name() {
        BigDecimal zhe = rate.multiply(BigDecimal.TEN).stripTrailingZeros();
        String cap = (maxDiscount == null)
                ? "不封顶"
                : "最多优惠 " + maxDiscount.stripTrailingZeros().toPlainString() + " 元";
        return "VIP（" + zhe.toPlainString() + " 折，" + cap + "）";
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);

        BigDecimal discounted = originalPrice.multiply(rate);
        if (maxDiscount == null) {
            return DiscountStrategy.normalize(discounted);
        }

        // 实际优惠额 = 原价 - 折后价
        BigDecimal actualDiscount = originalPrice.subtract(discounted);

        // 优惠额超过封顶 → 按封顶反推折后价：折后价 = 原价 - 封顶额
        if (actualDiscount.compareTo(maxDiscount) > 0) {
            return DiscountStrategy.normalize(originalPrice.subtract(maxDiscount));
        }
        return DiscountStrategy.normalize(discounted);
    }

    /**
     * 计算在给定原价下，封顶是否生效。
     *
     * <p><b>为什么单独提供这个方法？</b>
     * 因为"封顶生效的临界价格"是一个业务上很关心的数字：
     * 当原价超过 {@code maxDiscount / (1 - rate)} 时，VIP 折扣就"不再划算"了
     * （优惠额固定为封顶值，相当于折扣率越来越高）。
     * 客服和运营需要知道这个临界点，才能向用户解释"为什么我这单只减了 200"。
     *
     * <p>例如 rate=0.8、cap=200 时，临界价 = 200 / 0.2 = 1000 元：
     * 1000 元以下按八折走（优惠不足 200）；1000 元以上优惠额固定为 200。
     *
     * @param originalPrice 原价
     * @return 封顶是否生效
     */
    public boolean isCapped(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        if (maxDiscount == null) {
            return false;
        }
        BigDecimal actualDiscount = originalPrice.subtract(originalPrice.multiply(rate));
        return actualDiscount.compareTo(maxDiscount) > 0;
    }

    @Override
    public String toString() {
        return "VipDiscountStrategy[" + name() + "]";
    }
}
