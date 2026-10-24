package com.patterns.behavioral.observer;

import java.util.Objects;

/**
 * 一次投递的结果：发给谁、成功还是失败、失败原因是什么。
 *
 * <p><b>为什么需要这个类？直接不记录不行吗？</b>
 * 因为原项目的 {@code notifyFollowers} 是一个 {@code void} 方法：
 *
 * <pre>
 *   public void notifyFollowers(String postContent) {
 *       for (Follower f : followers) {
 *           f.update(userName, postContent);      // 谁成功谁失败，没人知道
 *       }
 *   }
 * </pre>
 *
 * 一旦某个 {@code update} 抛异常，整个循环中断，<b>前面发到哪了？
 * 后面几个人没收到？</b>——发布者完全无法回答。更糟的是异常会冒泡给
 * 调用 {@code post()} 的人，让他以为"发布失败了"，可实际上内容早就发出去了。
 *
 * <p><b>投递结果必须成为数据</b>，而不是"有异常就代表失败"这种隐式约定。
 * 有了它，发布方才能回答三个运营一定会问的问题：
 * <ol>
 *   <li>这次推送覆盖了多少人？</li>
 *   <li>哪些人失败了？为什么？</li>
 *   <li>是不是某一个人的通道坏了，拖累了所有人？</li>
 * </ol>
 *
 * <p><b>为什么用 {@code record} 而不是普通类？</b>
 * 它是一次调用的结果快照，构造完就不该再变——没有 setter、没有 setter 的必要。
 * {@code record} 天然不可变，还顺带给了 {@code equals}/{@code toString}，
 * 测试里可以直接断言整个报告对象。
 *
 * <p><b>为什么 {@code errorMessage} 是 {@code String} 而不是 {@code Throwable}？</b>
 * 因为报告会被打印、被序列化、被塞进日志系统。
 * 持有 {@code Throwable} 就等于持有整条异常链和它引用的所有对象（可能包括
 * 连接、缓冲区），长时间留在内存里就是泄漏。这里只留一行文字，
 * 需要堆栈的时候去日志里找——那是日志系统该干的事。
 *
 * @param observerId   观察者 ID，与 {@link Observer#id()} 一致
 * @param observerName 观察者展示名，与 {@link Observer#displayName()} 一致
 * @param succeeded    是否投递成功
 * @param errorMessage 失败原因；成功时为 {@code null}
 * @author design-patterns-pro
 */
public record Delivery(
        String observerId,
        String observerName,
        boolean succeeded,
        String errorMessage
) {

    /**
     * 紧凑构造器：两个标识非空，并且"成功就没有错因、失败就必须有错因"。
     *
     * <p>后半句是本类最重要的不变量。少了它，就可能出现
     * {@code succeeded=true, errorMessage="超时"} 这种自相矛盾的记录——
     * 打印出来谁也解释不清到底是成了还是败了。
     */
    public Delivery {
        Objects.requireNonNull(observerId, "观察者 ID 不能为 null");
        Objects.requireNonNull(observerName, "观察者展示名不能为 null");
        if (succeeded && errorMessage != null) {
            throw new IllegalArgumentException("投递成功时不应携带失败原因：" + observerId);
        }
        if (!succeeded && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("投递失败时必须给出失败原因：" + observerId);
        }
    }

    /**
     * 构造一条成功记录。
     *
     * <p>提供静态工厂而不是让调用方写 {@code new Delivery(id, name, true, null)}：
     * 那个 {@code null} 每次都要调用方自己记住"成功时必须传 null"。
     * 工厂方法把这个知识收进了本类。
     *
     * @param observer 投递成功的观察者
     * @return 成功记录
     */
    public static Delivery ok(Observer observer) {
        Objects.requireNonNull(observer, "观察者不能为 null");
        return new Delivery(observer.id(), observer.displayName(), true, null);
    }

    /**
     * 构造一条失败记录。
     *
     * <p>错因取的是 {@code 异常类名 + 消息}，例如
     * {@code IllegalStateException: 推送通道已关闭}。
     * 用类名而不是 {@code e.toString()} 是为了让输出稳定、不受 JDK 版本影响。
     *
     * @param observer 投递失败的观察者
     * @param cause    捕获到的异常
     * @return 失败记录
     */
    public static Delivery failed(Observer observer, RuntimeException cause) {
        Objects.requireNonNull(observer, "观察者不能为 null");
        Objects.requireNonNull(cause, "异常不能为 null");
        String message = cause.getMessage();
        String reason = cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return new Delivery(observer.id(), observer.displayName(), false, reason);
    }

    /**
     * 一行式描述，用于打印报告。
     *
     * @return 形如 {@code [成功] 站内信·张三} 或 {@code [失败] 推送·李四 — 原因} 的文本
     */
    public String describe() {
        if (succeeded) {
            return "[成功] " + observerName;
        }
        return "[失败] " + observerName + " — " + errorMessage;
    }
}
