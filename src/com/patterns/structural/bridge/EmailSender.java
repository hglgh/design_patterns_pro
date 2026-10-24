package com.patterns.structural.bridge;

/**
 * 具体实现：邮件发送。
 *
 * @author design-patterns-pro
 */
public class EmailSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [邮件] " + message);
    }
}
