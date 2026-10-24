package com.patterns.combined.orderservice;

import com.patterns.util.DisplayWidth;
import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * 折扣计算的最终结果：<b>一个不可变的快照</b>。
 *
 * <p>它把一次链执行的全部产出打包在一起：原价、应付、共减多少、
 * 每一步发生了什么、是否被终止、占用了哪些互斥组。
 * 调用方拿到它之后，<b>不需要再回头访问链、状态或任何可变对象</b> ——
 * 这一点决定了它可以被安全地缓存、传递、序列化、放进异步任务。</p>
 *
 * <p><b>为什么用 {@code record} 而不是普通类？</b></p>
 * 因为它就是一个<b>值</b>：两个结果对象只要所有字段相同，就是同一个结果。
 * 用 record 让「不可变」「逐字段相等」「可打印」三件事自动成立，
 * 也避免了手写 {@code equals/hashCode} 时漏字段这种经典 bug。</p>
 *
 * <p><b>为什么构造器里要 {@code List.copyOf} / {@code Set.copyOf}？</b></p>
 * 因为 record 的字段虽然是 final，但<b>引用的集合可以是可变的</b>。
 * 如果直接把调用方传进来的 {@code ArrayList} 存进去，
 * 调用方事后往里面加一条，这个「不可变快照」就悄悄变了 ——
 * 这比字段可变更隐蔽。{@code copyOf} 做了两件事：
 * 复制一份，并且拒绝任何 {@code null} 元素。</p>
 *
 * <p><b>为什么保留 {@code stopped} 和 {@code usedGroups}？</b></p>
 * 它们是<b>解释性信息</b>，不是计算结果：
 * <ul>
 *   <li>{@code stopped} 告诉调用方「后面还有节点没执行」，这对
 *       「为什么我的积分没用上」这类问题至关重要；</li>
 *   <li>{@code usedGroups} 让订单服务知道哪些互斥组被占用了，
 *       下次用户改选优惠时可以据此提示「与已选优惠冲突」。</li>
 * </ul>
 * <p>只返回一个「最终价」的结果对象，等于把所有可解释性都丢掉了。</p>
 *
 * @param originalPrice  订单原价
 * @param finalPrice     应付金额
 * @param entries        每一步的执行轨迹，按发生顺序
 * @param stopped        是否被某个节点要求终止
 * @param usedGroups     被占用的互斥组
 * @author design-patterns-pro
 */
public record DiscountResult(BigDecimal originalPrice, BigDecimal finalPrice,
                             List<AuditTrail.Entry> entries, boolean stopped,
                             Set<String> usedGroups) {

    /**
     * 冻结集合，防止外部修改。
     *
     * @throws IllegalArgumentException 任一集合为 null
     */
    public DiscountResult {
        if (originalPrice == null || finalPrice == null) {
            throw new IllegalArgumentException("原价与应付金额都不能为 null");
        }
        if (entries == null || usedGroups == null) {
            throw new IllegalArgumentException("轨迹与互斥组集合都不能为 null");
        }
        entries = List.copyOf(entries);
        usedGroups = Set.copyOf(usedGroups);
    }

    /**
     * 总减免额（原价减应付）。
     *
     * <p>没有把它存成字段，因为它是<b>派生值</b>：
     * 存一份就可能与 {@code originalPrice - finalPrice} 算出来不一致，
     * 这类「两个真相」是账目 bug 的常见来源。
     * 派生的东西永远现算。</p>
     *
     * @return 非负金额
     */
    public BigDecimal totalReduction() {
        return DiscountPolicy.money(originalPrice.subtract(finalPrice));
    }

    /**
     * 实际生效的步数与总步数。
     *
     * @return 两元素数组：{@code [生效数, 总步数]}
     */
    public int[] appliedCounts() {
        int applied = 0;
        for (AuditTrail.Entry entry : entries) {
            if (entry.decision() instanceof DiscountDecision.Applied) {
                applied++;
            }
        }
        return new int[] {applied, entries.size()};
    }

    /**
     * 减免占原价的比例，保留 1 位小数，例如 {@code 29.0}。
     *
     * <p>原价为 0 时返回 0，避免除零。虽然 {@link OrderContext}
     * 保证订单金额为正，但结果对象是公开类型，不能假设调用方一定从那里来。</p>
     *
     * @return 百分比数值
     */
    public BigDecimal reductionPercent() {
        if (originalPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return totalReduction()
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 1, RoundingMode.HALF_UP);
    }

    /**
     * 渲染成多行报告：第一行是汇总，后面每行是一步。
     *
     * <p>输出格式固定，不依赖任何随机顺序，因此可以逐字节比对。
     * 步号、节点名（按显示宽度补到 16 列）、决策、前后价、减免额、原因
     * 六段拼接。</p>
     *
     * @return 多行文本，行之间用 {@code \n} 分隔，末尾无换行
     */
    public String render() {
        int[] counts = appliedCounts();
        StringBuilder text = new StringBuilder();
        text.append("原价 ").append(Money.yuan(originalPrice))
                .append(" → 应付 ").append(Money.yuan(finalPrice))
                .append("，共减 ").append(Money.yuan(totalReduction()))
                .append("（").append(reductionPercent().toPlainString()).append("%）")
                .append("  生效 ").append(counts[0]).append('/').append(counts[1]).append(" 步");
        if (stopped) {
            text.append("  链路已终止");
        }
        if (!usedGroups.isEmpty()) {
            text.append("  互斥组 ").append(usedGroups);
        }
        for (AuditTrail.Entry entry : entries) {
            text.append('\n').append(entry.render());
        }
        return text.toString();
    }

    /**
     * 只保留生效的轨迹，用于「给用户看的优惠明细」。
     *
     * <p>跳过和终止的记录对客服排查有用，对用户是噪音。
     * 同一个结果对象提供两种视图，比生成两个结果对象更省。</p>
     *
     * @return 只含 {@code Applied} 的条目列表
     */
    public List<AuditTrail.Entry> appliedEntries() {
        return entries.stream()
                .filter(entry -> entry.decision() instanceof DiscountDecision.Applied)
                .toList();
    }

    /**
     * 给用户看的明细：每行「优惠名 减 ￥X.XX」。
     *
     * @return 多行文本，没有生效项时返回「无可用优惠」
     */
    public String renderUserFacing() {
        List<AuditTrail.Entry> applied = appliedEntries();
        if (applied.isEmpty()) {
            return "无可用优惠";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < applied.size(); index++) {
            AuditTrail.Entry entry = applied.get(index);
            if (index > 0) {
                text.append('\n');
            }
            text.append(DisplayWidth.padRight(entry.policy(), 24))
                    .append("减 ").append(Money.yuan(entry.reduction()));
        }
        return text.toString();
    }
}
