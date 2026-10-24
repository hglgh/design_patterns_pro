package com.patterns.structural.bridge;

/**
 * 具体实现：微信发送。
 *
 * @author design-patterns-pro
 */
public class WeChatSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [微信] " + message);
    }
}
