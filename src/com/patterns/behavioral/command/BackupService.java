package com.patterns.behavioral.command;

import java.util.ArrayList;
import java.util.List;

/**
 * 接收者：备份服务。
 *
 * <p>这个类故意保留了<b>磁盘空间</b>这个状态，因为它是本项目演示"失败与重试"的
 * 现实基础：备份失败最常见的原因就是磁盘满了，而"运维清一下磁盘再重试"是非常真实的流程。
 *
 * <p><b>为什么要把 {@code freeDiskMb} 显式建模出来，而不是简单地
 * {@code if (database.equals("logs_db")) throw ...} 假装失败？</b>
 * 因为后者是"为了演示而演示"：读者看不到失败的原因，也看不到重试为什么能成功。
 * 有了真实的容量约束，整个过程就自洽了——
 * <pre>
 *   可用 500MB → 备份 orders_db(400MB) 成功，剩 100MB
 *              → 备份 logs_db(600MB) 失败（100 &lt; 600）
 *   运维清理 600MB → 可用 700MB → 重试成功，剩 100MB
 * </pre>
 * <b>失败不是被"安排"的，是被容量算出来的。</b>
 *
 * <p><b>为什么提供 {@link #delete(int)}？</b>
 * 因为备份是<b>可撤销</b>的操作：删掉刚生成的备份文件就能回到执行前的状态
 * （磁盘空间也会还回去）。这才让 {@link BackupDatabaseCommand} 有可能实现
 * {@link UndoableCommand}。对比 {@link EmailService} 没有 {@code recall()}——
 * 能不能撤销，取决于<b>接收者有没有提供补偿能力</b>，而不是取决于你想不想。
 *
 * @author design-patterns-pro
 */
public class BackupService {

    /**
     * 一份备份文件。
     *
     * @param id       备份编号（删除时用它定位）
     * @param database 数据库名
     * @param sizeMb   占用空间
     * @param fileName 文件名
     */
    public record Backup(int id, String database, int sizeMb, String fileName) {
    }

    private final List<Backup> backups = new ArrayList<>();
    private int freeDiskMb;
    private int nextId = 1;

    /**
     * @param totalDiskMb 初始可用磁盘空间（MB）
     */
    public BackupService(int totalDiskMb) {
        if (totalDiskMb < 0) {
            throw new IllegalArgumentException("磁盘容量不能为负：" + totalDiskMb);
        }
        this.freeDiskMb = totalDiskMb;
    }

    /**
     * 备份一个数据库。
     *
     * @param database 数据库名
     * @param sizeMb   备份体积（MB）
     * @return 生成的备份文件信息
     * @throws ServiceException 数据库名为空、体积非法、或磁盘空间不足
     */
    public Backup backup(String database, int sizeMb) throws ServiceException {
        if (database == null || database.isBlank()) {
            throw new ServiceException("数据库名为空，无法备份");
        }
        if (sizeMb <= 0) {
            throw new ServiceException("备份体积必须为正数，实际 " + sizeMb + "MB");
        }
        if (sizeMb > freeDiskMb) {
            // 【注意】这个异常是"业务失败"，不是 bug：
            // 调用方应该决定「是重试、还是告警、还是跳过」，而不是让程序崩掉。
            throw new ServiceException("磁盘空间不足：需要 " + sizeMb
                    + "MB，可用 " + freeDiskMb + "MB");
        }
        freeDiskMb -= sizeMb;
        int id = nextId++;
        Backup backup = new Backup(id, database, sizeMb, database + "_backup_" + id + ".bak");
        backups.add(backup);
        System.out.println("        └ 备份服务：生成 " + backup.fileName()
                + "（" + sizeMb + "MB），剩余磁盘 " + freeDiskMb + "MB");
        return backup;
    }

    /**
     * 删除一份备份（供 {@link BackupDatabaseCommand#undo()} 使用）。
     *
     * <p><b>为什么删除要按 id 而不是按文件名？</b>
     * 因为文件名可能重复（同名库备份两次），而 id 是唯一的。
     * 「用唯一标识定位对象」是撤销操作的基本功——
     * 如果撤销时靠"数据库名"去找，很可能删掉别人的备份。
     *
     * @param backupId 备份编号
     * @throws IllegalStateException 编号不存在（说明撤销逻辑本身有 bug）
     */
    public void delete(int backupId) {
        Backup found = null;
        for (Backup b : backups) {
            if (b.id() == backupId) {
                found = b;
                break;
            }
        }
        if (found == null) {
            // 这是"撤销逻辑写错了"，属于程序 bug，用未受检异常直接炸掉
            throw new IllegalStateException("备份不存在，无法删除：id=" + backupId
                    + "（当前存在：" + backups.stream().map(Backup::fileName).toList() + "）");
        }
        backups.remove(found);
        freeDiskMb += found.sizeMb();
        System.out.println("        └ 备份服务：删除 " + found.fileName()
                + "，回收 " + found.sizeMb() + "MB，剩余磁盘 " + freeDiskMb + "MB");
    }

    /**
     * 模拟运维清理磁盘。用于演示"失败后重试成功"。
     *
     * @param mb 释放的空间（MB）
     */
    public void freeDisk(int mb) {
        if (mb <= 0) {
            throw new IllegalArgumentException("释放空间必须为正数：" + mb);
        }
        freeDiskMb += mb;
        System.out.println("        └ 运维：清理磁盘，释放 " + mb + "MB，当前可用 " + freeDiskMb + "MB");
    }

    /** @return 当前可用磁盘空间（MB） */
    public int getFreeDiskMb() {
        return freeDiskMb;
    }

    /** @return 现存备份列表（快照） */
    public List<Backup> getBackups() {
        return List.copyOf(backups);
    }
}
