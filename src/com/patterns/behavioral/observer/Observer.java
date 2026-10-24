package com.patterns.behavioral.observer;

/**
 * 观察者：对某类事件感兴趣、希望在事件发生时被通知的一方。
 *
 * <p><b>只声明三个方法，每个都有具体的理由：</b>
 *
 * <table border="1">
 *   <caption>Observer 的三个方法</caption>
 *   <tr><th>方法</th><th>谁在用</th><th>如果删掉</th></tr>
 *   <tr><td>{@link #id()}</td><td>Subject 去重与解绑、日志归因</td>
 *       <td>只能按 {@code equals} 去重，同名观察者会互相干扰</td></tr>
 *   <tr><td>{@link #displayName()}</td><td>日志、投递报告</td>
 *       <td>报告里只能出现类名 {@code InAppObserver@1a2b}</td></tr>
 *   <tr><td>{@link #onEvent(Event)}</td><td>业务回调</td>
 *       <td>观察者没有存在意义</td></tr>
 * </table>
 *
 * <h2>为什么 {@code id()} 是必需的</h2>
 *
 * <p>原项目用 {@code List<Follower>} 存观察者，去重和删除都依赖
 * {@link Object#equals}。而 {@code ConcreteFollower} 是 {@code record}，
 * 它的 {@code equals} 按 {@code name} 比较——于是<b>两个同名的张三</b>
 * 在列表里是"相等"的：
 *
 * <pre>
 *   f1.follow(star);         // 列表：[张三(a)]
 *   f2.follow(star);         // 列表：[张三(a), 张三(b)]   允许重复
 *   star.post(...);           // 张三收到 2 条通知
 *   f1.unfollow(star);        // 删掉的是 a，但 b 还在
 *   star.post(...);           // 张三仍然收到 1 条
 * </pre>
 *
 * 调用方以为"我取消了关注"，实际上另一个同名账号还在收。
 * 这不是 equals 写错了，而是<b>用 equals 当身份</b>这件事本身就错了：
 * <b>身份应该是显式的、稳定的、由业务给定的</b>，不是从字段推出来的。
 *
 * <h2>为什么 {@code onEvent} 不返回 {@code boolean}</h2>
 *
 * <p>因为返回值会立刻被误用成"成功/失败"，然后调用方会想：
 * "失败了要不要重试？重试几次？"——而重试策略不是观察者该决定的事。
 *
 * <p>本项目的约定很干脆：
 * <ul>
 *   <li><b>正常返回 = 投递成功</b>；</li>
 *   <li><b>抛 {@link RuntimeException} = 投递失败</b>，由
 *       {@link ContentCreator#publish} 捕获并记录，<b>不影响其他观察者</b>。</li>
 * </ul>
 *
 * <p>{@code Error}（OOM、StackOverflow）不在捕获范围内——那些是 JVM 级故障，
 * 吞掉只会让问题更隐蔽。
 *
 * <h2>为什么这里没有 {@code follow(Subject)} / {@code unfollow(Subject)}</h2>
 *
 * <p>原项目的 {@code Followable} 接口把"关注"放在了<b>观察者</b>身上：
 *
 * <pre>
 *   public interface Followable {
 *       void follow(User target);       // 观察者主动去改 Subject 的列表
 *       void unfollow(User target);
 *   }
 * </pre>
 *
 * 这个方向的依赖是反的。{@code ConcreteFollower.follow()} 里必须
 * {@code instanceof ConcreteUser} 再强转，才能调到 {@code addFollower}：
 *
 * <pre>
 *   public void follow(User target) {
 *       if (target instanceof ConcreteUser) {         // ← 依赖具体实现
 *           ((ConcreteUser) target).addFollower(this);
 *       }
 *       // 否则什么也不做 —— 静默失败
 *   }
 * </pre>
 *
 * 两个后果：<b>面向接口编程时无法注册</b>，以及<b>换个 Subject 实现就静默失效</b>。
 *
 * <p>正确方向是 {@code subject.subscribe(observer)}——<b>订阅是 Subject 的职责</b>，
 * 因为观察者列表本来就在 Subject 手里。观察者只需要实现 {@code onEvent}。
 *
 * @author design-patterns-pro
 */
public interface Observer {

    /**
     * 观察者的稳定标识，例如 {@code "fan-1001"}。
     *
     * <p>要求<b>非空、稳定、不随显示名变化</b>。
     * 它就是集合的键：注册去重、解绑、日志归因全部靠它。
     *
     * @return 观察者 ID
     */
    String id();

    /**
     * 展示名，例如 {@code 站内信·张三}。
     *
     * <p>与 {@link #id()} 分开的原因和 {@code OrderAction.label()} 一样：
     * 一个给机器用，一个给人看。运营要改文案时不该动 ID。
     *
     * @return 展示名
     */
    String displayName();

    /**
     * 收到一条事件。
     *
     * <p><b>契约：</b>
     * <ol>
     *   <li>本方法可能被多个事件连累调用，实现应该<b>无状态或自行保证线程安全</b>；</li>
     *   <li><b>不得抛出 {@link Error}</b>；抛 {@link RuntimeException} 视为投递失败，
     *       由 Subject 捕获并记入投递报告；</li>
     *   <li>不要在这里调用 {@code subject.subscribe(...)} 期待"立刻收到本次事件"——
     *       Subject 采用的是<b>快照遍历</b>，本次名单在开始通知时已经固定。</li>
     * </ol>
     *
     * @param event 事件，非空
     */
    void onEvent(Event event);
}
