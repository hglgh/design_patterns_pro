package com.patterns.combined.orderservice;

import com.patterns.util.DisplayWidth;
import com.patterns.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 审计轨迹：<b>把链上每一步发生了什么记下来</b>。
 *
 * <p>原项目所谓的「日志装饰器」只做了一件事：在策略前后各打一行
 * {@code System.out.println}。它有三个问题：</p>
 * <ol>
 *   <li><b>关不掉</b>：日志写死在业务类里，线上不想打也照打；</li>
 *   <li><b>不成结构</b>：只有一段文本，想统计「本单用了几种优惠、共减多少」必须再解析字符串；</li>
 *   <li><b>不带身份</b>：两行日志里没有任何策略名，多个折扣叠加后看不出哪一行属于谁。</li>
 * </ol>
 *
 * <p>本类把「记录」变成<b>结构化数据</b>：每一步是一条 {@link Entry}，
 * 有顺序号、节点名、策略名、决策、前后价格。有了结构化数据，
 * 「输出给用户看」「上报监控」「写数据库」都只是对同一个列表的不同渲染方式。</p>
 *
 * <p><b>为什么这个类是可变的（与项目其他值对象相反）？</b></p>
 * 因为它是一个<b>构建中的收集器</b>，不是最终产物：
 * 链执行期间每步追加一条，执行结束后通过 {@link #entries()} 交出快照，
 * 快照再被 {@link DiscountResult} 冻结成不可变对象。
 * 这是「构建期可变、发布后不可变」的标准做法（{@code StringBuilder}、
 * {@code Stream.Builder} 都是这个模式）。<b>不可变的边界应该在对外暴露处，
 * 而不是内部实现里。</b>把收集器也做成不可变（每次 add 复制整个列表）
 * 只会让链的执行变成 O(n²)。</p>
 *
 * <p><b>为什么 {@link Entry} 要保存「前价」和「后价」而不是只保存「减免额」？</b></p>
 * 两个字段都有人要：
 * <ul>
 *   <li>运营看明细要「减了多少」—— 前价减后价；</li>
 *   <li>财务对账要「每一步之后的余额」—— 后价；</li>
 *   <li>排查问题时要看「顺序变了结果差多少」—— 前后价一起看最直观。</li>
 * </ul>
 * 只存减免额会让后价需要「从头累加」才能得到，一旦中间有跳过的节点就很容易算错。</p>
 *
 * @author design-patterns-pro
 */
public final class AuditTrail {

    /** 收集到的条目，按发生顺序。 */
    private final List<Entry> entries = new ArrayList<>();

    /**
     * 追加一条记录。
     *
     * @param node     链节点名称
     * @param policy   策略名称（可能带装饰器前缀）
     * @param decision 该节点做出的决策
     * @param before   执行前价格
     * @param after    执行后价格
     * @return 刚追加的条目，便于调用方立即渲染
     */
    public Entry record(String node, String policy, DiscountDecision decision,
                        BigDecimal before, BigDecimal after) {
        Entry entry = new Entry(entries.size() + 1, node, policy, decision, before, after);
        entries.add(entry);
        return entry;
    }

    /**
     * 当前快照。
     *
     * <p>返回的是<b>不可变副本</b>，不是内部列表本身。这样调用方
     * 拿到结果之后，即使轨迹对象还在被后续执行写入，也不会影响已发布的结果。</p>
     *
     * @return 不可变列表
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * 已记录步数。
     *
     * @return 条目数量
     */
    public int size() {
        return entries.size();
    }

    /**
     * 是否有过任何记录。
     *
     * @return 无记录时返回 true
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 清空。仅用于复用同一个轨迹对象跑多次演示。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 一条轨迹：某个节点在某一步做了什么。
     *
     * @param seq      从 1 开始的步号
     * @param node     链节点名称
     * @param policy   策略名称
     * @param decision 决策
     * @param before   执行前价格
     * @param after    执行后价格
     */
    public record Entry(int seq, String node, String policy, DiscountDecision decision,
                        BigDecimal before, BigDecimal after) {

        /**
         * 本步实际减免额（前价减后价）。
         *
         * @return 非负金额
         */
        public BigDecimal reduction() {
            return DiscountPolicy.money(before.subtract(after));
        }

        /**
         * 渲染成一行固定宽度的表格行。
         *
         * <p>列宽是按<b>显示宽度</b>（中文占 2 列）计算后再补齐的，
         * 所以中英文混排时后面的列也会对齐。详见 {@link DisplayWidth}。</p>
         *
         * @return 例如 {@code " 1  满减①            生效    ￥200.00 → ￥180.00  减 ￥20.00"}
         */
        public String render() {
            String head = String.format("%2d  %s", seq, DisplayWidth.padRight(node, 16));
            String body = decision.label() + "  "
                    + Money.yuan(before) + " → " + Money.yuan(after)
                    + "  减 " + Money.yuan(reduction());
            String detail = switch (decision) {
                case DiscountDecision.Applied applied -> "";
                case DiscountDecision.Skipped skipped -> "  （" + skipped.reason() + "）";
                case DiscountDecision.Stopped stopped -> "  （" + stopped.reason() + "）";
            };
            return head + body + detail;
        }
    }
}
