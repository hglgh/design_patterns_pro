package com.patterns.behavioral.command;

/**
 * 命令：<b>把「一次请求」封装成一个对象</b>。
 *
 * <p>这是命令模式的全部出发点。一旦请求变成了对象，它就能被：
 * <ul>
 *   <li><b>排队</b>（放进队列，按顺序或按优先级执行）；</li>
 *   <li><b>记录</b>（写进审计日志、持久化到数据库，进程重启后还能重放）；</li>
 *   <li><b>传递</b>（跨线程投递、跨进程序列化——这正是 RPC 的本质）；</li>
 *   <li><b>组合</b>（多条命令打包成一个宏命令）；</li>
 *   <li><b>撤销</b>（前提是实现 {@link UndoableCommand}）。</li>
 * </ul>
 * 而「一个方法调用」做不到上面任何一件事——方法调用只能<b>立刻发生</b>，发生完就没了。
 *
 * <p><b>五个角色，本项目对应如下</b>：
 * <table border="1">
 *   <caption>命令模式的角色映射</caption>
 *   <tr><th>角色</th><th>职责</th><th>本项目</th></tr>
 *   <tr><td>Command（命令）</td><td>声明执行接口</td><td>{@code Command} / {@code UndoableCommand}</td></tr>
 *   <tr><td>ConcreteCommand（具体命令）</td><td>持有 Receiver + 参数，把两者绑成一次请求</td>
 *       <td>{@code SendEmailCommand} 等 5 个</td></tr>
 *   <tr><td>Receiver（接收者）</td><td>真正干活的业务对象，<b>不知道命令的存在</b></td>
 *       <td>{@code EmailService} / {@code BackupService} / {@code ReportService} / {@code ConfigService}</td></tr>
 *   <tr><td>Invoker（调用者）</td><td>持有命令、决定<b>何时</b>执行，<b>不认识 Receiver</b></td>
 *       <td>{@code TaskScheduler}</td></tr>
 *   <tr><td>Client（客户端）</td><td>创建 Receiver、创建命令、把命令交给 Invoker</td><td>{@code Client}</td></tr>
 * </table>
 *
 * <p><b>原项目缺的正是「Receiver」这一环</b>。它把业务代码直接写在命令里：
 * <pre>
 *   public class BackupTask implements Task {
 *       public void execute() {
 *           System.out.println("正在备份数据库...");
 *           // 实际的备份逻辑         ← 业务代码长在命令里了
 *       }
 *   }
 * </pre>
 * 后果有三个，一个比一个隐蔽：
 * <ol>
 *   <li><b>业务逻辑被绑死在「任务」这个概念上</b>——想在 HTTP 接口里手动触发一次备份？
 *       只能 {@code new BackupTask().execute()}，等于为了用一段逻辑而被迫构造一个命令对象。</li>
 *   <li><b>命令无法携带参数</b>——「备份哪个库」只能写死或做成字段再由外部 set，
 *       而命令对象本该是「请求参数的载体」。</li>
 *   <li><b>Invoker 其实没有解耦对象</b>——命令模式的卖点是「调用者不认识接收者」，
 *       可当命令自己就是接收者时，这条卖点就落空了。</li>
 * </ol>
 *
 * <p><b>为什么接口里有 {@link #getName()}？</b>
 * 原项目的 Invoker 打印的是：
 * <pre>
 *   任务执行成功！        ← 哪个任务？不知道
 *   任务执行失败，记录日志以供重试...   ← 哪个任务失败了？还是不知道
 * </pre>
 * 生产环境里一条「任务失败」的日志如果定位不到对象，等于没写。
 * 命令<b>自己</b>提供名字（而不是让调用方硬编码字符串），
 * 才能写出 {@code "[" + cmd.getName() + "] 失败"} 这种通用日志。
 * 这和策略模式里 {@code DiscountStrategy.name()} 是同一个设计取舍。
 *
 * <p><b>为什么这里没有 {@code undo()}？</b>
 * 见 {@link UndoableCommand} 的类注释——不是所有命令都能撤销，
 * 把 {@code undo()} 硬塞进这个接口，等于强迫每条命令都去实现一个它做不到的方法。
 *
 * @author design-patterns-pro
 * @see UndoableCommand 可撤销的命令
 * @see CommandException 执行失败的统一异常
 */
public interface Command {

    /**
     * 命令的可读名称，用于日志、审计和界面展示。
     *
     * <p><b>约定</b>：应当包含<b>关键参数</b>，让人不看代码就知道这条命令要干什么。
     * <ul>
     *   <li>✅ {@code "备份数据库 orders_db（400MB）"}</li>
     *   <li>❌ {@code "BackupDatabaseCommand"}——类名对运维人员没有意义；</li>
     *   <li>❌ {@code "备份"}——不知道备份哪个库，出问题时还得去翻调用栈。</li>
     * </ul>
     *
     * @return 命令名称，不应为 {@code null} 或空串
     */
    String getName();

    /**
     * 执行这条命令。
     *
     * <p><b>契约</b>：
     * <ol>
     *   <li><b>失败必须通过 {@link CommandException} 报出</b>，
     *       不允许像原项目那样在命令内部 {@code catch} 掉再打印一行字——
     *       那会让 Invoker 误以为任务成功了。</li>
     *   <li><b>程序 bug 不要捕获</b>（{@code NullPointerException}、{@code IndexOutOfBoundsException}），
     *       让它们直接抛出去。见 {@code Client} 的 demo3。</li>
     *   <li>命令可以持有<b>可变状态</b>（比如为了 undo 而记录"执行前的值"），
     *       因此<b>一个命令实例只应执行一次</b>；要重跑就重新构造一个对象。
     *       这条约束由 Invoker 的失败重试机制保证——见 {@code TaskScheduler#retryFailed()}。</li>
     * </ol>
     *
     * @throws CommandException 命令未能完成（业务失败，非程序 bug）
     */
    void execute() throws CommandException;
}
