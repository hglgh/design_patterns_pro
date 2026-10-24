package com.patterns.behavioral.visitor.pricing;

/**
 * 一行计价结果（不可变值对象）。
 *
 * <p>它是 {@link PricingVisitor} 的产出，也是 {@link CartSummary} 的输入。
 * 把"一行账"抽成类型而不是直接拼命符串，好处是：</p>
 * <ul>
 *   <li>金额仍然是 {@link Money}，可以继续参与计算（求和、比大小），
 *       不会因为提前格式化而丢失精度或变成不可解析的文本；</li>
 *   <li>同一份结果既能渲染成小票（{@link ReceiptVisitor} 的职责），
 *       也能导出成 JSON/CSV，而不用重新访问一遍购物车；</li>
 *   <li>测试里可以直接断言 {@code subtotal().format()} 等于 "46.40"，
 *       而不是在控制台输出里做字符串匹配。</li>
 * </ul>
 *
 * <p>{@code label} 和 {@code name} 分开，是为了让"计价方式"
 * 和"商品名"各自可独立变化：折扣商品的 label 是"8 折"（随折扣率变），
 * name 是商品名；买一赠一的 label 固定是"买一赠一"，
 * 但优惠件数体现在 {@code quantity} 与 {@code subtotal} 上。</p>
 *
 * @param label    计价方式标签，例如"原价""8 折""买一赠一"
 * @param name     商品名
 * @param quantity 购买件数
 * @param unitPrice 单价
 * @param subtotal 本行应付金额
 * @param discount 本行优惠金额，没有优惠时为 {@link Money#ZERO}
 */
public record LineResult(String label,
                         String name,
                         int quantity,
                         Money unitPrice,
                         Money subtotal,
                         Money discount) {

    /**
     * 紧凑构造器：拒绝 null 与负数，保证下游聚合不会出现脏数据。
     */
    public LineResult {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label 不能为空白");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空白");
        }
        if (unitPrice == null || subtotal == null || discount == null) {
            throw new IllegalArgumentException("金额字段不能为 null");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity 至少为 1，实际 " + quantity);
        }
        if (unitPrice.isNegative() || subtotal.isNegative() || discount.isNegative()) {
            throw new IllegalArgumentException("金额不能为负");
        }
    }

    /**
     * 本行是否享受了优惠。
     *
     * @return 优惠金额大于 0 时返回 true
     */
    public boolean hasDiscount() {
        return !discount.isZero();
    }
}
