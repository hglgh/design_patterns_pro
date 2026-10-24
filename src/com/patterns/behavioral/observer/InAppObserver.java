package com.patterns.behavioral.observer;

import java.util.Objects;

/**
 * 站内信观察者：把事件写进用户的通知中心。
 *
 * <p><b>这是最"标准"的一个观察者，它证明了观察者模式到底要什么：</b>
 * 一个 {@link Observer#id()}、一个 {@link Observer#displayName()}、
 * 一个 {@link #onEvent(Event)}，<b>仅此而已</b>。
 *
 * <p>它不认识 {@link Subject}，不认识 {@link ContentCreator}，
 * 甚至不知道"是谁发的"——因为 {@link Event} 已经把作者信息带在身上了。
 * 换成任何别的被观察者（订单系统、监控系统、聊天室）都能直接复用，
 * 只要它们发的是 {@code Event}。
 *
 * <p>对比原项目的 {@code ConcreteFollower}：它同时实现了
 * {@code Follower} 和 {@code Followable} 两个接口，一个类背了
 * "被通知"和"去关注"两件事。这里不需要——关注是 Subject 的方法。
 *
 * <p><b>为什么不需要 {@code equals}/{@code hashCode}？</b>
 * 因为身份由 {@link #id()} 显式给出，集合的键就是它。
 * 对象本身怎么比较，与本模式无关。
 *
 * @author design-patterns-pro
 */
public class InAppObserver implements Observer {

    private final String id;
    private final String displayName;

    /**
     * 创建一个站内信观察者。
     *
     * @param id          观察者 ID，非空
     * @param displayName 展示名，非空
     */
    public InAppObserver(String id, String displayName) {
        this.id = requireText(id, "观察者 ID");
        this.displayName = requireText(displayName, "展示名");
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
     * 收到事件：往通知中心塞一条。
     *
     * <p>真实实现会是 {@code notificationRepository.save(...)}，
     * 本示例打印一行——<b>观察者的输出格式完全可以各不相同</b>，
     * 这正是模式的意图：Subject 只负责"通知到了"，
     * 至于怎么呈现，是每个观察者自己的事。
     *
     * @param event 事件，非空
     */
    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        System.out.println("  [站内信] " + displayName + " ← " + event.describe()
                + "｜" + event.summary());
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
