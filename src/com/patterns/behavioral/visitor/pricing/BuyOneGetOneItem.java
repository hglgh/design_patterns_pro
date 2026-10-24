package com.patterns.behavioral.visitor.pricing;

/**
 * 买一赠一商品（具体元素，ConcreteElement）。
 *
 * <p><b>它修掉了原项目最隐蔽的一处重复。</b>
 * 原实现里「付几件的钱」这个公式
 * {@code quantity / 2 + quantity % 2} 在两个访问者中各写了一遍：
 * {@code PriceCalculatorVisitor} 第 29 行、
 * {@code PrintListVisitor} 第 23 行（探针 V5）。
 * 两处都得改才算改对，编译器不会提醒。</p>
 *
 * <p>现在公式只有一份：{@link #billableUnits()}。
 * 计算总价的访问者、打印小票的访问者、审计优惠的访问者，
 * 全都调用同一个方法。"买一赠一"这条业务规则从访问者手里
 * 收回到商品自己身上 —— 因为它是商品的定价属性，
 * 而不是某一种"操作"。</p>
 *
 * <p><b>{@code (quantity + 1) / 2} 与原公式等价吗？</b>
 * 等价。原式 {@code quantity / 2 + quantity % 2} 的语义是
 * "向下取整的一半"加上"余数"，也就是向上取整的一半；
 * {@code (quantity + 1) / 2} 正是整数除法下的向上取整。
 * 逐项验证：1→1、2→1、3→2、4→2、5→3、6→3，与原式完全一致。
 * 改写成后者是为了让"向上取整"这个意图直接读得出来。</p>
 *
 * @param name      商品名，非空白
 * @param unitPrice 单价，非负
 * @param quantity  购买数量，至少 1
 */
public record BuyOneGetOneItem(String name, Money unitPrice, int quantity) implements CartItem {

    /**
     * 紧凑构造器。
     *
     * @throws IllegalArgumentException 任一参数非法
     */
    public BuyOneGetOneItem {
        name = PricingText.requireName(name);
        unitPrice = PricingText.requirePrice(unitPrice);
        quantity = PricingText.requireQuantity(quantity);
    }

    /**
     * 需要付费的件数：每两件付一件的钱，奇数件时最后一件单独付费。
     *
     * @return 应付件数
     */
    public int billableUnits() {
        return (quantity + 1) / 2;
    }

    /**
     * 赠送的件数。
     *
     * @return 免费件数
     */
    public int freeUnits() {
        return quantity - billableUnits();
    }

    /**
     * 买一赠一前的金额（按全部件数计）。
     *
     * @return 未优惠时应付金额
     */
    public Money beforeDiscount() {
        return unitPrice.multiply(quantity);
    }

    /**
     * 应付金额 = 单价 × 应付件数。
     *
     * @return 应付金额
     */
    @Override
    public Money subtotal() {
        return unitPrice.multiply(billableUnits());
    }

    /**
     * 省下的金额 = 单价 × 赠送件数。
     *
     * @return 优惠金额
     */
    public Money discountAmount() {
        return unitPrice.multiply(freeUnits());
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
