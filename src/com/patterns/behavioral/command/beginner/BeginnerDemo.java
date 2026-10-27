package com.patterns.behavioral.command.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令模式入门演示：一个只有 4 个任务的夜间批处理。
 *
 * <p>同一个需求写两遍：
 * <ol>
 *   <li>{@link #before()} —— 调度器直接调用业务方法：加任务要改它，一条失败会拖垮后面所有任务；</li>
 *   <li>{@link #after()}  —— 把每次请求包成 Command 对象：调度器只认识 {@code execute()}。</li>
 * </ol>
 *
 * <p><b>这是入门示例，刻意做了两处简化：</b>
 * <ol>
 *   <li>业务失败用 {@link IllegalStateException} 表示，不引入自定义异常体系；</li>
 *   <li>不做撤销、异步、持久化——这些能力分别需要命令自己记录现场、线程池、存储介质，
 *       命令模式只提供「请求变成对象」这个结构，不负责替你把它们实现掉。</li>
 * </ol>
 * 完整版见同目录上一级的 {@code Client} 与 {@code README.md}。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        System.out.println("========== 改造前：调度器直接调用业务方法 ==========");
        before();

        System.out.println();
        System.out.println("========== 改造后：调度器只认识 Command ==========");
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        BackupService backup = new BackupService(500);
        ReportService report = new ReportService();
        EmailService email = new EmailService();

        NightlyBatch batch = new NightlyBatch(backup, report, email);
        System.out.println("今晚的任务：备份 orders_db → 备份 logs_db → 生成日报 → 发通知");
        batch.runAll();

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 库名、报表名、收件人都写死在 runAll() 里，换一个就要改这个类；");
        System.out.println("  2. logs_db 失败后，报表和通知被一起跳过；");
        System.out.println("  3. 日志只有「批处理失败」，不知道是哪条、为什么；");
        System.out.println("  4. 失败的那条没有留下对象，明天没法只重跑它。");
    }

    /**
     * 改造前的批处理：一个方法包办「有哪些任务」和「怎么执行」。
     */
    static class NightlyBatch {

        private final BackupService backup;
        private final ReportService report;
        private final EmailService email;

        NightlyBatch(BackupService backup, ReportService report, EmailService email) {
            this.backup = backup;
            this.report = report;
            this.email = email;
        }

        void runAll() {
            try {
                backup.backup("orders_db", 400);
                // ↓ 新需求：把 logs_db 也纳入。只能改这里，还要重新测试整个批处理。
                backup.backup("logs_db", 600);
                report.generate("每日销售报表", 1520);
                email.send("ops@shop.com", "夜间批处理完成", "共 4 项");
            } catch (IllegalStateException e) {
                System.out.println("[批处理] 失败：" + e.getMessage());
            }
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        BackupService backup = new BackupService(500);
        ReportService report = new ReportService();
        EmailService email = new EmailService();

        Scheduler scheduler = new Scheduler();
        scheduler.submit(new BackupCommand(backup, "orders_db", 400));
        scheduler.submit(new BackupCommand(backup, "logs_db", 600));
        scheduler.submit(new ReportCommand(report, "每日销售报表", 1520));
        scheduler.submit(new EmailCommand(email, "ops@shop.com", "夜间批处理完成", "请查收日报"));

        System.out.println();
        System.out.println("—— 第一轮：一次交给调度器，按入队顺序执行 ——");
        scheduler.run();

        System.out.println();
        System.out.println("—— 运维清理磁盘后：只重试失败的那条 ——");
        backup.freeDisk(600);
        scheduler.retryFailed();

        System.out.println();
        scheduler.printHistory();

        System.out.println();
        System.out.println("—— 新增一种任务：清理临时文件 ——");
        scheduler.submit(new CleanTempCommand());
        scheduler.run();
        System.out.println();
        System.out.println("CleanTempCommand 是新写的类；Scheduler 的源码一行都没改。");
        System.out.println("代价：4 种任务 = 1 个接口 + 4 个命令类 + 1 个调度器。");
    }

    /**
     * 命令：调用者唯一认识的类型。
     *
     * <p>它只有两件事：<b>说自己是谁</b>（给日志看）、<b>能被统一执行</b>（给调度器调）。
     * 调度器不需要知道背后是备份、报表还是发邮件。
     */
    interface Command {

        /** 命令名，会出现在排队、失败和历史记录里。 */
        String name();

        /** 执行。业务上做不到时抛 {@link IllegalStateException}。 */
        void execute();
    }

    /** 具体命令：持有「谁来做」和「做的参数」，把两者绑成一次请求。 */
    static class BackupCommand implements Command {

        private final BackupService backup;   // 谁来做（Receiver）
        private final String database;        // 做什么的参数
        private final int sizeMb;             // 做什么的参数

        BackupCommand(BackupService backup, String database, int sizeMb) {
            this.backup = backup;
            this.database = database;
            this.sizeMb = sizeMb;
        }

        @Override
        public String name() {
            return "备份 " + database + "（" + sizeMb + "MB）";
        }

        @Override
        public void execute() {
            backup.backup(database, sizeMb);
        }
    }

    static class ReportCommand implements Command {

        private final ReportService report;
        private final String title;
        private final int rows;

        ReportCommand(ReportService report, String title, int rows) {
            this.report = report;
            this.title = title;
            this.rows = rows;
        }

        @Override
        public String name() {
            return "生成报表 " + title;
        }

        @Override
        public void execute() {
            report.generate(title, rows);
        }
    }

    static class EmailCommand implements Command {

        private final EmailService email;
        private final String to;
        private final String subject;
        private final String body;

        EmailCommand(EmailService email, String to, String subject, String body) {
            this.email = email;
            this.to = to;
            this.subject = subject;
            this.body = body;
        }

        @Override
        public String name() {
            return "发邮件给 " + to;
        }

        @Override
        public void execute() {
            email.send(to, subject, body);
        }
    }

    /**
     * 新任务：只新增这一个类，Scheduler 不用改。
     *
     * <p>注意它没有独立的 Receiver——动作只有一行，命令自己做完即可。
     * 但只要动作背后有真正的业务对象（数据库、邮件服务器），就应该像上面三个命令那样
     * 把活交给 Receiver，否则「命令」又会变回「厨师」。
     */
    static class CleanTempCommand implements Command {

        @Override
        public String name() {
            return "清理临时文件";
        }

        @Override
        public void execute() {
            System.out.println("    删除 3 个临时文件，释放 12MB");
        }
    }

    /**
     * 调用者（Invoker）：只负责排队、何时执行、记录结果。
     *
     * <p>通读这个类，找不到 BackupService、ReportService、EmailService 任何一个名字，
     * 也没有 if/else 判断任务类型。<b>这就是命令模式买到的解耦。</b>
     */
    static class Scheduler {

        private final List<Command> queue = new ArrayList<>();
        private final List<Command> failed = new ArrayList<>();
        private final List<String> history = new ArrayList<>();
        private int seq = 0;

        void submit(Command command) {
            queue.add(command);
            System.out.println("[调度器] 入队：" + command.name() + "（待执行 " + queue.size() + " 条）");
        }

        void run() {
            // 先复制一份再清空：本次只执行「进入时已经在队列里」的那批。
            List<Command> batch = new ArrayList<>(queue);
            queue.clear();

            int ok = 0;
            for (Command command : batch) {
                seq++;
                System.out.println("[" + seq + "] " + command.name());
                try {
                    command.execute();
                    history.add(seq + ". " + command.name() + " → 成功");
                    System.out.println("    √ 成功");
                    ok++;
                } catch (IllegalStateException e) {
                    // 只接住业务失败；空指针之类的程序 bug 会直接抛出去。
                    history.add(seq + ". " + command.name() + " → 失败：" + e.getMessage());
                    failed.add(command);   // 失败的命令留在手里，才有「重试」可言
                    System.out.println("    × 失败：" + e.getMessage());
                }
            }
            System.out.println("[调度器] 本轮：共 " + batch.size() + " 条，成功 " + ok
                    + " 条，失败 " + (batch.size() - ok) + " 条，待重试 " + failed.size() + " 条");
        }

        void retryFailed() {
            if (failed.isEmpty()) {
                System.out.println("[调度器] 没有待重试的任务");
                return;
            }
            System.out.println("[调度器] 重试 " + failed.size() + " 条失败任务");
            queue.addAll(failed);
            failed.clear();
            run();
        }

        void printHistory() {
            System.out.println("[调度器] 执行历史：");
            for (String line : history) {
                System.out.println("    " + line);
            }
        }
    }

    // ==================================================================
    // 三个「接收者」：普通业务类，完全不知道 Command 和 Scheduler 的存在
    // ==================================================================

    static class BackupService {

        private int freeDiskMb;

        BackupService(int freeDiskMb) {
            this.freeDiskMb = freeDiskMb;
        }

        void backup(String database, int sizeMb) {
            if (sizeMb > freeDiskMb) {
                throw new IllegalStateException("磁盘只剩 " + freeDiskMb + "MB，放不下 " + sizeMb + "MB");
            }
            freeDiskMb -= sizeMb;
            System.out.println("    [备份] " + database + " 完成，剩余磁盘 " + freeDiskMb + "MB");
        }

        void freeDisk(int mb) {
            freeDiskMb += mb;
            System.out.println("    [运维] 清理出 " + mb + "MB，可用磁盘 " + freeDiskMb + "MB");
        }
    }

    static class ReportService {

        void generate(String title, int rows) {
            if (rows == 0) {
                throw new IllegalStateException("没有数据，报表不生成");
            }
            System.out.println("    [报表] " + title + " 生成完毕，共 " + rows + " 行");
        }
    }

    static class EmailService {

        void send(String to, String subject, String body) {
            if (to == null || !to.contains("@")) {
                throw new IllegalStateException("收件人地址不合法：" + to);
            }
            System.out.println("    [邮件] 已发送给 " + to + "，主题：" + subject);
        }
    }
}
