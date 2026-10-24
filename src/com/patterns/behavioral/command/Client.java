package com.patterns.behavioral.command;

/**
 * 客户端：五段演示，每段回答一个关于命令模式的具体问题。
 *
 * <ol>
 *   <li>{@link #demo1()} —— Invoker 到底解耦了什么？</li>
 *   <li>{@link #demo2()} —— 一条命令失败，整批任务怎么办？失败的任务去哪了？</li>
 *   <li>{@link #demo3()} —— 业务失败和程序 bug 为什么要分开？</li>
 *   <li>{@link #demo4()} —— 撤销/重做的三条不变量是怎么被保证的？</li>
 *   <li>{@link #demo5()} —— 多条命令要"一起成功或一起回滚"怎么办？</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) throws CommandException {
        demo1();
        demo2();
        demo3();
        demo4();
        demo5();
        System.out.println();
        System.out.println("全部演示结束。");
    }

    // ==================================================================
    // demo1：Invoker 不认识 Receiver
    // ==================================================================

    /**
     * 展示命令模式最核心的解耦：调度器只认识 {@link Command}。
     */
    private static void demo1() {
        printTitle("1. 基本调度：Invoker 完全不认识 Receiver");

        EmailService email = new EmailService();
        BackupService backup = new BackupService(2000);
        ReportService report = new ReportService();

        System.out.println("  客户端手上有 3 个互不相干的服务（Receiver）：");
        System.out.println("      EmailService / BackupService / ReportService");
        System.out.println("  它们都不知道「命令」「调度器」的存在 —— 就是普通业务类。");
        System.out.println();

        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(new BackupDatabaseCommand(backup, "orders_db", 400));
        scheduler.submit(new GenerateReportCommand(report, "每日销售报表", 1520));
        scheduler.submit(new SendEmailCommand(email, "ops@shop.com", "备份完成", "orders_db 已备份"));

        System.out.println();
        System.out.println("  调度器执行时，内部只做这一件事（它眼里只有 Command 接口）：");
        System.out.println("      for (Command c : batch) {");
        System.out.println("          c.execute();");
        System.out.println("      }");

        int ok = scheduler.run();

        System.out.println();
        System.out.println("  >>> " + ok + "/3 成功。整段输出里，调度器的代码没有出现任何 Receiver 的类名，");
        System.out.println("      也没有 instanceof。新增一种命令时，TaskScheduler 一行都不用改。");
    }

    // ==================================================================
    // demo2：失败隔离 + 重试
    // ==================================================================

    /**
     * 展示两件事：失败不会阻塞后续任务；失败的任务不会消失，可以重试。
     */
    private static void demo2() {
        printTitle("2. 失败隔离与重试：失败的任务不会丢失");

        EmailService email = new EmailService();
        BackupService backup = new BackupService(500);
        ReportService report = new ReportService();

        System.out.println("  初始可用磁盘：500MB");
        System.out.println();

        TaskScheduler scheduler = new TaskScheduler();
        scheduler.submit(new BackupDatabaseCommand(backup, "orders_db", 400));   // 能放下
        scheduler.submit(new BackupDatabaseCommand(backup, "logs_db", 600));     // 放不下
        scheduler.submit(new GenerateReportCommand(report, "每日销售报表", 800));
        scheduler.submit(new SendEmailCommand(email, "ops@shop.com", "定时任务", "今日任务已跑"));

        scheduler.run();

        System.out.println();
        System.out.println("  >>> 关键点 1：第 2 条失败了，第 3、4 条照常执行。");
        System.out.println("      批量任务要的是「跑完所有能跑的」，而不是「第一个失败就全停」。");
        System.out.println();
        System.out.println("  >>> 关键点 2：失败原因被完整打印。");
        System.out.println("      原项目是 catch (Exception e) 之后只打印一句「任务执行失败」，e 从头到尾没用过。");
        System.out.println();
        System.out.println("  失败待重试：" + scheduler.getFailedCommands().stream()
                .map(Command::getName).toList());
        System.out.println("  >>> 关键点 3：它还在！原项目用 poll() 取任务，失败的这条已经被从队列里拿走，");
        System.out.println("      方法返回之后程序里再也找不到这个对象，所谓「记录日志以供重试」根本无法实现。");
        System.out.println();

        System.out.println("  —— 运维清理磁盘后重试 ——");
        backup.freeDisk(600);
        scheduler.retryFailed();

        System.out.println();
        scheduler.printHistory();
        System.out.println("  >>> 同一条命令的两次执行都留在历史里：先失败、后成功。");
        System.out.println("      这说明「重试」不是把失败抹掉，而是追加一次新的尝试 —— 审计要的就是这个。");
    }

    // ==================================================================
    // demo3：异常边界
    // ==================================================================

    /**
     * 展示业务失败（{@link CommandException}）与程序 bug（{@link RuntimeException}）的分界。
     */
    private static void demo3() {
        printTitle("3. 异常边界：程序 bug 绝不能被伪装成「任务失败」");

        EmailService email = new EmailService();
        TaskScheduler scheduler = new TaskScheduler();

        scheduler.submit(new SendEmailCommand(email, "bad-address", "标题", "正文"));
        scheduler.submit(buggyCommand());

        System.out.println();
        System.out.println("  第 1 条是业务失败（地址不合法），第 2 条是代码 bug（空指针）。");
        System.out.println();

        try {
            scheduler.run();
            System.out.println("  >>> 没有异常抛出（不应该发生）");
        } catch (RuntimeException e) {
            System.out.println();
            System.out.println("  >>> run() 没有吞掉它，直接抛出来了：");
            System.out.println("      " + e.getClass().getSimpleName() + "：" + e.getMessage());
            System.out.println();
            System.out.println("      这正是想要的结果：");
            System.out.println("        · 业务失败 → CommandException → 记入历史、可以重试；");
            System.out.println("        · 程序 bug  → RuntimeException → 当场炸掉，逼开发者去修。");
            System.out.println("      如果像原项目那样 catch (Exception e) 一把抓，");
            System.out.println("      这个 NPE 会被永远埋在「任务执行失败」这五个字下面。");
        }

        System.out.println();
        System.out.println("  注意：第 2 条抛出后，run() 的循环被中断了 —— 这是有意的。");
        System.out.println("  bug 面前继续跑下去，只会制造更多脏数据。");
        System.out.println();
        scheduler.printHistory();
        System.out.println("  >>> 历史里只有第 1 条。第 2 条没被记录为「失败」，因为它根本不是「业务失败」。");
    }

    /**
     * 构造一条"有 bug"的命令：它忘了判空就调用 {@code length()}。
     *
     * <p>为什么用匿名类而不是再建一个文件？
     * 因为它只是一次性的演示道具，且它<b>恰恰说明命令是个接口</b>——
     * 任何对象只要实现两个方法，就能被调度器执行，不需要继承什么基类。
     */
    private static Command buggyCommand() {
        return new Command() {
            @Override
            public String getName() {
                return "缓存预热（代码有 bug）";
            }

            @Override
            public void execute() {
                // 模拟真实 bug：配置没配就返回 null，而代码忘了判空
                String cacheDir = System.getProperty("app.cache.dir");
                System.out.println("        └ 缓存目录长度：" + cacheDir.length());
            }
        };
    }

    // ==================================================================
    // demo4：撤销 / 重做
    // ==================================================================

    /**
     * 展示双栈撤销/重做的三条不变量。
     */
    private static void demo4() throws CommandException {
        printTitle("4. 撤销与重做：命令对象记住了「执行前的现场」");

        ConfigService config = new ConfigService(
                "payment.timeout", "10s",
                "retry.max", "3");
        CommandHistory history = new CommandHistory();

        System.out.println("  初始配置：" + config.snapshot());
        System.out.println();

        System.out.println("  —— 执行两条命令 ——");
        history.execute(new UpdateConfigCommand(config, "payment.timeout", "30s"));
        history.execute(new UpdateConfigCommand(config, "feature.newCheckout", "on"));
        System.out.println();
        System.out.println("  当前配置：" + config.snapshot());
        System.out.println();

        System.out.println("  —— 撤销两次 ——");
        history.undo();
        history.undo();
        System.out.println();
        System.out.println("  撤销后：" + config.snapshot());
        System.out.println("  >>> 注意 feature.newCheckout 是【消失】，不是变成空串。");
        System.out.println("      它原本就不存在，正确的补偿是 remove() 而不是 set(\"\")。");
        System.out.println("      「不存在」和「值为空」是两件事，混为一谈会给下游留下一个假配置。");
        System.out.println();

        System.out.println("  —— 重做一次 ——");
        history.redo();
        System.out.println("  重做后：" + config.snapshot());
        history.printStacks();
        System.out.println();

        System.out.println("  —— 执行一条新命令：重做栈必须被清空 ——");
        history.execute(new UpdateConfigCommand(config, "retry.max", "5"));
        history.printStacks();
        System.out.println();
        history.redo();
        System.out.println("  >>> 撤销过、还没重做的那一步，就永远回不来了。");
        System.out.println("      这和 Git 在历史中间提交、后面的 commit 离开当前分支是同一个道理。");
        System.out.println();

        System.out.println("  —— 一直撤销到栈空 ——");
        history.undo();
        history.undo();
        history.undo();
        history.undo();
        System.out.println();

        System.out.println("  —— 撤销一条从未执行过的命令 ——");
        UndoableCommand neverExecuted = new UpdateConfigCommand(config, "unknown.key", "x");
        try {
            neverExecuted.undo();
            System.out.println("  >>> 没有抛异常（不应该发生）");
        } catch (IllegalStateException e) {
            System.out.println("  >>> [OK] 被拦住了：" + e.getMessage());
            System.out.println("      没成功执行过的事，谈不上撤销；这种「用法错误」应该是异常，而不是静默无事发生。");
        }
    }

    // ==================================================================
    // demo5：宏命令 + 补偿
    // ==================================================================

    /**
     * 展示「多条命令一起成功，或失败后逆序补偿」。
     */
    private static void demo5() {
        printTitle("5. 宏命令：打包多条命令，失败就逆序补偿");

        ConfigService config = new ConfigService("release.channel", "stable");
        BackupService backup = new BackupService(2000);
        ReportService report = new ReportService();
        EmailService email = new EmailService();
        TaskScheduler scheduler = new TaskScheduler();

        System.out.println("  发布流程 4 步：改配置 → 备份数据库 → 生成报表 → 发通知邮件");
        System.out.println("  最后一步【不可撤销】（邮件发出去收不回来），所以它必须排在最后。");
        System.out.println();

        System.out.println("  【场景 5.1】上游数据还没到，报表 0 行 → 第 3 步失败");
        Command releaseFailed = new MacroCommand("发布 v2.3.1",
                new UpdateConfigCommand(config, "release.version", "v2.3.1"),
                new BackupDatabaseCommand(backup, "orders_db", 400),
                new GenerateReportCommand(report, "发布核对报表", 0),        // ← 0 行，会失败
                new SendEmailCommand(email, "team@shop.com", "发布完成", "v2.3.1 已上线"));
        scheduler.submit(releaseFailed);
        scheduler.run();

        System.out.println();
        System.out.println("  回滚后的系统状态：");
        System.out.println("      release.version = " + config.get("release.version") + "   （原本不存在）");
        System.out.println("      备份文件        = " + backup.getBackups().size() + " 份");
        System.out.println("      报表文件        = " + report.getFiles().size() + " 份");
        System.out.println("      已发邮件        = " + email.getOutbox().size() + " 封");
        System.out.println("      可用磁盘        = " + backup.getFreeDiskMb() + "MB（已还原）");
        System.out.println("  >>> 前三项都回到「没有」的状态，磁盘也一分不少 ——");
        System.out.println("      前两步的副作用被完整撤销了，而邮件压根还没发。");
        System.out.println("      为什么能这么干净？因为不可逆的那一步排在最后。");
        System.out.println();

        System.out.println("  【场景 5.2】数据到位后重跑，4 步全部成功");
        Command releaseOk = new MacroCommand("发布 v2.3.1",
                new UpdateConfigCommand(config, "release.version", "v2.3.1"),
                new BackupDatabaseCommand(backup, "orders_db", 400),
                new GenerateReportCommand(report, "发布核对报表", 1520),
                new SendEmailCommand(email, "team@shop.com", "发布完成", "v2.3.1 已上线"));
        scheduler.submit(releaseOk);
        scheduler.run();

        System.out.println();
        System.out.println("  成功后的系统状态：");
        System.out.println("      release.version = " + config.get("release.version"));
        System.out.println("      备份文件        = " + backup.getBackups().size() + " 份");
        System.out.println("      报表文件        = " + report.getFiles().size() + " 份");
        System.out.println("      已发邮件        = " + email.getOutbox().size() + " 封");
        System.out.println("      可用磁盘        = " + backup.getFreeDiskMb() + "MB");
        System.out.println();
        System.out.println("  注：5.1 失败的那条宏命令仍在待重试列表里（"
                + scheduler.getFailedCommands().size() + " 条）。");
        System.out.println("      它和 5.2 是两次独立的提交 —— 调度器不会自作主张地认为");
        System.out.println("      「同类操作成功了就算它过了」；要不要放弃失败任务，应由人或明确的策略决定。");
        System.out.println();

        scheduler.printHistory();
        System.out.println("  >>> 两条宏命令各是一条执行记录 —— 调度器不知道里面装了 4 个步骤。");
        System.out.println("      这就是「组合模式 + 命令模式」的效果：一组命令和一条命令长得一样。");
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /**
     * 打印分节标题。
     *
     * <p>为什么用 {@code "=".repeat(64)} 而不是手敲 64 个等号？
     * 因为手敲容易数错，而且改长度时要改很多处。
     *
     * @param title 标题
     */
    private static void printTitle(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));
    }
}
