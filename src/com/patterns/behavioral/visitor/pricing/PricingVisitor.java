package com.patterns.behavioral.visitor.pricing;

/**
 * 计价访问者（具体访问者，ConcreteVisitor）：把每件商品翻译成一行账。
 *
 * <p><b>它替代了原项目的 PriceCalculatorVisitor。</b>
 * 对照一下两者的差别：</p>
 * <table border="1">
 *   <caption>原实现与本实现对照</caption>
 *   <tr><th>维度</th><th>原 PriceCalculatorVisitor</th><th>本实现</th></tr>
 *   <tr>
 *     <td>结果如何带出</td>
 *     <td>void + 内部 {@code totalPrice} 字段累加</td>
 *     <td>返回 {@link LineResult}，聚合交给调用方</td>
 *   </tr>
 *   <tr>
 *     <td>能否复用</td>
 *     <td>不能，第二次 accept 结果翻倍（探针 V3）</td>
 *     <td>能，类里没有任何字段</td>
 *   </tr>
 *   <tr>
 *     <td>买一赠一公式</td>
 *     <td>写在 visit 里，和打印访问者重复（探针 V5）</td>
 *     <td>调用 {@link BuyOneGetOneItem#billableUnits()}</td>
 *   </tr>
 *   <tr>
 *     <td>金额类型</td>
 *     <td>double，输出 46.400000000000006（探针 V6）</td>
 *     <td>{@link Money}，输出 46.40</td>
 *   </tr>
 *   <tr>
 *     <td>是否打印</td>
 *     <td>每个分支都 {@code System.out.println("计算…")}</td>
 *     <td>不打印，返回值交给调用方</td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么这里不累加？</b>
 * 因为"求和"只对"最终要一个总数"的场景成立。
 * 一旦调用方想要的是"前 3 行 VIP 商品免单""按品类分组小计"
 * 或者"导出明细给财务系统"，累加器就成了障碍。
 * 返回明细之后，这些都只是对 {@code List<LineResult>} 的再加工。</p>
 */
public final class PricingVisitor implements CartVisitor<LineResult> {

    @Override
    public LineResult visit(RegularItem item) {
        return new LineResult(
                "原价",
                item.name(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal(),
                Money.ZERO);
    }

    @Override
    public LineResult visit(DiscountItem item) {
        return new LineResult(
                item.rateLabel(),
                item.name(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal(),
                item.discountAmount());
    }

    @Override
    public LineResult visit(BuyOneGetOneItem item) {
        return new LineResult(
                "买一赠一",
                item.name(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal(),
                item.discountAmount());
    }
}
