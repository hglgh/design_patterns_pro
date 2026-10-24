package com.patterns.behavioral.memento;

/**
 * 撤销面板要显示的那几个数字。
 *
 * <p><b>为什么不直接暴露 {@code SnapshotHistory} 的两个栈？</b>
 * 因为一旦暴露，调用方就能 {@code undo.pollLast()} 把基线扔掉，
 * 或者往栈里塞一个自己 new 的快照 —— Caretaker 的封装就没了。
 * 这一条和 {@link Snapshot} 是同一个原则：<b>给出去的接口越窄，能被破坏的方式越少</b>。
 *
 * <p>做成 record 之后，这份统计天然是不可变的值：界面上拿到的
 * 一定是某一瞬间的完整快照，不会出现"绘制到一半时栈被改了"的撕裂。
 *
 * @param undoDepth    撤销栈里现有快照数，含基线，所以最小为 1
 * @param redoDepth    重做栈里现有快照数
 * @param snapshotsTaken 累计拍摄次数，含失败路径与撤销/重做时补拍的
 * @param evicted      因超出深度上限而被淘汰的快照数
 * @param edits        成功完成并计入撤销栈的编辑次数
 * @author design-patterns-pro
 */
public record HistoryStats(int undoDepth, int redoDepth, int snapshotsTaken, int evicted, int edits) {

    /**
     * 紧凑构造器：只校验负数，因为"深度为 0"在某个瞬间是合法的
     * （{@link SnapshotHistory#reset} 执行到一半时视图可能被读到）。
     *
     * @throws IllegalArgumentException 任一字段为负时
     */
    public HistoryStats {
        if (undoDepth < 0 || redoDepth < 0 || snapshotsTaken < 0 || evicted < 0 || edits < 0) {
            throw new IllegalArgumentException("统计值不能为负");
        }
    }

    /**
     * 还能不能撤销。
     *
     * <p>判据是 {@code undoDepth > 1} 而不是 {@code > 0}：栈底那张快照代表
     * “最早保留的版本”，它可以被恢复，但不该被“撤销掉”。
     * 注意栈底不一定是“打开编辑器时的最初状态” —— 超出深度上限后
     * 最老的快照会被淘汰，栈底就向前推进了。见 {@link SnapshotHistory#edit}。
     *
     * <p>这个判断必须和 {@link SnapshotHistory#undo()} 里的判断始终一致，
     * 所以两边引用同一处代码。
     *
     * @return 栈里除最早一张外还有快照时返回 {@code true}
     */
    public boolean canUndo() {
        return undoDepth > 1;
    }

    /**
     * 还能不能重做。
     *
     * @return 重做栈非空时返回 {@code true}
     */
    public boolean canRedo() {
        return redoDepth > 0;
    }

    /**
     * 一行摘要，供 {@link ConsoleHistoryListener} 打印。
     *
     * <p>显示的是“还能撤几步”（{@code undoDepth - 1}），而不是栈里
     * 有几张快照 —— 因为用户关心的是“我要按几次”，不是内部堆了几个对象。
     *
     * @return 形如 {@code 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2}
     */
    public String summary() {
        return "可撤 " + Math.max(0, undoDepth - 1) + " 步"
                + " | 可重 " + redoDepth + " 步"
                + " | 已拍 " + snapshotsTaken + " 淘汰 " + evicted + " 编辑 " + edits;
    }
}
