package com.patterns.behavioral.memento;

/**
 * 一次编辑的语义分类。
 *
 * <p><b>为什么快照要记"这次是因为什么拍的"？</b>
 * 原项目的 {@code History} 只存状态，不存原因：
 *
 * <pre>
 *   Stack&lt;DocumentMemento&gt; history
 * </pre>
 *
 * 于是撤销面板只能显示"版本 1 / 版本 2 / 版本 3"。用户点了两次撤销之后
 * 完全不知道自己退到了哪一步 —— 因为每一步长得都一样。
 *
 * <p>真实产品（IDEA 的 Local History、Figma 的版本历史、WPS 的撤销列表）
 * 都带图标和文案：输入、删除、替换、格式化。用户靠这个判断"我要退几步"。
 *
 * <p><b>为什么 {@link #BASELINE} 要作为枚举值，而不是让 kind 为 null？</b>
 * 基线（打开文件时的初始状态）是<b>唯一没有"前一次编辑"的快照</b>。
 * 如果允许 null，{@code SnapshotHistory} 的每个分支都要判空：
 *
 * <pre>
 *   String text = meta.kind() == null ? "基线" : meta.kind().label();
 * </pre>
 *
 * 这类判空会蔓延到每一个消费方。给它一个真实的枚举值，
 * null 就从类型系统里消失了 —— 与本项目在状态模式文档里
 * 用 {@code PENDING} 替代"订单还没有状态"是同一个手法。
 *
 * @author design-patterns-pro
 */
public enum EditKind {

    /** 基线：编辑器打开时的原始状态，是撤销栈的底，永远不可被撤销掉。 */
    BASELINE("基线"),

    /** 输入：在光标处插入字符。 */
    TYPING("输入"),

    /** 删除：退格删除光标前的字符。 */
    DELETION("删除"),

    /** 选区：改变了光标或选中范围，但正文没变。 */
    SELECTION("选区"),

    /** 整段替换：把全文换成另一份内容。 */
    REPLACE("整段替换"),

    /** 撤销：由 {@code undo()} 主动拍摄的"撤销前状态"，用于支撑重做。 */
    UNDO("撤销"),

    /** 重做：由 {@code redo()} 主动拍摄的"重做前状态"，用于支撑再次撤销。 */
    REDO("重做");

    private final String label;

    EditKind(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于撤销面板的图标文案。
     *
     * @return 编辑类型的中文名
     */
    public String label() {
        return label;
    }
}
