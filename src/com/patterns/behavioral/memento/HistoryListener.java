package com.patterns.behavioral.memento;

/**
 * 历史变更的监听者。
 *
 * <p><b>为什么备忘录需要这个？</b>
 * 因为撤销栈变了，界面必须跟着变 —— 撤销按钮要亮/灭，
 * 历史面板要增删一行，"已保存"标记要变成"有未保存的修改"。
 * 这些反应如果靠调用方在每次 {@code edit} / {@code undo} 之后自己刷新，
 * 那就是原项目的老毛病：<b>靠调用方自觉</b>。
 *
 * <p>所以 {@link SnapshotHistory} 在三个入口（{@code edit}、
 * {@code undo}、{@code redo}）之后统一回调本接口，
 * 调用方只需要在启动时注册一次。
 *
 * <p><b>为什么不直接把 {@code SnapshotHistory} 传进来？</b>
 * 监听器拿到历史对象就能调 {@code undo()} —— 于是"刷新界面"这个动作
 * 有可能顺手改一次历史，出现递归回调。这里只传
 * {@link HistoryStats}（不可变的值）和 {@link Editor}（只读地看正文长度），
 * <b>拿到的东西不足以引发副作用</b>。
 *
 * <p><b>关于"这算不算观察者模式"：</b>机制上是，但它的地位是<b>配套设施</b>
 * 而不是备忘录的一部分。删掉监听器，备忘录照样工作；删掉备忘录，撤销功能就没了。
 * 判断一个角色是不是"模式本体"，看的是<b>删了它模式还成不成立</b>。
 *
 * @author design-patterns-pro
 * @see ConsoleHistoryListener
 */
@FunctionalInterface
public interface HistoryListener {

    /**
     * 撤销栈或重做栈发生变化时回调。
     *
     * <p>实现方必须<b>快速返回</b>：这个回调在 {@code edit} 的同步路径上，
     * 慢一秒就是用户敲字卡一秒。
     *
     * @param stats  变化后的统计，不可变
     * @param editor 编辑器本身，只应读取
     */
    void onHistoryChanged(HistoryStats stats, Editor editor);
}
