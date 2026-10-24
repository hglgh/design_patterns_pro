package com.patterns.behavioral.observer;

import java.util.Objects;

/**
 * 推送观察者：把事件推送到用户的手机。
 *
 * <p><b>它承担两个示范任务：</b>
 * <ol>
 *   <li><b>按事件类型调整自己的行为</b>——实时事件走高优先级，
 *       其余走普通通道；</li>
 *   <li><b>会失败</b>——设备下线、通道关闭的时候，
 *       这里抛出的异常会被 {@link ContentCreator#publish} 捕获，
 *       变成 {@link Delivery} 里的失败记录，而<b>不会中断其他人的投递</b>。</li>
 * </ol>
 *
 * <h2>为什么把"通道关闭"做成构造参数，而不是 setter</h2>
 *
 * <p>因为 setter 会引入可变状态，这个类就不再是"初始化后只读"的了，
 * 线程安全、可测试性都要跟着重新考虑。
 * 而本示例要演示的只有一件事：<b>一个观察者坏了，其他人不受影响。</b>
 * 一个不可变的"坏观察者"就足以说明，不需要"坏掉又恢复"的完整生命周期。
 *
 * <p>另外，用构造参数表达"这个观察者天生就是坏的"还有个好处：
 * Demo 里一眼就能看出谁是被故意弄坏的，不会让人误以为
 * 那段失败输出是跑错了。
 *
 * <h2>为什么异常抛的是 {@code IllegalStateException}</h2>
 *
 * <p>因为它准确描述了这个状态：<b>对象本身没被用错，只是它现在不可用。</b>
 * 对比 {@code IllegalArgumentException}（参数传错了）、
 * {@code NullPointerException}（少传了东西）——那些都是调用方的错误，
 * 而"通道关了"是运行环境的问题，重试有可能成功。
 * 选对异常类型，日志一眼就能分辨这是"代码 bug"还是"环境故障"。
 *
 * @author design-patterns-pro
 */
public class PushObserver implements Observer {

    private final String id;
    private final String displayName;

    /** 设备标识，例如 {@code device-w5}。 */
    private final String deviceId;

    /** 推送通道是否可用。{@code false} 时 {@link #onEvent} 必然失败。 */
    private final boolean channelOpen;

    /**
     * 创建一个通道正常的推送观察者。
     *
     * @param id          观察者 ID，非空
     * @param displayName 展示名，非空
     * @param deviceId    设备标识，非空
     */
    public PushObserver(String id, String displayName, String deviceId) {
        this(id, displayName, deviceId, true);
    }

    /**
     * 创建一个推送观察者。
     *
     * @param id          观察者 ID，非空
     * @param displayName 展示名，非空
     * @param deviceId    设备标识，非空
     * @param channelOpen 通道是否可用
     */
    public PushObserver(String id, String displayName, String deviceId, boolean channelOpen) {
        this.id = requireText(id, "观察者 ID");
        this.displayName = requireText(displayName, "展示名");
        this.deviceId = requireText(deviceId, "设备标识");
        this.channelOpen = channelOpen;
    }

    /**
     * 创建一个通道已关闭的推送观察者，专供"失败隔离"演示使用。
     *
     * <p>静态工厂方法的名字直接点明了意图（{@code closed}），
     * 比在 {@code new PushObserver(..., false)} 里数第四个参数的布尔值更容易读。
     *
     * @param id          观察者 ID，非空
     * @param displayName 展示名，非空
     * @param deviceId    设备标识，非空
     * @return 通道关闭的推送观察者
     */
    public static PushObserver closed(String id, String displayName, String deviceId) {
        return new PushObserver(id, displayName, deviceId, false);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * 设备标识。
     *
     * @return 设备标识
     */
    public String deviceId() {
        return deviceId;
    }

    /**
     * 收到事件：通道可用则打印并标记优先级，不可用则抛异常。
     *
     * <p>注意这里<b>没有 try-catch</b>。观察者不需要自己兜底——
     * 兜底是 Subject 的责任。观察者只管如实报告"我失败了"，
     * 这比自己吞掉异常再打印一行日志要诚实得多：
     * 吞掉之后，发布方永远不知道推送其实没发出去。
     *
     * @param event 事件，非空
     * @throws IllegalStateException 推送通道已关闭时
     */
    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        if (!channelOpen) {
            throw new IllegalStateException("推送通道已关闭：" + deviceId);
        }
        String priority = event.type().realtime() ? "实时" : "普通";
        System.out.println("  [推送/" + priority + "] " + deviceId + " ← "
                + event.describe() + "｜" + event.summary());
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
