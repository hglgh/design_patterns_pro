package com.patterns.behavioral.visitor.pricing;

/**
 * 小票渲染访问者（具体访问者，ConcreteVisitor）。
 *
 * <p><b>它替代了原项目的 PrintListVisitor，并修掉了它最尴尬的一处。</b>
 * 原实现因为接口上有个 {@code getTotalPrice()}，只能写
 * {@code return 0;}（探针 V4）。这里返回的是 {@code String} ——
 * 一行小票文本，语义自洽，不需要实现任何与自己无关的方法。</p>
 *
 * <p><b>它和 PricingVisitor 的分工是什么？</b>
 * 两者都会"看"同一件商品，但关心的东西不同：
 * </p>
 * <ul>
 *   <li>{@link PricingVisitor} 关心数字：这行多少钱、省了多少；</li>
 *   <li>{@link ReceiptVisitor} 关心排版：这一行打印成什么样。</li>
 * </ul>
 * <p>把"算"和"排版"分开之后，改小票格式（加对齐、加分隔线、
 * 换成 JSON）不需要动任何计价逻辑，也不会因为格式化而丢掉
 * {@link Money} 的精度。原项目把两者混在一个类里，
 * 而且用 double 直接拼字符串（探针 V6 的
 * {@code 46.400000000000006} 就是这样漏到用户面前的）。</p>
 *
 * <p><b>为什么不用 {@code String.format("%-20s")} 对齐？</b>
 * 中文字符在等宽字体里占两个字符宽度，而 {@code String.length()}
 * 只算一个，按长度补空格会错位。用 {@code " | "} 分隔在任何字体、
 * 任何语言下都稳定，而且输出逐字节可复现（README 的哈希校验依赖这点）。</p>
 */
public final class ReceiptVisitor implements CartVisitor<String> {

    @Override
    public String visit(RegularItem item) {
        return line(item.name(), "", item.quantity(), item.unitPrice(), item.subtotal(), null);
    }

    @Override
    public String visit(DiscountItem item) {
        return line(item.name(), item.rateLabel(), item.quantity(),
                item.unitPrice(), item.subtotal(), item.discountAmount());
    }

    @Override
    public String visit(BuyOneGetOneItem item) {
        return line(item.name(), "买一赠一", item.quantity(),
                item.unitPrice(), item.subtotal(), item.discountAmount());
    }

    /**
     * 拼一行小票。
     *
     * @param name      商品名
     * @param label     计价标签，无标签时传空串
     * @param quantity  件数
     * @param unitPrice 单价
     * @param subtotal  小计
     * @param discount  优惠金额，null 表示该行没有优惠、不输出优惠段
     * @return 形如 "《设计模式》(8 折) | ×1 | 单价 58.00 | 小计 46.40 | 省 11.60"
     */
    private static String line(String name, String label, int quantity,
                               Money unitPrice, Money subtotal, Money discount) {
        StringBuilder text = new StringBuilder(96);
        text.append(name);
        if (!label.isEmpty()) {
            text.append('(').append(label).append(')');
        }
        text.append(" | ×").append(quantity);
        text.append(" | 单价 ").append(unitPrice.format());
        text.append(" | 小计 ").append(subtotal.format());
        if (discount != null && !discount.isZero()) {
            text.append(" | 省 ").append(discount.format());
        }
        return text.toString();
    }
}
