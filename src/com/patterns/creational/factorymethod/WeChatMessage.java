package com.patterns.creational.factorymethod;

/**
 * 扩展演示：微信消息（新增具体产品）。
 *
 * <p>用于演示"开闭原则"：新增一种消息类型，只需新增两个类
 * （{@link WeChatMessage} + {@link WeChatMessageFactory}），
 * 不需要修改 {@link NotificationService} 等已有代码。
 *
 * @author design-patterns-pro
 */
class WeChatMessage implements Message {

    @Override
    public void send(String to, String content) {
        System.out.println("[微信] 发送给 " + to + "，内容：" + content);
    }
}
