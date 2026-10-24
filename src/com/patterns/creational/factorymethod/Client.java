package com.patterns.creational.factorymethod;

/**
 * 工厂方法模式客户端演示。
 *
 * <p>通过切换不同的 {@link MessageFactory}，让 {@link NotificationService}
 * 在不修改自身代码的前提下，发送不同类型的消息。
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        System.out.println("========== 工厂方法模式演示 ==========\n");

        // 场景 1：发送短信
        NotificationService smsService = new NotificationService(new SmsMessageFactory());
        smsService.notifyUser("13812345678", "您的验证码是 123456");

        // 场景 2：发送邮件
        NotificationService emailService = new NotificationService(new EmailMessageFactory());
        emailService.notifyUser("user@example.com", "您的订单已发货");

        // 场景 3：发送站内信
        NotificationService inAppService = new NotificationService(new InAppMessageFactory());
        inAppService.notifyUser("user_1001", "您有一条新的系统通知");

        // 场景 4：演示"新增一种消息类型"的扩展性
        System.out.println("\n========== 扩展：新增微信消息 ==========");
        NotificationService weChatService = new NotificationService(new WeChatMessageFactory());
        weChatService.notifyUser("wx_user_888", "您的好友请求已通过");
    }
}
