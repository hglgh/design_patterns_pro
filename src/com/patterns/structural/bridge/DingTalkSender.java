package com.patterns.structural.bridge;

/**
 * 具体实现：钉钉发送（演示"实现维度独立扩展"）。
 *
 * <p>新增一种渠道，只需新增一个 MessageSender 实现类，
 * 不需要修改 Message 及其子类。
 *
 * @author design-patterns-pro
 */
public class DingTalkSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [钉钉] " + message);
    }
}
