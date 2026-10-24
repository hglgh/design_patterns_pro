package com.patterns.creational.factorymethod;

import java.util.Objects;

/**
 * 使用方：通知服务。
 *
 * <p>只依赖抽象（{@link MessageFactory} 与 {@link Message}），不关心具体实现。
 * 通过构造器注入工厂，体现"依赖倒置原则"。
 *
 * @author design-patterns-pro
 */
public final class NotificationService {

    private final MessageFactory messageFactory;

    /**
     * 构造器注入具体工厂。
     *
     * @param messageFactory 消息工厂
     */
    public NotificationService(MessageFactory messageFactory) {
        // 工厂是使用方唯一依赖的实现入口，为 null 时要尽早报错，而不是等到 notifyUser()
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory");
    }

    /**
     * 发送通知。
     *
     * @param to      接收者
     * @param content 内容
     */
    public void notifyUser(String to, String content) {
        Message message = messageFactory.createMessage();
        message.send(to, content);
    }
}
