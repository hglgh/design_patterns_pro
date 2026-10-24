package com.patterns.behavioral.visitor.pricing;

/**
 * 购物车访问者接口（Visitor）。
 *
 * <p><b>它和原项目最刺眼的差别：没有 {@code getTotalPrice()}。</b>
 * 原接口：
 * <pre>{@code
 * public interface ShoppingCartVisitor {
 *     void visit(NormalItem item);
 *     void visit(DiscountItem item);
 *     void visit(BogofItem item);
 *     double getTotalPrice();   // ← 打印访问者被迫 return 0
 * }
 * }</pre>
 * 一个"能否算出总价"的能力被写进了所有访问者都必须实现的接口，
 * 于是 {@code PrintListVisitor} 只能返回 0（探针 V4）。
 * 这里的接口只声明三个 visit，"总价"由 {@link PricingVisitor}
 * 的返回值加上 {@link CartSummary} 的聚合来表达 ——
 * 能力跟着实现走，不跟着接口走。</p>
 *
 * <p><b>为什么不用 void + 内部累加？</b>
 * 原项目的 {@code PriceCalculatorVisitor} 用实例字段
 * {@code totalPrice} 累加，同一个对象对同一辆购物车跑两次，
 * 总价从 99.0 变成 198.0（探针 V3）。返回值的写法让访问者
 * 必须无状态：它没有地方存中间结果，也就没有"用坏了"的状态。</p>
 *
 * @param <R> 该访问者对所有商品类型统一产出的结果类型
 * @see CartItem
 */
public interface CartVisitor<R> {

    /**
     * 访问原价商品。
     *
     * @param item 原价商品，不为 null
     * @return 访问结果
     */
    R visit(RegularItem item);

    /**
     * 访问折扣商品。
     *
     * @param item 折扣商品，不为 null
     * @return 访问结果
     */
    R visit(DiscountItem item);

    /**
     * 访问买一赠一商品。
     *
     * @param item 买一赠一商品，不为 null
     * @return 访问结果
     */
    R visit(BuyOneGetOneItem item);
}
