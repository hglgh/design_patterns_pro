package com.patterns.creational.factorymethod;

/**
 * 扩展演示：微信消息工厂（新增具体工厂）。
 *
 * @author design-patterns-pro
 */
class WeChatMessageFactory implements MessageFactory {

    @Override
    public Message createMessage() {
        return new WeChatMessage();
    }
}
