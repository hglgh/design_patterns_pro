package com.patterns.behavioral.visitor.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 购物车（对象结构，ObjectStructure）。
 *
 * <p>它负责三件事：存放商品、逐个让商品接受访问、把结果折叠成账单。
 * 访问者不负责遍历，商品也不负责遍历 —— 遍历是对象结构的职责。</p>
 *
 * <p><b>add 为什么立即拒绝 null？</b>
 * 原项目的 {@code ShoppingCart.addItem(null)} 能通过，
 * 直到 {@code accept} 遍历到第二件时才抛 NPE，
 * 堆栈指向 {@code ShoppingCart:24}，而这个位置离真正的错误源头
 * （某次 addItem 传了 null）隔了好几行代码（探针 V8）。
 * 在 add 时就拒绝，异常消息可以直接告诉调用方是第几次添加出了问题。</p>
 *
 * <p><b>为什么把 summarize() 放在购物车上，而不是访问者上？</b>
 * 因为"这车要付多少钱"是对整辆车的提问，不是对某件商品的提问。
 * 访问者只回答"这件商品多少钱"（{@link PricingVisitor}），
 * 求和由对象结构完成。原项目把这个语义塞进了
 * {@code ShoppingCartVisitor.getTotalPrice()}，
 * 结果打印访问者被迫返回 0（探针 V4）。</p>
 */
public final class ShoppingCart {

    private final List<CartItem> items = new ArrayList<>();

    /**
     * 加入一件商品。
     *
     * @param item 商品，不能为 null
     * @return this，便于链式调用
     * @throws IllegalArgumentException item 为 null
     */
    public ShoppingCart add(CartItem item) {
        if (item == null) {
            throw new IllegalArgumentException(
                    "第 " + (items.size() + 1) + " 次 add 传入的商品为 null");
        }
        items.add(item);
        return this;
    }

    /**
     * 商品种数（明细行数）。
     *
     * @return 行数
     */
    public int lineCount() {
        return items.size();
    }

    /**
     * 商品总件数。
     *
     * @return 各行数量之和
     */
    public int itemCount() {
        int count = 0;
        for (CartItem item : items) {
            count += item.quantity();
        }
        return count;
    }

    /**
     * 是否为空车。
     *
     * @return 没有任何商品时返回 true
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 只读的商品列表。
     *
     * @return 不可变列表
     */
    public List<CartItem> items() {
        return List.copyOf(items);
    }

    /**
     * 让每件商品接受同一个访问者，按加入顺序收集结果。
     *
     * @param visitor 访问者，不能为 null
     * @param <R>     结果类型
     * @return 与商品顺序一致的不可变结果列表
     */
    public <R> List<R> acceptAll(CartVisitor<R> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        List<R> results = new ArrayList<>(items.size());
        for (CartItem item : items) {
            results.add(item.accept(visitor));
        }
        return List.copyOf(results);
    }

    /**
     * 结账：产出明细 + 合计 + 优惠。
     *
     * <p>等价于 {@code CartSummary.of(acceptAll(new PricingVisitor()))}。
     * 保留这个便利方法是因为"结账"是本 Demo 的主线操作，
     * 每次调用都写完整两步会掩盖重点；但实现里仍然走
     * 访问者 + 聚合的通用路径，没有另开一套计价逻辑。</p>
     *
     * @return 结账汇总
     */
    public CartSummary summarize() {
        return CartSummary.of(acceptAll(new PricingVisitor()));
    }
}
