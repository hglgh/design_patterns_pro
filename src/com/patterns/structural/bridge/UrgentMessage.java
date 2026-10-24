package com.patterns.structural.bridge;

/**
 * 扩充抽象：紧急消息。
 *
 * @author design-patterns-pro
 */
public class UrgentMessage extends Message {

    public UrgentMessage(MessageSender sender) {
        super(sender);
    }

    @Override
    public void send(String content) {
        System.out.println("【紧急告警】");
        sender.send("【加急】" + content);
    }
}
