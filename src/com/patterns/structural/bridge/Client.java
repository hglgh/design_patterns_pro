package com.patterns.structural.bridge;

/**
 * 桥接模式客户端演示。
 *
 * <p>展示"两个维度独立扩展"：
 * <ul>
 *   <li>抽象维度：NormalMessage / UrgentMessage / ScheduledMessage</li>
 *   <li>实现维度：EmailSender / SmsSender / WeChatSender / DingTalkSender</li>
 * </ul>
 *
 * <p>M × N 的组合，只需 M + N 个类，而不是 M × N 个类。
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        System.out.println("========== 桥接模式演示 ==========\n");

        // 创建实现维度（渠道）
        MessageSender email = new EmailSender();
        MessageSender sms = new SmsSender();
        MessageSender wechat = new WeChatSender();
        MessageSender dingtalk = new DingTalkSender();

        // 组合：抽象维度 × 实现维度
        System.out.println("--- 普通通知 × 邮件 ---");
        new NormalMessage(email).send("欢迎使用我们的系统！");

        System.out.println("\n--- 紧急告警 × 短信 ---");
        new UrgentMessage(sms).send("服务器 CPU 负载过高！");

        System.out.println("\n--- 紧急告警 × 微信 ---");
        new UrgentMessage(wechat).send("数据库连接异常！");

        System.out.println("\n--- 定时消息 × 钉钉（扩展演示）---");
        new ScheduledMessage(dingtalk, 5000).send("明日 10:00 例会");

        System.out.println("\n--- 普通通知 × 钉钉（扩展演示）---");
        new NormalMessage(dingtalk).send("新版本已发布");

        System.out.println("\n========== 维度独立扩展验证 ==========");
        System.out.println("新增 ScheduledMessage（抽象维度）：未修改任何 Sender 类");
        System.out.println("新增 DingTalkSender（实现维度）：未修改任何 Message 类");
    }
}
