package com.patterns.creational.factorymethod;

/**
 * 抽象工厂：消息工厂。
 *
 * <p>工厂方法模式的核心 —— 把"创建哪个具体产品"延迟到子类实现。
 * 使用方（{@link NotificationService}）只依赖此接口，不关心具体工厂。
 *
 * @author design-patterns-pro
 */
public interface MessageFactory {

    /**
     * 创建一条消息（具体类型由子类决定）。
     *
     * @return 消息实例
     */
    Message createMessage();
}
