package com.patterns.behavioral.observer;

import java.util.Objects;

/**
 * 邮件观察者：把事件摘要发到用户的邮箱。
 *
 * <p><b>它比 {@link InAppObserver} 多带了一个字段</b>：邮箱地址。
 * 这一点很关键——观察者<b>可以有自己的状态</b>，只要这份状态与
 * "事件长什么样"无关。邮箱地址属于"这个人怎么被联系到"，
 * 和事件内容没有关系，放在观察者里天经地义。
 *
 * <p>反过来，如果某个字段是"从事件里算出来的"（比如"这条事件属于哪个话题"），
 * 那它就该待在事件里，不该变成观察者的状态。
 *
 * <h2>为什么直播不发邮件</h2>
 *
 * <p>这是本类唯一一处业务判断：
 *
 * <pre>
 *   if (event.type().realtime()) {
 *       return;      // 直播错过就没了，邮件发出去也是废纸
 *   }
 * </pre>
 *
 * <p>它证明了观察者模式的另一半价值：<b>同一个事件，不同渠道可以有完全不同的处理。</b>
 * 邮件渠道说"直播不用发"，推送渠道说"直播必须立刻发"——
 * 两条规则互不干扰，各自待在各自的 {@code onEvent} 里。
 * 如果把它们写在 {@code publish} 里，就是一个越写越长的
 * {@code if (channel == EMAIL && type != LIVE) ...}。
 *
 * <p>注意这里的判断用的是 {@link EventType#realtime()} 而不是
 * {@code event.type() == EventType.LIVE}——将来加一种"限时秒杀"事件、
 * 也标记为实时，这段代码不用改。
 *
 * <p>{@link #onEvent} 里没有打印"跳过"，而是用返回值之外的方式静默略过——
 * 因为"这个渠道不需要这条事件"不是失败。报告里它依然记作<b>成功</b>：
 * 我们成功地把"不发"这件事执行了。失败指的是"想发但没发出去"。
 *
 * @author design-patterns-pro
 */
public class EmailObserver implements Observer {

    private final String id;
    private final String displayName;

    /** 收件邮箱。这是观察者自己的状态，与事件无关。 */
    private final String email;

    /**
     * 创建一个邮件观察者。
     *
     * @param id          观察者 ID，非空
     * @param displayName 展示名，非空
     * @param email       收件邮箱，非空
     */
    public EmailObserver(String id, String displayName, String email) {
        this.id = requireText(id, "观察者 ID");
        this.displayName = requireText(displayName, "展示名");
        this.email = requireText(email, "邮箱");
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
     * 收件邮箱。
     *
     * @return 邮箱地址
     */
    public String email() {
        return email;
    }

    /**
     * 收到事件：非实时的才发邮件，实时事件静默略过。
     *
     * <p>略过不等于失败，所以这里<b>不抛异常</b>——
     * 抛了就会在报告里记一条失败，而实际上什么坏事都没发生。
     *
     * @param event 事件，非空
     */
    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        if (event.type().realtime()) {
            return;
        }
        System.out.println("  [邮件] " + displayName + "(" + email + ") ← "
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
