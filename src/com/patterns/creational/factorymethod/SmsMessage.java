package com.patterns.creational.factorymethod;

/**
 * 具体产品：短信消息。
 *
 * @author design-patterns-pro
 */
public class SmsMessage implements Message {

    @Override
    public void send(String to, String content) {
        System.out.println("[短信] 发送给 " + to + "，内容：" + content);
    }
}
