package com.patterns.structural.bridge;

/**
 * 扩充抽象：普通消息。
 *
 * @author design-patterns-pro
 */
public class NormalMessage extends Message {

    public NormalMessage(MessageSender sender) {
        super(sender);
    }

    @Override
    public void send(String content) {
        System.out.println("【普通通知】");
        sender.send(content);
    }
}
