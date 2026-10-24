package com.patterns.structural.facade.subsystem;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 子系统 3：审计日志。记录所有敏感操作，用于事后追溯。
 *
 * <p><b>为什么审计要独立成子系统？</b>
 * 审计是典型的<b>横切关注点</b>：注册要记、注销要记、改邮箱要记、登录失败也要记。
 * 如果散落在各处，就会到处出现 {@code System.out.println("log...")} 重复代码；
 * 集中到一个子系统后，日志格式、时间戳、存储介质都只需改一处。
 *
 * @author design-patterns-pro
 */
public class AuditLogger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<String> records = new ArrayList<>();

    /**
     * 记录一条审计日志。
     *
     * @param action 操作类型，如 REGISTER / UNREGISTER
     * @param detail 操作详情
     */
    public void log(String action, String detail) {
        String entry = String.format("%s  %-12s %s", LocalTime.now().format(TIME), action, detail);
        records.add(entry);
        System.out.println("      [Audit] " + entry);
    }

    /**
     * @return 只读的审计记录列表，防止外部篡改审计数据
     */
    public List<String> getRecords() {
        return Collections.unmodifiableList(records);
    }
}
