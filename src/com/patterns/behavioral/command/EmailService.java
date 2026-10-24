package com.patterns.behavioral.command;

import java.util.ArrayList;
import java.util.List;

/**
 * 接收者：邮件服务。真正"发邮件"的地方。
 *
 * <p><b>请注意这个类里没有出现任何与模式相关的词</b>：没有 {@code Command}、
 * 没有 {@code execute}、没有队列、没有调度。它就是一个普通业务类：
 * 谁调用它都行——定时任务、HTTP 接口、单元测试、命令行工具。
 *
 * <p>这正是命令模式希望接收者保持的样子：<b>可复用的业务能力，不被上层调度概念污染</b>。
 * 原项目把业务代码写进了 {@code EmailTask} 里，于是"发邮件"这件事
 * 就只能以"任务"的形式存在——想在一个 Controller 里发一封通知邮件，
 * 也得先去构造一个任务对象。
 *
 * <p><b>为什么这里没有 {@code recall()}（撤回邮件）？</b>
 * 因为现实里没有这个东西。邮件一旦投递到对方服务器，你就无法"取消"。
 * 这直接决定了一件事：{@link SendEmailCommand} <b>只能是 {@link Command}，
 * 不能是 {@link UndoableCommand}</b>——不是偷懒，是物理世界不允许。
 *
 * <p>由此推出一条非常重要的工程原则（见 {@code Client} 的 demo5）：
 * <b>把不可逆的步骤放在流程的最后</b>。
 * 如果"发邮件"排在"生成报表"前面，那么报表失败时整个流程就无法完整回滚。
 *
 * @author design-patterns-pro
 */
public class EmailService {

    /**
     * 已投递邮件（本项目用它代替真实的 SMTP，充当审计流水）。
     *
     * @param to      收件人
     * @param subject 标题
     * @param body    正文
     */
    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> outbox = new ArrayList<>();

    /**
     * 发送一封邮件。
     *
     * <p><b>为什么地址校验放在接收者，而不是命令里？</b>
     * 因为"什么样的地址算合法"是<b>邮件服务</b>的业务规则，
     * 不是"发送邮件这条命令"的规则。放在接收者里，
     * 任何调用方（不管是不是通过命令）都能获得同样的保护——
     * 这就是把规则放在对的地方的价值。
     *
     * <p>反过来说，如果哪天要改成"只允许公司内网域名"，只改这一个方法即可，
     * 所有命令、所有调用方自动受益。
     *
     * @param to      收件人地址，必须包含 {@code @}
     * @param subject 标题
     * @param body    正文
     * @throws ServiceException 收件人为空或地址格式非法
     */
    public void send(String to, String subject, String body) throws ServiceException {
        if (to == null || to.isBlank()) {
            throw new ServiceException("收件人为空，邮件未发送");
        }
        if (!to.contains("@")) {
            throw new ServiceException("收件人地址非法：" + to);
        }
        outbox.add(new SentMail(to, subject, body));
        System.out.println("        └ 邮件服务：已投递 → " + to + "（标题「" + subject + "」）");
    }

    /**
     * 已投递邮件列表（快照）。
     *
     * <p>用 {@code List.copyOf()} 返回<b>副本</b>而不是 {@code unmodifiableList()} 视图：
     * 这里要的是"调用那一刻的记录"，而不是"能看见后续变化的窗口"。
     *
     * @return 不可变的邮件列表
     */
    public List<SentMail> getOutbox() {
        return List.copyOf(outbox);
    }
}
