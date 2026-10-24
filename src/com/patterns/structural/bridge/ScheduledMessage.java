package com.patterns.structural.bridge;

/**
 * 扩充抽象：定时消息（演示"抽象维度独立扩展"）。
 *
 * <p>新增一种消息类型，只需新增一个 Message 子类，
 * 不需要修改 MessageSender 及其实现类。
 *
 * @author design-patterns-pro
 */
public class ScheduledMessage extends Message {

    private final long delayMillis;

    public ScheduledMessage(MessageSender sender, long delayMillis) {
        super(sender);
        this.delayMillis = delayMillis;
    }

    @Override
    public void send(String content) {
        System.out.println("【定时消息】将在 " + delayMillis + "ms 后发送");
        // 实际场景会用 ScheduledExecutorService，这里简化为立即发送
        sender.send("[定时] " + content);
    }
}
