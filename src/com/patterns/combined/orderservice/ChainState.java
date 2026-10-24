package com.patterns.combined.orderservice;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 链的执行状态：<b>当前价格 + 已用互斥组 + 审计轨迹</b>。
 *
 * <p>责任链的每个节点都需要读「当前价」、写「减后的价」，
 * 还需要知道「某个互斥组是不是已经被前面的节点占了」。
 * 这三样东西如果都靠方法参数一个个传，{@code process} 的签名会变成
 * {@code process(price, context, trail, usedGroups, stopped)} ——
 * 五个参数里四个是"传递中的状态"，而且每加一种状态就要改所有节点的签名。</p>
 *
 * <p>打包成一个对象之后：<b>签名稳定、新增状态不影响任何子类</b>，
 * 这也是「引入参数对象」这个重构手法的标准理由。</p>
 *
 * <p><b>为什么这个类可变，而 {@link OrderContext} 不可变？</b></p>
 * 两者在链上的角色完全不同：
 * <ul>
 *   <li>{@code OrderContext} 是<b>输入事实</b>：订单金额、用户等级、积分余额。
 *       它在一次计算中不应该被任何节点改变 —— 否则后面的节点看到的世界就变了；</li>
 *   <li>{@code ChainState} 是<b>累加器</b>：价格会一路减少，互斥组会被占用，
 *       轨迹会变长。它存在的意义就是被改变。</li>
 * </ul>
 * <p><b>「不可变」不是教条，它只适用于那些"不该被改"的东西。</b>
 * 把累加器也做成不可变（每步 return 一个新 ChainState），
 * 会让链条变成一串返回值传递，可读性反而更差。</p>
 *
 * <p><b>为什么用 {@link LinkedHashSet} 存互斥组？</b></p>
 * 只为了一件事：打印「已占用的组」时顺序稳定，Demo 输出可复现。
 * {@code HashSet} 的迭代顺序虽然在同一 JDK 内稳定，但不该依赖它。</p>
 *
 * @author design-patterns-pro
 */
public final class ChainState {

    /** 当前价格，随节点执行逐步减少。 */
    private BigDecimal price;

    /** 审计轨迹。 */
    private final AuditTrail trail = new AuditTrail();

    /** 已被占用的互斥组名称，按首次占用顺序。 */
    private final Set<String> usedGroups = new LinkedHashSet<>();

    /** 是否已被某个节点要求终止。 */
    private boolean stopped;

    /**
     * 以初始价格创建执行状态。
     *
     * @param initialPrice 订单原价，非 null 且非负
     * @throws IllegalArgumentException 价格为 null 或为负
     */
    public ChainState(BigDecimal initialPrice) {
        DiscountPolicy.requireValidPrice(initialPrice);
        this.price = DiscountPolicy.money(initialPrice);
    }

    /**
     * 当前价格。
     *
     * @return 当前价格
     */
    public BigDecimal price() {
        return price;
    }

    /**
     * 扣减价格。
     *
     * <p>这里再做一次「不低于 0」的兜底。理论上策略契约已经保证了
     * {@code amount <= price}，但这是<b>跨类的约定</b>，
     * 而跨类约定是最容易在重构中被破坏的东西。多一次 {@code max}，
     * 换来的是「无论哪个策略写错，用户都不会看到负价」。</p>
     *
     * @param amount 减免金额
     */
    public void reduce(BigDecimal amount) {
        BigDecimal next = DiscountPolicy.money(price.subtract(amount));
        this.price = next.signum() < 0 ? DiscountPolicy.ZERO : next;
    }

    /** 审计轨迹。 */
    public AuditTrail trail() {
        return trail;
    }

    /**
     * 是否已终止。
     *
     * @return 已终止时返回 true
     */
    public boolean stopped() {
        return stopped;
    }

    /**
     * 标记终止。
     *
     * <p>终止原因不单独存一份 —— 它已经写在最后一条 {@link AuditTrail.Entry} 里了。
     * 重复保存两份状态，是「两处真相」的开始。</p>
     */
    public void stop() {
        this.stopped = true;
    }

    /**
     * 某个互斥组是否已被占用。
     *
     * @param group 组名
     * @return 已占用时返回 true
     */
    public boolean groupUsed(String group) {
        return usedGroups.contains(group);
    }

    /**
     * 占用一个互斥组。
     *
     * @param group 组名
     */
    public void markGroup(String group) {
        usedGroups.add(group);
    }

    /**
     * 已占用的互斥组，按占用顺序。
     *
     * @return 不可变集合
     */
    public Set<String> usedGroups() {
        return Collections.unmodifiableSet(usedGroups);
    }

    /**
     * 已执行步数。
     *
     * @return 轨迹条目数
     */
    public int steps() {
        return trail.size();
    }
}
