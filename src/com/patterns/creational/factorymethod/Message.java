package com.patterns.creational.factorymethod;

/**
 * 抽象产品：消息。
 *
 * <p>定义所有具体消息必须实现的统一行为 {@link #send(String, String)}。
 * 使用方只依赖此接口，不关心具体是短信、邮件还是站内信。
 *
 * @author design-patterns-pro
 */
public interface Message {

    /**
     * 发送消息。
     *
     * @param to      接收者（手机号 / 邮箱 / 用户 ID）
     * @param content 消息内容
     */
    void send(String to, String content);
}
