package com.patterns.behavioral.state;

import java.util.Set;

/**
 * 已取消：订单作废，流程终止。
 *
 * <p><b>终态</b>：没有任何允许的动作。
 *
 * <p><b>它可能从两个地方转进来：</b>
 * <ul>
 *   <li>从 {@link PendingState}——还没付钱，取消只需释放库存；</li>
 *   <li>从 {@link PaidState}——已经付了钱，取消需要原路退款。</li>
 * </ul>
 * 两条路径的后果完全不同，但<b>终点是同一个状态</b>。
 *
 * <p><b>为什么"从哪儿来"不记在状态里？</b>
 * 这是本类最重要的一个设计点。看一眼状态对象是怎么存在的：
 * <pre>
 *   CancelledState.INSTANCE      // 全场只有一个
 * </pre>
 * 整个 JVM 里所有的已取消订单，共享<b>同一个</b> {@code CancelledState} 实例。
 * 如果把"这张单退了多少钱"存进状态对象，第二张取消的单就会覆盖第一张的数据——
 * 这是把<b>实例数据</b>错放进了<b>共享状态</b>。
 *
 * <p>所以边界非常清楚：
 * <table border="1">
 *   <caption>数据该放在哪</caption>
 *   <tr><th>数据</th><th>例子</th><th>放哪</th><th>为什么</th></tr>
 *   <tr><td>每张订单各不相同</td><td>退款金额、取消原因、取消时间</td>
 *       <td>{@link Order}</td><td>状态对象是共享单例，存不了</td></tr>
 *   <tr><td>所有该状态的订单都一样</td><td>能不能发货、显示名叫什么</td>
 *       <td>状态类</td><td>这是行为的定义，不是数据的记录</td></tr>
 * </table>
 *
 * <blockquote>
 * <b>状态模式里最容易犯的错：往状态对象里塞实例数据。</b>
 * 一旦塞了，状态就不能是单例；不能是单例，就不能用 enum；
 * 不能用 enum，就丢掉了穷尽检查和编译期保护——
 * 一个错误的起点，会连锁丢掉后面所有的好处。
 * </blockquote>
 *
 * <p><b>那 {@code CancelledState} 自己知道"要不要退款"吗？</b>
 * 不知道，也不需要知道。它只需要回答一个问题：在"已取消"这个状态下，
 * 动作该怎么处理。答案是没有动作——这就够了。
 * 至于"这张单退没退款"，那是订单详情页去读 {@code Order.cancelReason()}
 * 的事，跟状态无关。
 *
 * <p><b>这个边界划得对不对，可以用一个测试来验：</b>
 * 如果把 100 张取消的订单同时放在内存里，会不会互相干扰？
 * 会 → 说明有数据错放进了共享状态。
 * 不会 → 边界是干净的。
 *
 * @author design-patterns-pro
 */
public enum CancelledState implements OrderState {

    /** 唯一实例。 */
    INSTANCE;

    /** 空集合：终态不接受任何动作。 */
    private static final Set<OrderAction> ALLOWED = Set.of();

    @Override
    public String displayName() {
        return "已取消";
    }

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;
    }

    /**
     * 永远不会被正常调用。
     *
     * <p>理由与 {@link CompletedState#accept} 完全一致：空的动作集合意味着
     * {@code Order.transition()} 会在调用之前就拒绝一切。
     * 这里抛异常，是为了让"绕过 {@code Order} 直接调用状态"这种结构性错误
     * 立刻暴露，而不是伪装成一次正常的业务拒绝。
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
