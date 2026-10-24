package com.patterns.behavioral.command;

import java.util.Objects;

/**
 * 具体命令：备份一个数据库。<b>可撤销</b>。
 *
 * <p><b>它比 {@link SendEmailCommand} 多了一个字段 {@link #created}，这是全部的关键。</b>
 *
 * <p>撤销的本质不是"反向调用一个方法"，而是<b>知道要恢复成什么样</b>。
 * 备份操作执行后，系统里多了一个文件、少了一些磁盘空间；
 * 要撤销它，就必须记住"这次具体生成了哪个文件"——
 * 光知道"我备份过 orders_db"是不够的，因为可能备份过十次。
 *
 * <p>这个字段也不是随便能放对位置的：
 * <pre>
 *   ❌ 在构造函数里捕获    → 那时还没执行，没有任何东西可捕获
 *   ❌ 在 undo() 里才去查  → 查到的可能是别人后来生成的文件，删错对象
 *   ✅ 在 execute() 成功的那一刻记下来  → 这才是"本次执行造成的副作用"的精确证据
 * </pre>
 * 所以 <b>"捕获现场"必须紧跟在"产生副作用"之后</b>，这是所有 undo 实现的共同模式。
 *
 * <p><b>为什么要 {@code created = null} 来防止重复撤销？</b>
 * 因为删除<b>不是幂等操作</b>：第二次删同一个文件会报"文件不存在"。
 * 置空之后，第二次 {@code undo()} 会被开头的检查拦住，抛出明确的
 * {@link IllegalStateException}，而不是在接收者里抛一个让人摸不着头脑的错误。
 * 对比 {@link UpdateConfigCommand}：它是"把值改回旧值"，天然幂等，就不需要这个防护。
 *
 * @author design-patterns-pro
 * @see BackupService 接收者
 */
public class BackupDatabaseCommand implements UndoableCommand {

    private final BackupService backupService;
    private final String database;
    private final int sizeMb;

    /** 执行成功后生成的备份；{@code null} 表示"尚未成功执行，或已经撤销过"。 */
    private BackupService.Backup created;

    /**
     * @param backupService 接收者，不能为 {@code null}
     * @param database      数据库名
     * @param sizeMb        备份体积（MB）
     */
    public BackupDatabaseCommand(BackupService backupService, String database, int sizeMb) {
        this.backupService = Objects.requireNonNull(backupService, "backupService 不能为 null");
        this.database = database;
        this.sizeMb = sizeMb;
    }

    @Override
    public String getName() {
        return "备份数据库 " + database + "（" + sizeMb + "MB）";
    }

    @Override
    public void execute() throws CommandException {
        try {
            created = backupService.backup(database, sizeMb);
        } catch (ServiceException e) {
            // 注意这里没有给 created 赋值 —— 失败时它保持 null，
            // 于是 undo() 会正确地拒绝撤销（没成功的事，谈不上撤销）。
            throw new CommandException(getName(), e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (created == null) {
            throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
        }
        backupService.delete(created.id());
        created = null;
    }
}
