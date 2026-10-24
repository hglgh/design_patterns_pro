package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 一条协同消息：谁发的、什么性质、正文是什么。
 *
 * <p><b>为什么要把这三个字段包成一个对象？</b>
 * 中介者的入参本来可以写成三个散装参数：
 *
 * <pre>
 *   void say(Participant from, MessageKind kind, String text);
 *   void receive(Role from, MessageKind kind, String text);
 * </pre>
 *
 * 只要一处不对齐，参数就会错位 —— 而且三个参数里有两个是"枚举 + 字符串"
 * 的组合，编译器很难帮你发现问题。把"一条消息"封装成一个不可变对象，
 * 换来三件事：
 *
 * <ol>
 *   <li><b>中转不丢字段。</b>中枢收到的是 {@code Message}，转发的也是
 *       同一个 {@code Message}，不需要拆开再拼起来。</li>
 *   <li><b>校验只写一次。</b>正文非空、类型非空都在紧凑构造器里检查，
 *       不会出现"某一条投递路径忘了校验"。</li>
 *   <li><b>以后加字段不用改签名。</b>比如加一个 {@code timestamp}
 *       或 {@code messageId}，只改这个 record，投递链路上所有方法都不用动。</li>
 * </ol>
 *
 * <p><b>为什么是 record 而不是普通类？</b>
 * 消息是典型的值对象：创建之后不该被修改，两个字段相同的消息就是同一条消息。
 * {@code record} 自动给了 {@code equals} / {@code hashCode} / {@code toString}，
 * 而且所有字段隐式 {@code final} —— 这正是"消息一旦发出就不该被改写"的语义。
 *
 * <p>注意 {@link #from()} 记的是<b>角色</b>而不是参与者对象。
 * 这样消息不会因为"持有某个参与者的引用"而延长它的生命周期，
 * 也避免了"转发时把发送者自己又发回去"这类错误。
 *
 * @param from 发送方的角色
 * @param kind 消息的性质，决定它走哪条路由
 * @param text 正文，不能为空白
 * @author design-patterns-pro
 */
public record Message(Role from, MessageKind kind, String text) {

    /**
     * 紧凑构造器：只做校验与规范化，不做任何"业务动作"。
     *
     * <p>三个字段全部非空是硬约束。原项目允许 {@code new ChatUser(null, room)}，
     * 然后在广播时打印出 {@code "null：大家好"} —— 一个空名字会在整条链路上
     * 传播很久才被发现。这里在消息创建的那一刻就拦住。
     */
    public Message {
        Objects.requireNonNull(from, "发送方角色不能为 null");
        Objects.requireNonNull(kind, "消息类型不能为 null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("消息正文不能为空白");
        }
        text = text.strip();
    }

    /**
     * 一句话描述，用于所有参与者统一的日志格式。
     *
     * <p>把它放在 {@code Message} 上，而不是每个参与者的 {@code receive()}
     * 里各拼一遍，是为了保证六种角色的日志长得一样 ——
     * 日志格式不一致，是排查协同问题时最常见的时间浪费。
     *
     * @return 形如 {@code 客户 的提问：耳机左耳没声音}
     */
    public String describe() {
        return from.displayName() + " 的" + kind.label() + "：" + text;
    }
}
