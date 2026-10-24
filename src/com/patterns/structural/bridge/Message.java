package com.patterns.structural.bridge;

import java.util.Objects;

/**
 * 抽象部分：消息。
 *
 * <p>桥接模式的"抽象维度"。持有 {@link MessageSender} 引用（桥），
 * 把"发送"委托给实现部分。
 *
 * <p>新增一种消息类型（如定时消息），只需新增一个子类，
 * 不影响实现维度（渠道）。
 *
 * @author design-patterns-pro
 */
public abstract class Message {

    /** 桥：持有实现部分的引用。 */
    protected final MessageSender sender;

    protected Message(MessageSender sender) {
        // 桥不能是 null：否则要到 send() 里才报 NPE，离真正的错误位置很远
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /**
     * 发送消息。
     *
     * <p>子类负责“这种消息怎么包装内容”，具体“走哪个渠道”由 {@link #sender} 完成。
     * 注意这不是模板方法：这里没有固定骨架，子类完全决定流程。
     *
     * @param content 内容
     */
    public abstract void send(String content);
}
