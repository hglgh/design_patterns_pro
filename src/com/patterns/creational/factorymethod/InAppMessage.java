package com.patterns.creational.factorymethod;

/**
 * 具体产品：站内信消息。
 *
 * @author design-patterns-pro
 */
public class InAppMessage implements Message {

    @Override
    public void send(String to, String content) {
        System.out.println("[站内信] 推送给用户 " + to + "，内容：" + content);
    }
}
