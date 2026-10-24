package com.patterns.behavioral.state;

import java.util.Set;

/**
 * 已完成：买家已签收，交易结束。
 *
 * <p><b>终态</b>：没有任何允许的动作，订单彻底定稿。
 *
 * <p><b>为什么它和 {@link CancelledState} 长得几乎一样，却不合并？</b>
 * 这是一个值得认真回答的问题，因为"消除重复"是程序员的本能，
 * 而这里的答案是<b>不要消除</b>。
 *
 * <p>先看合并会得到什么：
 * <pre>
 *   public enum TerminalState implements OrderState {   // 终态：已完成 / 已取消
 *       COMPLETED("已完成"), CANCELLED("已取消");
 *       ...
 *   }
 * </pre>
 * 看起来省了一个类。但接下来会发生什么？
 * <ul>
 *   <li>运营提出："已完成 7 天内的订单要支持申请售后。"</li>
 *   <li>于是 {@code TerminalState} 里出现 {@code if (this == COMPLETED) ...}——</li>
 *   <li>下一句是"已取消的订单要支持恢复"：{@code else if (this == CANCELLED) ...}——</li>
 *   <li>再下一句……最终 {@code TerminalState} 变成了一个用 {@code if} 分派的
 *       伪状态类，<b>正是状态模式要消灭的那种代码</b>。</li>
 * </ul>
 *
 * <p>合并的诱因是"它们现在一样"，但状态类的生命周期<b>不取决于现在</b>，
 * 取决于业务分化。已完成和已取消是两个完全不同的业务结局，
 * 它们将来走向不同的分支是<b>迟早的事</b>。
 *
 * <blockquote>
 * <b>两个类长得一样，不等于它们是一个概念。</b>
 * 判断标准不是"代码是否相同"，而是"它们将来是否会同向演化"。
 * 同向 → 合并；可能分道扬镳 → 各留各的。
 * </blockquote>
 *
 * <p>顺带一提：这里也没有把两个终态抽成
 * {@code abstract class FinalState} 来共享 {@link #allowedActions()}。
 * 因为那点"共享"只有一行 {@code return Set.of();}，
 * 而代价是五个状态不能都做成 {@code enum}（Java 不支持多继承）。
 * <b>为了一行代码牺牲 enum 的三个好处，是一笔明显的亏本买卖。</b>
 *
 * @author design-patterns-pro
 */
public enum CompletedState implements OrderState {

    /** 唯一实例。 */
    INSTANCE;

    /**
     * 空集合：终态不接受任何动作。
     *
     * <p>用 {@code Set.of()} 而不是 {@code Collections.emptySet()}：
     * 前者是 JDK 9+ 的写法，返回的是同一个不可变单例，语义更直白。
     */
    private static final Set<OrderAction> ALLOWED = Set.of();

    @Override
    public String displayName() {
        return "已完成";
    }

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;
    }

    /**
     * 永远不会被正常调用。
     *
     * <p>{@code Order.transition()} 会先检查
     * {@code allowedActions().contains(action)}，而本状态的集合是空的，
     * 所以任何动作都会在<b>到达这里之前</b>被拒绝——这是正常路径。
     *
     * <p>那这个方法为什么还要实现？因为接口要求。
     * 而且它必须<b>抛异常而不是返回一个假的拒绝</b>：
     * 如果真的有人绕过 {@code Order} 直接调了它，
     * 那说明代码里存在一个不该存在的调用路径，
     * 这时候让它<b>大声崩掉</b>，比返回一句"已完成不能支付"要安全得多——
     * 后者会把这个结构性错误伪装成一次正常的业务拒绝，永远查不出来。
     *
     * @param action 动作
     * @param order  订单
     * @return 不返回，总是抛出
     * @throws IllegalStateException 只要被调用就抛
     */
    @Override
    public TransitionResult accept(OrderAction action, Order order) {
        throw OrderState.unreachable(this, action);
    }
}
