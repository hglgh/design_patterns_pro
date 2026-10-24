package com.patterns.behavioral.visitor.pricing;

/**
 * 购物车商品（元素接口，Element）。
 *
 * <p><b>接口上只留"每个商品都真的具备"的能力。</b>
 * 这正是为了避开原项目最典型的设计缺陷：
 * 原 {@code ShoppingCartVisitor} 接口里多了一个
 * {@code double getTotalPrice()}，导致打印访问者必须写
 * {@code return 0;}（探针 V4）。同理，这里也不会在
 * {@code CartItem} 上声明 {@code discountAmount()} ——
 * 原价商品没有折扣，让它的实现返回 0 同样是"为了满足接口而编造返回值"。</p>
 *
 * <p>所以：</p>
 * <ul>
 *   <li>{@code name/unitPrice/quantity/subtotal} 三个实现都真正具备，
 *       放进接口；</li>
 *   <li>{@code discountAmount} 只有折扣商品和买一赠一商品才有，
 *       各自在自己的类里实现，接口不声明。访问者需要时按具体类型调用。</li>
 * </ul>
 *
 * <p><b>为什么要 subtotal()？</b>
 * 因为"这件商品按规则应付多少"属于商品自身的定价知识，
 * 不是某种"操作"。把公式留在元素里，才能让
 * {@code PricingVisitor} 和 {@code ReceiptVisitor}
 * 共用一个结果 —— 原项目的买一赠一公式在两个访问者里各写了一遍（探针 V5），
 * 就是因为元素自己不知道该怎么算。</p>
 *
 * <p>访问者拿走的是"操作"（怎么展示、怎么汇总、怎么打折审计），
 * 不是"元素自身的换算规则"。这条界线是访问者模式最容易被搞混的地方。</p>
 */
public sealed interface CartItem permits RegularItem, DiscountItem, BuyOneGetOneItem {

    /**
     * 商品名，非空白。
     *
     * @return 商品名
     */
    String name();

    /**
     * 单价。
     *
     * @return 单价，非负
     */
    Money unitPrice();

    /**
     * 购买数量（件）。
     *
     * @return 数量，至少 1
     */
    int quantity();

    /**
     * 按本商品的计价规则应支付的金额。
     *
     * <p>原价商品是「单价 × 数量」；
     * 折扣商品是「单价 × 数量 × 折扣率」；
     * 买一赠一是「单价 × 应付件数」。三种规则各自封装在元素内部。</p>
     *
     * @return 应付金额
     */
    Money subtotal();

    /**
     * 接受访问者，完成双重分派的第二跳。
     *
     * @param visitor 访问者，不能为 null
     * @param <R>     访问结果类型
     * @return 访问者针对本商品给出的结果
     */
    <R> R accept(CartVisitor<R> visitor);
}
