package com.patterns.behavioral.command;

import java.util.Objects;

/**
 * 具体命令：发送一封通知邮件。
 *
 * <p><b>这个类只有 20 行业务代码，但它体现了命令模式最关键的三件事：</b>
 *
 * <p><b>1. 它自己不干活。</b>
 * {@code execute()} 里只做一件事——调用 {@link EmailService#send}。
 * 命令是"请求的封装"，不是"执行的实现"。如果哪天发邮件的逻辑要改
 * （换 SMTP、加重试、加模板），改的是 Receiver，<b>这个类一行都不用动</b>。
 * 反观原项目的 {@code EmailTask}：
 * <pre>
 *   public void execute() {
 *       System.out.println("发送通知邮件...");
 *       // 实际的发邮件逻辑      ← 业务逻辑和"任务"这个概念焊死在一起
 *   }
 * </pre>
 *
 * <p><b>2. 参数是字段，所以"请求"变成了"数据"。</b>
 * {@code to / subject / body} 这三个参数本来只能出现在方法调用的栈上，
 * 调用一结束就没了。现在它们是对象的字段，于是这条请求可以被
 * <b>排队</b>、<b>落库</b>（把字段序列化成一行记录）、<b>重放</b>（从记录里还原出对象再执行一次）。
 * 这不是"为了好看"，而是任务调度系统能不能做到"进程重启后继续跑"的分水岭。
 *
 * <p><b>3. 它不实现 {@link UndoableCommand}。</b>
 * 因为邮件<b>发出去就收不回来</b>。这不是没做，是做不了。
 * 承认这一点，才能推出"不可逆操作要放在流程最后"这条工程原则。
 *
 * @author design-patterns-pro
 * @see EmailService 接收者
 */
public class SendEmailCommand implements Command {

    private final EmailService emailService;
    private final String to;
    private final String subject;
    private final String body;

    /**
     * @param emailService 接收者，不能为 {@code null}
     * @param to           收件人
     * @param subject      标题
     * @param body         正文
     */
    public SendEmailCommand(EmailService emailService, String to, String subject, String body) {
        // 在构造期就校验依赖，而不是等 execute() 时抛 NPE ——
        // "错误越早暴露，修复成本越低"，且此处是调用方的编码错误，属于未受检异常。
        this.emailService = Objects.requireNonNull(emailService, "emailService 不能为 null");
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    /**
     * 名称里带上收件人，日志才能定位到具体是哪封邮件。
     *
     * <p>对比原项目的「任务执行成功！」，读者可以体会差别有多大。
     */
    @Override
    public String getName() {
        return "发送邮件 → " + to;
    }

    @Override
    public void execute() throws CommandException {
        try {
            emailService.send(to, subject, body);
        } catch (ServiceException e) {
            // 【命令层的核心职责：翻译】
            // 把接收者的「收件人地址非法：ops」翻译成一条带上下文、但又没有重复信息的失败记录。
            // 注意这里传的是 getName() 和 e.getMessage() 两个参数，而不是自己拼字符串：
            // 拼进 message 会丢失结构，调用方想单独取「原因」时就只能去拆分字符串。
            throw new CommandException(getName(), e.getMessage(), e);
        }
    }
}
