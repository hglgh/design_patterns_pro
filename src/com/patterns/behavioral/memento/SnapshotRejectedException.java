package com.patterns.behavioral.memento;

/**
 * 拒绝一份不属于本编辑器的快照。
 *
 * <p><b>为什么不用 {@code IllegalArgumentException} 直接扔？</b>
 * 因为这条错误有独立的业务含义：<b>跨文档污染</b>。
 * 原项目的 {@code History} 不绑定任何 {@code Document}（探针 P10），
 * 一份历史可以被两个文档共用，撤销时把 A 的快照装进 B 里 —— 而且
 * <b>全程没有任何异常</b>，用户只会发现"撤销之后内容变成了别的文件"。
 *
 * <p>把这种错误单独类型化，带来三个实际好处：
 * <ol>
 *   <li>调用方可以精确捕获：{@code catch (SnapshotRejectedException e)}，
 *       而不会连"参数非法"一起吞掉；</li>
 *   <li>日志里一眼能看出是"串档"而不是"参数写错了"；</li>
 *   <li>继承了 {@link IllegalArgumentException}，所以它仍然是一个
 *       "调用方用错了 API"的编程错误，而不是需要重试的业务异常。</li>
 * </ol>
 *
 * <p><b>什么时候会抛？</b>
 * 只有一种情况：{@code 快照 instanceof Editor.Memento} 为假。
 * 由于 {@code Memento} 是 {@link Editor} 的私有嵌套类，正常代码
 * <b>根本造不出</b>这种快照；能走到这里，说明有人绕过了类型系统
 * （反射、或者把另一个编辑器的快照传了进来）。
 *
 * @author design-patterns-pro
 * @see Editor#restore(Snapshot)
 */
public class SnapshotRejectedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 说明被拒原因，应包含快照的元信息
     */
    public SnapshotRejectedException(String message) {
        super(message);
    }
}
