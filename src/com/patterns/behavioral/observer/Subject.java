package com.patterns.behavioral.observer;

/**
 * 被观察者（主题）：维护一份观察者名单，并在事件发生时逐一分发。
 *
 * <p><b>为什么接口里必须有 {@code subscribe} / {@code unsubscribe}？</b>
 *
 * <p>这是原项目最根本的结构性错误。它把"关注"放在了观察者身上
 * （{@code Followable.follow(User target)}），而观察者列表却存在 Subject 手里。
 * 结果是观察者必须反向操作 Subject 的内部集合，还要 {@code instanceof} 强转：
 * 依赖方向反了，换个 {@code User} 实现就静默失效。
 *
 * <p>正确的方向是：<b>谁持有数据，谁提供操作数据的方法。</b>
 * 名单在 Subject 里，注册和注销就应该是 Subject 的方法。
 * 这样 {@link Observer} 只需要实现 {@link Observer#onEvent(Event)} 一件事，
 * 它对 Subject 一无所知——这才是真正的解耦。
 *
 * <p><b>为什么 {@code unsubscribe} 返回 {@code boolean}，
 * 而 {@code subscribe} 不返回？</b>
 *
 * <p>因为两者的"失败"含义完全不同：
 * <ul>
 *   <li>{@code subscribe} 的语义是"确保这个观察者在名单里"——<b>幂等</b>。
 *       重复订阅不报错、也不重复投递，调用方不需要知道"之前是不是已经有了"。</li>
 *   <li>{@code unsubscribe} 的语义是"把它从名单里删掉"，而调用方常常需要知道
 *       <b>到底删掉了没有</b>——比如前端点"取消关注"，按钮是不是该变灰？
 *       返回 {@code false} 说明本来就没订阅，这往往是调用方传错了 ID。</li>
 * </ul>
 *
 * <p><b>为什么 {@code publish} 返回 {@link DispatchReport} 而不是 {@code void}
 * 或者 {@code int}？</b>
 * 见 {@link DispatchReport} 的说明：内容发布成功和通知投递成功是两件事，
 * 不能用一个异常或一个数字混为一谈。返回类型本身就是契约的一部分——
 * 它告诉每一个调用方："这里会有失败，而且失败是正常业务的一部分。"
 *
 * <p><b>为什么没有 {@code publish(Event, Predicate<Observer> filter)}？</b>
 * "按类型订阅""按标签订阅"是真实需求，但那属于<b>订阅关系</b>的建模，
 * 不是本模式的核心。真要做，正确的位置是引入一个
 * {@code Subscription(Observer, Predicate&lt;Event&gt;)} 作为名单里的一项，
 * 而不是给 {@code publish} 加参数。留到 README 的"扩展对比"里讨论，
 * 不在核心示例里预先设计。
 *
 * @author design-patterns-pro
 */
public interface Subject {

    /**
     * 注册一个观察者。同一个 {@link Observer#id()} 重复注册不会产生第二个订阅。
     *
     * <p>约定：
     * <ul>
     *   <li>重复注册时<b>保留第一次注册的实例</b>，不做替换——
     *       替换会让"到底哪一个在收通知"变得不可预测；</li>
     *   <li>新观察者排在名单末尾，投递顺序 = 注册顺序。</li>
     * </ul>
     *
     * @param observer 观察者，非空
     */
    void subscribe(Observer observer);

    /**
     * 按 ID 注销一个观察者。
     *
     * <p>按 ID 而不是按对象注销，是因为调用方手里常常只有 ID
     * （比如从请求参数里拿到的 {@code fanId}），拿不到当初注册的那个实例。
     *
     * @param observerId 观察者 ID，非空
     * @return 真的删掉了一个返回 {@code true}，本来就没订阅返回 {@code false}
     */
    boolean unsubscribe(String observerId);

    /**
     * 当前订阅者数量。
     *
     * <p>存在的理由是让 Demo 和测试能直接观察"注册/注销"的效果，
     * 而不必去反射读内部集合。
     *
     * @return 订阅者数量
     */
    int subscriberCount();

    /**
     * 广播一条事件给当前所有订阅者。
     *
     * <p>契约：
     * <ol>
     *   <li>先<b>快照</b>再遍历——开始通知后新订阅的人收不到本次事件，
     *       通知途中退订的人也不会让遍历抛
     *       {@link java.util.ConcurrentModificationException}；</li>
     *   <li>单个观察者抛 {@link RuntimeException} 只影响它自己，
     *       其余照常收到，失败记入返回的报告；</li>
     *   <li>不捕获 {@link Error}。</li>
     * </ol>
     *
     * @param event 事件，非空
     * @return 本次投递的完整报告
     */
    DispatchReport publish(Event event);
}
