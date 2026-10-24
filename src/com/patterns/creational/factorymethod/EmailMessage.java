package com.patterns.creational.factorymethod;

/**
 * 具体产品：邮件消息。
 *
 * @author design-patterns-pro
 */
public class EmailMessage implements Message {

    @Override
    public void send(String to, String content) {
        System.out.println("[邮件] 发送给 " + to + "，内容：" + content);
    }
}
