package com.patterns.behavioral.command;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收者：报表服务。
 *
 * <p>这个类提供了一个非常适合演示"业务失败"的场景：
 * <b>数据源没有数据，报表生成不出来</b>。
 *
 * <p>这件事和"代码有 bug"完全不同：
 * <ul>
 *   <li>它<b>每天都会发生</b>（凌晨跑批时上游还没导数据）；</li>
 *   <li>它<b>不需要改代码</b>就能恢复（等数据到了重跑一次）；</li>
 *   <li>所以它必须是 {@link ServiceException}，而不是 {@code NullPointerException}。</li>
 * </ul>
 * 原项目的 Invoker 用 {@code catch (Exception e)} 把这两种情况混在一起，
 * 结果就是「报表今天没数据」和「报表代码写错了」在日志里长得一模一样。
 *
 * <p>报表是<b>可撤销</b>的：删掉刚生成的 CSV 文件即可回到执行前。
 * 所以 {@link GenerateReportCommand} 实现了 {@link UndoableCommand}。
 *
 * @author design-patterns-pro
 */
public class ReportService {

    /**
     * 一份已生成的报表。
     *
     * @param name     报表名
     * @param rows     数据行数
     * @param fileName 生成的文件名
     */
    public record Report(String name, int rows, String fileName) {
    }

    /** 用 LinkedHashMap 保证输出顺序稳定、可复现（HashMap 的顺序不保证，会让文档对不上）。 */
    private final Map<String, Report> files = new LinkedHashMap<>();

    /**
     * 生成报表。
     *
     * @param name 报表名，例如 {@code "每日销售报表"}
     * @param rows 数据行数
     * @return 生成的报表
     * @throws ServiceException 报表名为空，或数据源 0 行
     */
    public Report generate(String name, int rows) throws ServiceException {
        if (name == null || name.isBlank()) {
            throw new ServiceException("报表名为空，无法生成");
        }
        if (rows <= 0) {
            throw new ServiceException("数据源 0 行，报表「" + name + "」未生成");
        }
        Report report = new Report(name, rows, name + "_" + rows + "行.csv");
        files.put(report.fileName(), report);
        System.out.println("        └ 报表服务：生成 " + report.fileName()
                + "（" + report.rows() + " 行）");
        return report;
    }

    /**
     * 删除一份报表（供 {@link GenerateReportCommand#undo()} 使用）。
     *
     * @param fileName 文件名
     * @throws IllegalStateException 文件不存在（说明撤销逻辑有 bug）
     */
    public void delete(String fileName) {
        Report removed = files.remove(fileName);
        if (removed == null) {
            throw new IllegalStateException("报表不存在，无法删除：" + fileName
                    + "（当前存在：" + files.keySet() + "）");
        }
        System.out.println("        └ 报表服务：删除 " + fileName);
    }

    /**
     * 当前已生成的报表（副本，防止外部直接修改内部状态）。
     *
     * @return 文件名 → 报表 的不可变视图
     */
    public Map<String, Report> getFiles() {
        return Map.copyOf(files);
    }
}
