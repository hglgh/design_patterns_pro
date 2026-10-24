package com.patterns.creational.factorymethod;

/**
 * 具体工厂：站内信消息工厂。
 *
 * @author design-patterns-pro
 */
public class InAppMessageFactory implements MessageFactory {

    @Override
    public Message createMessage() {
        return new InAppMessage();
    }
}
