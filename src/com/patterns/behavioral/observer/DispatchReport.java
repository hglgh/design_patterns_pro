package com.patterns.behavioral.observer;

import java.util.List;
import java.util.Objects;

/**
 * 一次广播的完整结果：发了什么事件、发给几个人、成了几个、败了几个、每个人怎么样了。
 *
 * <p><b>为什么 {@code publish} 不返回 {@code void}？</b>
 *
 * <p>原项目的 {@code post()} 是这样的：
 *
 * <pre>
 *   public void post(String content) {
 *       System.out.println(userName + " 发布动态：" + content);
 *       notifyFollowers(content);        // void
 *   }
 * </pre>
 *
 * 调用方拿到 {@code void}，等于拿到一句"应该发完了吧"。出了任何问题，
 * 唯一的信号是异常——可异常又会中断整个通知链。
 * 于是代码陷入两难：<b>想要知道失败就得收异常，可收了异常就发不全。</b>
 *
 * <p>本项目把它拆成两件事：
 * <ul>
 *   <li><b>单个观察者的成败</b>——记在 {@link Delivery} 里，不抛给调用方；</li>
 *   <li><b>整次广播的成败</b>——由本类汇总，作为返回值交给调用方。</li>
 * </ul>
 *
 * 这样"发布内容"这件事永远不会因为"某个粉丝的推送通道坏了"而失败。
 * 内容发布成功和通知投递成功是两件不同的事，不能用一个异常绑在一起。
 *
 * <p><b>为什么把 {@code succeeded}/{@code failed} 存成字段，
 * 而不是每次从</b> {@code deliveries} <b>现算？</b>
 * 因为这个对象会被反复读（打印、打点、告警判断），
 * 每次 {@code stream().filter().count()} 是 O(n)。
 * 而构造时算一次是 O(n)，之后全是 O(1)。
 * 更重要的一点：<b>构造处是唯一能保证三个数字自洽的地方</b>——
 * 如果允许外部只给 {@code deliveries} 再让 getter 现算，
 * 一旦有人传进来一个可变的 {@code List} 并在构造后改动它，
 * 三个数字就会跟着变，报告也就不再是"那一刻的快照"了。
 *
 * <p><b>为什么构造时就 {@code List.copyOf}？</b>
 * {@code List.copyOf} 会做一次真正的复制，返回一个不可变列表。
 * 它不是 {@code Collections.unmodifiableList} 那种"视图"——
 * 视图只是禁止你改，原列表被别处改了它照样跟着变。
 * 报告要的是冻结的事实，必须真拷贝。
 *
 * @param event     本次广播的事件
 * @param deliveries 逐个观察者的投递结果，顺序与注册顺序一致
 * @param succeeded 成功数量
 * @param failed    失败数量
 * @author design-patterns-pro
 */
public record DispatchReport(
        Event event,
        List<Delivery> deliveries,
        int succeeded,
        int failed
) {

    /**
     * 紧凑构造器：复制列表，并校验三个数字与明细完全对得上。
     *
     * <p>这条校验看起来啰嗦，但它把"报告自己说自己"的可能性堵死了：
     * 只要有任何一个数字和明细不符，构造就会失败，
     * 而不是在三个月后被人发现"告警说 0 失败，其实漏了 3 个人"。
     */
    public DispatchReport {
        Objects.requireNonNull(event, "事件不能为 null");
        deliveries = List.copyOf(deliveries);

        long okCount = deliveries.stream().filter(Delivery::succeeded).count();
        long failCount = deliveries.size() - okCount;
        if (okCount != succeeded || failCount != failed) {
            throw new IllegalArgumentException(
                    "统计数字与明细不符：声明 " + succeeded + " 成功 / " + failed
                            + " 失败，实际 " + okCount + " 成功 / " + failCount + " 失败");
        }
    }

    /**
     * 便捷构造：只有明细，两个数字自动算出来。
     *
     * <p>这是 {@link com.patterns.behavioral.observer.ContentCreator} 实际使用的入口。
     * 让调用方自己数数是没有意义的重复劳动，而且一定会有人数错。
     *
     * @param event      本次广播的事件
     * @param deliveries 逐个观察者的投递结果
     * @return 报告
     */
    public static DispatchReport of(Event event, List<Delivery> deliveries) {
        List<Delivery> copy = List.copyOf(deliveries);
        int ok = (int) copy.stream().filter(Delivery::succeeded).count();
        return new DispatchReport(event, copy, ok, copy.size() - ok);
    }

    /**
     * 投递总数（成功 + 失败）。
     *
     * @return 本次广播一共尝试投递了多少个观察者
     */
    public int attempted() {
        return deliveries.size();
    }

    /**
     * 是否全员成功。
     *
     * <p>注意：没有订阅者时返回 {@code true}（空真）。
     * 语义上"没有人需要通知"确实不存在失败，但如果业务上把
     * "零订阅者"也视为异常，应该判断 {@link #attempted()} 而不是这个方法。
     *
     * @return 没有任何一个观察者失败时返回 {@code true}
     */
    public boolean allSucceeded() {
        return failed == 0;
    }

    /**
     * 把报告渲染成多行文本，供 Demo 打印。
     *
     * <p>格式固定为三段：标题行、每个观察者一行、汇总行。
     * 之所以不直接 {@code toString()}，是因为 {@code record} 生成的
     * {@code toString} 会把整个 {@code List} 塞进一行——
     * 六个人就是六行挤成一行，终端里根本读不了。
     *
     * @return 多行报告文本
     */
    public String describeAsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("投递报告｜").append(event.describe()).append('\n');
        for (Delivery delivery : deliveries) {
            sb.append("  ").append(delivery.describe()).append('\n');
        }
        sb.append("  合计：尝试 ").append(attempted())
                .append(" 人，成功 ").append(succeeded)
                .append(" 人，失败 ").append(failed).append(" 人");
        return sb.toString();
    }
}
