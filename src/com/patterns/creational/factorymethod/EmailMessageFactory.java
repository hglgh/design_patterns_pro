package com.patterns.creational.factorymethod;

/**
 * 具体工厂：邮件消息工厂。
 *
 * @author design-patterns-pro
 */
public class EmailMessageFactory implements MessageFactory {

    @Override
    public Message createMessage() {
        return new EmailMessage();
    }
}
