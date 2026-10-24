package com.patterns.behavioral.visitor.pricing;

/**
 * 原价商品（具体元素，ConcreteElement）。
 *
 * <p>原项目叫 {@code NormalItem}。{@code Normal} 在中文语境里
 * 容易被读成"正常"（暗示其他商品不正常），
 * 这里改成 {@code RegularItem}（常规/原价），语义更中立。</p>
 *
 * <p>它没有 {@code discountAmount()} 方法 —— 这是刻意的。
 * 原价商品不打折，"省了多少钱"对它没有意义，
 * 所以在它身上根本不存在这个方法。访问者需要区分时，
 * 在自己的 {@code visit(RegularItem)} 分支里返回
 * {@link Money#ZERO} 即可：那里的零是"确实没省"这个事实，
 * 而不是为了满足某个接口而编出来的占位值。</p>
 *
 * @param name      商品名，非空白
 * @param unitPrice 单价，非负
 * @param quantity  数量，至少 1
 */
public record RegularItem(String name, Money unitPrice, int quantity) implements CartItem {

    /**
     * 紧凑构造器：三项都要校验。
     *
     * @throws IllegalArgumentException 任一参数非法
     */
    public RegularItem {
        name = PricingText.requireName(name);
        unitPrice = PricingText.requirePrice(unitPrice);
        quantity = PricingText.requireQuantity(quantity);
    }

    /**
     * 按「单价 × 数量」计算应付金额。
     *
     * @return 应付金额
     */
    @Override
    public Money subtotal() {
        return unitPrice.multiply(quantity);
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
