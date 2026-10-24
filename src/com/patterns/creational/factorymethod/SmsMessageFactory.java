package com.patterns.creational.factorymethod;

/**
 * 具体工厂：短信消息工厂。
 *
 * @author design-patterns-pro
 */
public class SmsMessageFactory implements MessageFactory {

    @Override
    public Message createMessage() {
        return new SmsMessage();
    }
}
