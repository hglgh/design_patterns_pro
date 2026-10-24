package com.patterns.behavioral.visitor.pricing;

import java.math.BigDecimal;

/**
 * 折扣商品（具体元素，ConcreteElement）。
 *
 * <p><b>打折的乘法顺序是有讲究的。</b>
 * 本类用的是「(单价 × 数量) × 折扣率」，只做一次舍入。
 * 如果写成「(单价 × 折扣率) × 数量」，那么单价先被舍入一次，
 * 误差再乘以数量放大。举例：单价 3.33 元、打 8 折、买 10 件：</p>
 * <ul>
 *   <li>先乘数量：3330 分 × 0.8 = 2664 分 = 26.64 元（精确）；</li>
 *   <li>先打折：333 分 × 0.8 = 266.4 → 266 分，再 ×10 = 2660 分 = 26.60 元，
 *       白白少收 4 分，而且件数越多差得越远。</li>
 * </ul>
 * <p>这类"舍入顺序"问题在 double 时代根本看不出来（因为到处都错），
 * 换成整数分之后才变得必须显式决定。</p>
 *
 * <p><b>为什么 discountAmount() 不在 CartItem 接口上？</b>
 * 见 {@link CartItem} 的说明：原价商品没有折扣，
 * 强行把方法提到接口上，就等于强迫它返回一个假值。</p>
 *
 * @param name      商品名，非空白
 * @param unitPrice 单价，非负
 * @param quantity  数量，至少 1
 * @param rate      折扣率，必须在 (0, 1] 之间
 */
public record DiscountItem(String name, Money unitPrice, int quantity, BigDecimal rate)
        implements CartItem {

    /**
     * 紧凑构造器：折扣率必须落在 (0, 1]。
     *
     * @throws IllegalArgumentException 任一参数非法
     */
    public DiscountItem {
        name = PricingText.requireName(name);
        unitPrice = PricingText.requirePrice(unitPrice);
        quantity = PricingText.requireQuantity(quantity);
        rate = PricingText.requireRate(rate);
    }

    /**
     * 折前金额（单价 × 数量）。
     *
     * @return 未打折时应付金额
     */
    public Money beforeDiscount() {
        return unitPrice.multiply(quantity);
    }

    /**
     * 折后应付金额。先乘数量再打折，全程只有一次舍入。
     *
     * @return 应付金额
     */
    @Override
    public Money subtotal() {
        return beforeDiscount().times(rate);
    }

    /**
     * 省下的金额。因为 {@code subtotal} 是四舍五入的结果，
     * 这个差值一定是非负的。
     *
     * @return 优惠金额
     */
    public Money discountAmount() {
        return beforeDiscount().minus(subtotal());
    }

    /**
     * 折扣的中文表示，例如 0.8 → "8 折"，0.85 → "8.5 折"。
     *
     * <p>{@code stripTrailingZeros} 让 0.80 显示成"8 折"而不是"8.0 折"，
     * 但 0.85 仍然保留小数位。</p>
     *
     * @return 形如 "8 折" 的标签
     */
    public String rateLabel() {
        return rate.movePointRight(1).stripTrailingZeros().toPlainString() + " 折";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(CartVisitor<R> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor 不能为 null");
        }
        return visitor.visit(this);
    }
}
