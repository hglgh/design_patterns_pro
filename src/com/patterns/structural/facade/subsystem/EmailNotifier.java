package com.patterns.structural.facade.subsystem;

/**
 * 子系统 4：邮件通知。负责对外发送各类通知邮件。
 *
 * <p><b>为什么通知要独立成子系统？</b>
 * 发送邮件涉及 SMTP 连接、模板渲染、失败重试等复杂细节，
 * 与"注册业务"本身毫无关系。放进外观会让外观承担两种职责，
 * 而且将来换成短信通知时，必须去改外观代码。
 *
 * @author design-patterns-pro
 */
public class EmailNotifier {

    private int sentCount = 0;

    /**
     * 发送一封邮件（Demo 中只打印，不真发）。
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param body    正文
     */
    public void send(String to, String subject, String body) {
        sentCount++;
        System.out.println("      [Email] → " + to + " | 主题：" + subject + " | 正文：" + body);
    }

    /**
     * @return 已发送邮件数，用于演示"外观能汇总子系统状态"
     */
    public int getSentCount() {
        return sentCount;
    }
}
