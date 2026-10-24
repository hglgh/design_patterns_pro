package com.patterns.structural.bridge;

/**
 * 具体实现：短信发送。
 *
 * @author design-patterns-pro
 */
public class SmsSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [短信] " + message);
    }
}
