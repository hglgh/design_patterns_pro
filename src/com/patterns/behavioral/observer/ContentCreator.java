package com.patterns.behavioral.observer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内容创作者：既是"发内容的作者"，也是"维护粉丝名单的被观察者"。
 *
 * <p><b>为什么把这两个身份合在一个类里？</b>
 * 因为在现实业务里它们本来就是一件事——一个账号既发内容，也持有关注者列表。
 * 硬拆成 {@code Author} 和 {@code SubscriberRegistry} 两个类，
 * 只会让调用方每次发布都要多走一步"去问注册表要名单"，而注册表的
 * 生命周期又必须和作者绑定，拆开反而增加了一致性负担。
 *
 * <p>但要注意：<b>合并不是耦合。</b> 观察者依然只认识 {@link Observer} 接口，
 * 完全不知道 {@code ContentCreator} 的存在。
 *
 * <h2>名单为什么是 {@code LinkedHashMap<String, Observer>}</h2>
 *
 * <p>一个集合要同时满足三个要求，能选的只有它：
 *
 * <table border="1">
 *   <caption>为什么不用别的集合</caption>
 *   <tr><th>候选</th><th>问题</th></tr>
 *   <tr><td>{@code ArrayList}（原项目）</td>
 *       <td>允许重复；删除靠 {@code equals}；删一个要 O(n) 扫描</td></tr>
 *   <tr><td>{@code HashSet}</td>
 *       <td>能去重，但迭代顺序不稳定——同一份代码跑两次，
 *           通知顺序可能不一样，Demo 输出就不可复现</td></tr>
 *   <tr><td>{@code HashMap}</td>
 *       <td>同上，且顺序的不确定性来自哈希值，更难解释</td></tr>
 *   <tr><td>{@code ConcurrentHashMap}</td>
 *       <td>线程安全，但迭代顺序同样不保证</td></tr>
 * </table>
 *
 * <p>{@code LinkedHashMap} 同时给到三样东西：
 * <b>键去重</b>（同一个 {@code id} 只会有一份）、
 * <b>插入序</b>（投递顺序 = 注册顺序，输出可复现）、
 * <b>O(1) 删除</b>（按 id 直接定位）。
 *
 * <p>用 {@code id} 当键、{@code Observer} 当值，还有一层额外好处：
 * <b>去重发生在注册那一刻</b>，而不是在每次遍历时判重。
 * 原项目在通知时才暴露"同一个人收到两条"的问题，
 * 而在这里根本进不了名单。
 *
 * <h2>发布为什么先快照</h2>
 *
 * <p>{@code new ArrayList<>(observers.values())} 这一行是整段代码里
 * 最容易被删掉、删掉就出事的一行。原因见 {@link #publish(Event)} 的注释。
 *
 * <h2>线程安全说明</h2>
 *
 * <p>本类<b>不是线程安全的</b>。多个线程同时 {@code subscribe} 与
 * {@code publish} 时，{@code LinkedHashMap} 可能出现结构损坏。
 * 生产环境的最小改动是把字段换成 {@code ConcurrentHashMap}，
 * 但那样就丢了插入序；想两者都要，就得在外层加锁，
 * 或者引入一个"注册改动时重建快照"的写时复制结构。
 * 本示例选择保持简单——<b>把问题写清楚，比留一个似是而非的并发实现更好</b>。
 *
 * @author design-patterns-pro
 */
public class ContentCreator implements Subject {

    private final String authorId;
    private final String authorName;

    /**
     * 观察者名单：键是 {@link Observer#id()}，值是观察者实例。
     *
     * <p>{@code LinkedHashMap} 保证迭代顺序 = 插入顺序，
     * 于是"投递顺序 = 注册顺序"这条契约不需要额外做什么就成立了。
     */
    private final Map<String, Observer> observers = new LinkedHashMap<>();

    /**
     * 创建一个内容创作者。
     *
     * @param authorId   作者 ID，非空
     * @param authorName 作者显示名，非空
     */
    public ContentCreator(String authorId, String authorName) {
        this.authorId = requireText(authorId, "作者 ID");
        this.authorName = requireText(authorName, "作者名");
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code putIfAbsent} 而不是 {@code put}：
     * 重复订阅时保留第一次那个实例。如果改成 {@code put}，
     * 后来者会悄悄替换先来者——两个实例长得一样的时候看不出问题，
     * 一旦它们持有不同的状态（比如不同的重试计数），
     * 就会出现"注册了 A，通知却发给了 B"这种极难排查的现象。
     */
    @Override
    public void subscribe(Observer observer) {
        Objects.requireNonNull(observer, "观察者不能为 null");
        String id = Objects.requireNonNull(observer.id(), "观察者 ID 不能为 null");
        observers.putIfAbsent(id, observer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>返回值的来源很直接：{@code remove} 返回被删掉的那个值，
     * 为 {@code null} 就说明键不存在。<b>不要</b>写成
     * {@code observers.remove(id); return true;} ——
     * 那样调用方永远得不到"没删掉"的信号。
     */
    @Override
    public boolean unsubscribe(String observerId) {
        Objects.requireNonNull(observerId, "观察者 ID 不能为 null");
        return observers.remove(observerId) != null;
    }

    @Override
    public int subscriberCount() {
        return observers.size();
    }

    /**
     * 当前名单的展示名列表，顺序与投递顺序一致。
     *
     * <p>给 Demo 用：能直观看出"注册顺序"确实是稳定的，
     * 而不是 HashMap 那种每次运行都可能变化的顺序。
     *
     * @return 不可变的展示名列表
     */
    public List<String> subscriberNames() {
        List<String> names = new ArrayList<>(observers.size());
        for (Observer observer : observers.values()) {
            names.add(observer.displayName());
        }
        return List.copyOf(names);
    }

    /**
     * 广播一条事件。
     *
     * <p><b>第一步：快照。</b>
     *
     * <pre>
     *   List&lt;Observer&gt; snapshot = new ArrayList&lt;&gt;(observers.values());
     * </pre>
     *
     * 这一行解决了原项目最阴的那个 bug。原项目直接
     * {@code for (Follower f : followers)} 遍历，一旦某个观察者在
     * {@code update} 里取消了关注，就会发生下面两件事之一：
     * <ul>
     *   <li>通常是 {@link java.util.ConcurrentModificationException}——
     *       后面的粉丝全部收不到，异常还冒泡给发布者；</li>
     *   <li>如果恰好删的是<b>倒数第二个</b>元素，{@code ArrayList}
     *       迭代器的 {@code cursor} 追上了新的 {@code size}，
     *       {@code hasNext()} 直接返回 {@code false}，<b>不报错</b>，
     *       但最后一个粉丝静默地漏掉了。</li>
     * </ul>
     *
     * 同一个 bug 两种表现，其中一种完全无声。快照把两种都消灭：
     * 遍历的是独立列表，观察者怎么改原集合都不影响本轮。
     *
     * <p>快照还顺带确定了语义：<b>本次通知的名单，在开始的那一刻就固定了。</b>
     * 中途订阅的人收不到本次（他来得太晚），中途退订的人仍然会收到本次
     * （在约定里他还没退）。任何"边界情况"都必须有一个明确的答案，
     * 否则不同人看同一段代码会得出不同结论。
     *
     * <p><b>第二步：逐个投递，失败隔离。</b>
     *
     * <p>只捕获 {@link RuntimeException}：
     * <ul>
     *   <li>业务异常（NullPointerException、IllegalStateException、
     *       "推送通道关闭"）都是 RuntimeException，属于"这一个观察者坏了"，
     *       应该记下来、继续发下一个；</li>
     *   <li>{@link Error}（OutOfMemoryError、StackOverflowError）
     *       是 JVM 级故障，进程还能不能跑下去都不确定，
     *       在这里吞掉只会把真正的问题藏起来。</li>
     * </ul>
     *
     * <p>对比原项目：一个观察者抛异常，后面的全部收不到，
     * 而且 {@code post()} 本身也"失败"了——可内容其实早发出去了。
     * 一对多的通知，天生就该是"部分成功"的语义。
     */
    @Override
    public DispatchReport publish(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");

        List<Observer> snapshot = new ArrayList<>(observers.values());
        List<Delivery> deliveries = new ArrayList<>(snapshot.size());

        for (Observer observer : snapshot) {
            try {
                observer.onEvent(event);
                deliveries.add(Delivery.ok(observer));
            } catch (RuntimeException e) {
                deliveries.add(Delivery.failed(observer, e));
            }
        }

        return DispatchReport.of(event, deliveries);
    }

    /**
     * 发一篇图文。
     *
     * <p>三个便捷方法的存在理由：{@code authorId}/{@code authorName}
     * 是这个对象的固有属性，让调用方每次都手写一遍纯属自找麻烦，
     * 而且一定会有人写错。
     *
     * @param title   标题，非空
     * @param content 正文摘要
     * @return 投递报告
     */
    public DispatchReport publishArticle(String title, String content) {
        return publish(new Event(EventType.ARTICLE, authorId, authorName, title, content));
    }

    /**
     * 发一个视频。
     *
     * @param title   视频标题，非空
     * @param content 简介
     * @return 投递报告
     */
    public DispatchReport publishVideo(String title, String content) {
        return publish(new Event(EventType.VIDEO, authorId, authorName, title, content));
    }

    /**
     * 开一场直播。
     *
     * <p>直播没有正文，所以只收标题——这也是 {@link Event} 允许
     * {@code content} 为 {@code null}（转成空串）的原因。
     *
     * @param title 直播标题，非空
     * @return 投递报告
     */
    public DispatchReport publishLive(String title) {
        return publish(new Event(EventType.LIVE, authorId, authorName, title, ""));
    }

    /**
     * 作者 ID。
     *
     * @return 作者 ID
     */
    public String authorId() {
        return authorId;
    }

    /**
     * 作者显示名。
     *
     * @return 作者显示名
     */
    public String authorName() {
        return authorName;
    }

    /**
     * 校验字符串非 null 且非空白。
     *
     * @param value 待校验值
     * @param name  字段名
     * @return 原值
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
