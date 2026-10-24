package com.patterns.behavioral.memento;

import java.util.Objects;

/**
 * 把历史变更打到控制台 —— 模拟"撤销面板"这一层 UI。
 *
 * <p>它的存在是为了让 Demo 能<b>看见</b>撤销栈的每一次变化。
 * 没有它，整份 Demo 就只剩"正文变成了什么"，而看不出
 * "为什么这次编辑算了三步、那次只算一步"。
 *
 * <p><b>为什么前缀可配置？</b>
 * 因为同一个进程里可能挂两个监听器（一个打控制台，一个写
 * 埋点），不区分来源的日志是没法读的。前缀是个很便宜、
 * 但确实会被用到的小设计。
 *
 * @author design-patterns-pro
 */
public final class ConsoleHistoryListener implements HistoryListener {

    private final String prefix;

    /**
     * @param prefix 每行日志的前缀，不能为 null
     * @throws NullPointerException 前缀为 null 时
     */
    public ConsoleHistoryListener(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    @Override
    public void onHistoryChanged(HistoryStats stats, Editor editor) {
        System.out.println("      [" + prefix + "] " + stats.summary()
                + " | 正文 " + editor.length() + " 字");
    }
}
