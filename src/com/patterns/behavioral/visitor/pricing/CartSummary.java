package com.patterns.behavioral.visitor.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次结账的完整汇总（不可变值对象）。
 *
 * <p><b>它替代了原项目的 getTotalPrice()。</b>
 * 原项目的总价是访问者身上的一个 double 字段，
 * 调用方为了拿总价必须先 {@code cart.accept(visitor)} 再
 * {@code visitor.getTotalPrice()}，两步之间有耦合；
 * 而且访问者复用一次就翻倍（探针 V3）。</p>
 *
 * <p>现在总价是一个显式的聚合结果：明细行 + 合计 + 优惠。
 * 三个数字放在一起，好处是：</p>
 * <ul>
 *   <li>调用方拿到的是"一次结账的完整结论"，不是一个孤立的数字；</li>
 *   <li>可以断言 {@code total + saved == 原价合计} 这条恒等式；</li>
 *   <li>同一个对象可以直接序列化成账单，不需要再访问购物车。</li>
 * </ul>
 *
 * @param lines 明细行，按购物车顺序
 * @param total 应付合计
 * @param saved 优惠合计
 */
public record CartSummary(List<LineResult> lines, Money total, Money saved) {

    /**
     * 紧凑构造器：复制明细列表并校验金额。
     */
    public CartSummary {
        if (lines == null) {
            throw new IllegalArgumentException("lines 不能为 null");
        }
        List<LineResult> copy = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            LineResult line = lines.get(index);
            if (line == null) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行为 null");
            }
            copy.add(line);
        }
        lines = List.copyOf(copy);

        if (total == null || saved == null) {
            throw new IllegalArgumentException("金额字段不能为 null");
        }
        if (total.isNegative() || saved.isNegative()) {
            throw new IllegalArgumentException("金额不能为负");
        }
    }

    /**
     * 把明细折叠成汇总。
     *
     * <p>这是"访问者负责逐项、对象结构负责聚合"的分工在结果侧的延续：
     * 访问者产出明细，这个静态方法负责求和。
     * 空购物车会得到 {@link Money#ZERO} 而不是 null。</p>
     *
     * @param lines 明细行，不能为 null
     * @return 汇总结果
     */
    public static CartSummary of(List<LineResult> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("lines 不能为 null");
        }
        Money total = Money.ZERO;
        Money saved = Money.ZERO;
        for (LineResult line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("明细行不能为 null");
            }
            total = total.plus(line.subtotal());
            saved = saved.plus(line.discount());
        }
        return new CartSummary(lines, total, saved);
    }

    /**
     * 明细行数（不同商品的种数，不是件数）。
     *
     * @return 行数
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * 商品总件数（各行数量之和）。
     *
     * @return 件数
     */
    public int itemCount() {
        int count = 0;
        for (LineResult line : lines) {
            count += line.quantity();
        }
        return count;
    }

    /**
     * 是否为空账单。
     *
     * @return 没有任何明细时返回 true
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
