package com.patterns.behavioral.command;

import java.util.Objects;

/**
 * 具体命令：生成一份报表。<b>可撤销</b>。
 *
 * <p>它和 {@link BackupDatabaseCommand} 是同一个套路：
 * 执行时记住"生成了哪个文件"，撤销时把它删掉。
 * 两个类放在一起看，你会发现 <b>可撤销命令的骨架是完全固定的</b>：
 * <pre>
 *   execute() ──▶ 调用接收者 ──▶ 把返回值/现场存进字段
 *   undo()    ──▶ 检查字段是否有效 ──▶ 用字段里的信息调用接收者的补偿方法 ──▶ 清空字段
 * </pre>
 * 不同的只是"补偿方法"叫什么（{@code delete} / 恢复旧值 / 反向转账）。
 *
 * <p><b>本类在 demo5 里承担一个重要角色</b>：它是那个"会失败"的步骤。
 * 因为"今天上游没导数据"导致 {@code rows = 0}，报表服务抛出业务异常，
 * 于是宏命令要把它<b>前面已经做完的两步</b>逆序回滚掉。
 * 这就是真实世界的 <b>Saga / 补偿事务</b>：
 * 没有分布式事务的时候，靠"每一步都能撤销"来保证最终一致性。
 *
 * @author design-patterns-pro
 * @see ReportService 接收者
 */
public class GenerateReportCommand implements UndoableCommand {

    private final ReportService reportService;
    private final String reportName;
    private final int rows;

    /** 执行成功后生成的文件名；{@code null} 表示尚未成功执行或已撤销。 */
    private String createdFileName;

    /**
     * @param reportService 接收者，不能为 {@code null}
     * @param reportName    报表名
     * @param rows          数据行数
     */
    public GenerateReportCommand(ReportService reportService, String reportName, int rows) {
        this.reportService = Objects.requireNonNull(reportService, "reportService 不能为 null");
        this.reportName = reportName;
        this.rows = rows;
    }

    @Override
    public String getName() {
        return "生成报表「" + reportName + "」";
    }

    @Override
    public void execute() throws CommandException {
        try {
            createdFileName = reportService.generate(reportName, rows).fileName();
        } catch (ServiceException e) {
            // 失败时 createdFileName 保持 null → 不允许被撤销
            throw new CommandException(getName(), e.getMessage(), e);
        }
    }

    @Override
    public void undo() {
        if (createdFileName == null) {
            throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
        }
        reportService.delete(createdFileName);
        createdFileName = null;
    }
}
