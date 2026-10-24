package com.patterns.structural.bridge;

/**
 * 实现部分接口：消息发送渠道。
 *
 * <p>桥接模式的"实现维度"。新增一种渠道（如钉钉），
 * 只需新增一个实现类，不影响抽象维度。
 *
 * @author design-patterns-pro
 */
public interface MessageSender {
    void send(String message);
}
