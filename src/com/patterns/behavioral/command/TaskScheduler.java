package com.patterns.behavioral.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Invoker（调用者）：任务调度器。
 *
 * <p><b>这个类里最重要的特征是"它不认识任何接收者"。</b>
 * 通读全文，你不会找到 {@code EmailService}、{@code BackupService}、
 * {@code ReportService}、{@code ConfigService} 中的任何一个名字，
 * 也不会找到 {@code if (task instanceof ...)}。
 * 它只认识 {@link Command} 这一个接口。
 *
 * <p>这就是命令模式最核心的解耦：<b>「谁来执行」和「执行什么」被彻底分开了</b>。
 * 于是同一个调度器可以跑邮件、备份、报表、配置变更，
 * 甚至跑一个把四者串起来的 {@link MacroCommand}——<b>一行代码都不用改</b>。
 *
 * <p><b>原项目的 {@code TaskScheduler} 有四个具体问题，本类逐个修掉：</b>
 * <table border="1">
 *   <caption>原项目 Invoker 的问题与修复</caption>
 *   <tr><th>#</th><th>原项目</th><th>后果</th><th>本项目的做法</th></tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@code catch (Exception e)}</td>
 *     <td>把 NPE 这类程序 bug 和"磁盘满了"混为一谈，bug 被伪装成业务失败</td>
 *     <td>只 {@code catch (CommandException)}，其余异常直接穿出去</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>只打印"任务执行失败"，{@code e} 从不使用</td>
 *     <td>日志里没有任何失败原因，等于没写</td>
 *     <td>打印 {@code e.getMessage()} 并存入执行记录</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>{@code poll()} 把任务从队列里取走</td>
 *     <td>失败的任务<b>消失了</b>，无法重试，也无法重跑</td>
 *     <td>失败的命令留在 {@code failed} 列表里，{@link #retryFailed()} 可重试</td>
 *   </tr>
 *   <tr>
 *     <td>4</td>
 *     <td>打印"任务执行成功！"，没有任务名</td>
 *     <td>不知道是哪一个任务</td>
 *     <td>打印 {@code command.getName()}，含关键参数</td>
 *   </tr>
 * </table>
 *
 * <p><b>为什么本类不记录耗时？</b>
 * 这是一个刻意的取舍：{@code System.nanoTime()} 的差值会让每次运行输出都不一样，
 * 而<b>本项目的所有输出都要能被读者原样复现</b>。
 * 真实系统当然要记耗时（那是性能分析的基础），但教学代码优先保证「可对照」。
 * <b>什么时候该放弃一点真实性，是为了让另一件事更清楚——这个决定必须是有意识的。</b>
 *
 * @author design-patterns-pro
 * @see Command
 */
public class TaskScheduler {

    /**
     * 一条执行记录（审计日志）。
     *
     * <p>用 {@code record}（JDK 16+）而不是普通类：它是纯粹的数据载体，
     * 没有行为、没有可变状态，正是 record 的适用场景。
     *
     * @param seq          执行序号（从 1 开始）
     * @param commandName  命令名称
     * @param success      是否成功
     * @param errorMessage 失败原因；成功时为 {@code null}
     */
    public record ExecutionRecord(int seq, String commandName, boolean success, String errorMessage) {
    }

    private final Deque<Command> queue = new ArrayDeque<>();
    private final List<ExecutionRecord> history = new ArrayList<>();
    private final List<Command> failed = new ArrayList<>();
    private int sequence = 0;

    /**
     * 提交一条命令到队列。
     *
     * <p>注意这里<b>只是入队，不执行</b>。
     * 什么时候执行由 Invoker 决定——这就是命令模式相对"直接方法调用"多出来的那个自由度：
     * <b>请求的"提出"和"执行"在时间上被解耦了</b>。
     *
     * @param command 命令，不能为 {@code null}
     */
    public void submit(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("command 不能为 null");
        }
        queue.offer(command);
        System.out.println("  [调度器] 入队：" + command.getName() + "（待执行 " + queue.size() + " 条）");
    }

    /** @return 队列中还剩多少条命令 */
    public int pendingCount() {
        return queue.size();
    }

    /** @return 失败待重试的命令（快照） */
    public List<Command> getFailedCommands() {
        return List.copyOf(failed);
    }

    /** @return 执行历史（快照） */
    public List<ExecutionRecord> getHistory() {
        return List.copyOf(history);
    }

    /**
     * 按提交顺序执行队列中的<b>全部</b>命令。
     *
     * <p><b>关键设计 1：先做快照，再清队列。</b>
     * 如果直接 {@code while (!queue.isEmpty()) { queue.poll(); ... }}，
     * 那么命令在执行过程中再提交新命令就会陷入不可预期的循环。
     * 快照的语义是明确的：<b>本次 run() 只处理"进入时已经在队列里"的那批</b>，
     * 执行期间新提交的，留给下一次 run()。
     *
     * <p><b>关键设计 2：一条失败不影响后续。</b>
     * 批量任务里最常见的需求是"跑完所有能跑的"，
     * 而不是"第一个失败就全停"。所以这里逐条 try-catch，
     * 并把失败的命令留档。
     *
     * @return 本次执行成功的命令数量
     */
    public int run() {
        if (queue.isEmpty()) {
            System.out.println("  [调度器] 队列为空，没有任务可执行");
            return 0;
        }

        List<Command> batch = new ArrayList<>(queue);
        queue.clear();

        int ok = 0;
        for (Command command : batch) {
            sequence++;
            System.out.println();
            System.out.println("  [" + sequence + "] " + command.getName());
            try {
                command.execute();
                history.add(new ExecutionRecord(sequence, command.getName(), true, null));
                System.out.println("        √ 成功");
                ok++;
            } catch (CommandException e) {
                // 【只捕获这一种】—— 精确的网眼。
                // NullPointerException、IllegalStateException 会直接穿过 run() 抛给调用方，
                // 让程序 bug 当场暴露，而不是被伪装成"这条任务今天没跑成"。
                // 存 getDetail() 而不是 getMessage()：表格里已经有一列是命令名，不必重复。
                history.add(new ExecutionRecord(sequence, command.getName(), false, e.getDetail()));
                failed.add(command);
                System.out.println("        × 失败：" + e.getDetail());
            }
        }

        System.out.println();
        System.out.println("  [调度器] 本轮结束：共 " + batch.size() + " 条，成功 " + ok
                + " 条，失败 " + (batch.size() - ok) + " 条，累计待重试 " + failed.size() + " 条");
        return ok;
    }

    /**
     * 重试所有失败的命令。
     *
     * <p><b>为什么原项目做不到这件事？</b>
     * 因为它用 {@code poll()} 取任务，失败的那条已经被从队列里<b>拿走了</b>，
     * 方法返回之后程序里再也找不到这个对象。
     * 本项目把它留在 {@code failed} 里，重试才有对象可重试。
     *
     * <p><b>为什么重试是安全的？</b>
     * 因为只重试<b>失败</b>的命令，而失败的命令内部"现场字段"仍然是空的
     * （{@code created == null}、{@code createdFileName == null}），
     * 重新执行不会覆盖任何已记录的状态。
     * <b>已经成功的命令绝不重跑</b>——那会把它的现场字段改成第二次执行的结果，
     * 让之前那份撤销记录失效（比如把备份 id 换成新的，再去撤销就删错文件了）。
     *
     * @return 本次重试成功的命令数量
     */
    public int retryFailed() {
        if (failed.isEmpty()) {
            System.out.println("  [调度器] 没有待重试的任务");
            return 0;
        }
        System.out.println("  [调度器] 重试 " + failed.size() + " 条失败任务");
        List<Command> retry = new ArrayList<>(failed);
        failed.clear();
        for (Command command : retry) {
            queue.offer(command);
        }
        return run();
    }

    /**
     * 打印执行历史（审计报表）。
     *
     * <p>这段输出本身就是命令模式的一个副产品：
     * 因为请求是对象，所以它可以被记录成一行行结构化数据。
     * 原项目那套写法里，除了几条散落的 {@code println}，什么记录都不会留下。
     */
    public void printHistory() {
        System.out.println("  ┌─ 执行历史 ────────────────────────────────");
        for (ExecutionRecord r : history) {
            String result = r.success() ? "√ 成功" : "× " + r.errorMessage();
            System.out.println("  │ " + r.seq() + ". " + r.commandName() + " → " + result);
        }
        System.out.println("  └───────────────────────────────────────────");
    }
}
