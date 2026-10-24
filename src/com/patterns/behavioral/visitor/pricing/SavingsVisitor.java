package com.patterns.behavioral.visitor.pricing;

/**
 * 优惠审计访问者（具体访问者，ConcreteVisitor）：只关心"省了多少钱"。
 *
 * <p><b>它存在的意义是演示"第三种操作只加一个类"。</b>
 * 运营同学经常需要一张报表："这次促销一共让利多少？"
 * 这个需求不需要商品名、不需要单价、不需要件数，
 * 只要一个金额。于是它是一个 {@code CartVisitor<Money>}，
 * 三个分支分别返回该商品省下的钱。</p>
 *
 * <p><b>{@code visit(RegularItem)} 返回 {@link Money#ZERO} 算不算"被迫实现"？</b>
 * 不算，这是本重构里一个需要说清楚的界线：</p>
 * <ul>
 *   <li>原项目 {@code PrintListVisitor.getTotalPrice()} 返回 0 是<b>错</b>的：
 *       那辆购物车明明要付 108 元，"总价是 0"这个陈述本身是假的，
 *       0 只是一个为了通过编译而编出来的值（探针 V4）。</li>
 *   <li>这里 {@code visit(RegularItem)} 返回 0 是<b>对</b>的：
 *       原价商品确实没有优惠，"省了 0 元"是一个真实成立的事实。</li>
 * </ul>
 * <p>同样的零，一个是谎言，一个是事实。判断标准不是"返回 0 好不好"，
 * 而是"这个返回值在语义上是否成立"。</p>
 *
 * <p><b>它和 PricingVisitor 是否重复？</b>
 * 不重复。PricingVisitor 返回的是整行账（含标签、件数、单价、小计、优惠），
 * 这个访问者只返回一个金额。虽然金额可以从 LineResult 里再取出来，
 * 但那要求调用方先跑一次 PricingVisitor、再遍历结果做过滤 ——
 * 多了一次分配、多了一层间接。当购物车很大、或者商品类型将来变得很多时，
 * "只算需要的"更直接。</p>
 */
public final class SavingsVisitor implements CartVisitor<Money> {

    @Override
    public Money visit(RegularItem item) {
        return Money.ZERO;
    }

    @Override
    public Money visit(DiscountItem item) {
        return item.discountAmount();
    }

    @Override
    public Money visit(BuyOneGetOneItem item) {
        return item.discountAmount();
    }
}
