package com.patterns.behavioral.memento;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 撤销/重做历史 —— 备忘录模式里的 <b>Caretaker（负责人）</b>。
 *
 * <p>Caretaker 的职责只有一句话：<b>保管快照、决定什么时候拍</b>。
 * 它不该知道快照里装了什么，也不该替 {@link Editor} 改状态。
 *
 * <p>原项目的 {@code History} 违反了这句话，它做了三件超出职责的事：
 * <ol>
 *   <li>{@code popMemento()} 内部同时完成了"弹栈"、"压重做栈"、"返回上一个"，
 *       调用方只要忘了把返回值交给 {@code restore}，历史就被静默吃掉（探针 P2）；</li>
 *   <li>用返回 {@code null} 表示"没有更多"，而调用方拿到 {@code null}
 *       直接送进 {@code restore} 就是 NPE（探针 P1、P3）；</li>
 *   <li>不绑定任何 {@code Document}，一份历史可以被两个文档共用（探针 P10）。</li>
 * </ol>
 *
 * <p><b>本类的三条修正：</b>
 * <table border="1">
 *   <caption>与原项目的差异</caption>
 *   <tr><th>原项目</th><th>本项目</th><th>解决什么</th></tr>
 *   <tr><td>{@code popMemento()} 返回 {@code DocumentMemento}，可能为 null</td>
 *       <td>{@code undo()} 返回 {@code boolean}，内部一步到位</td>
 *       <td>null 语义消失，history 与 editor 不可能脱节</td></tr>
 *   <tr><td>拍快照靠调用方自己调 {@code save()} + {@code pushMemento()}</td>
 *       <td>{@link #edit} 把一段操作包成原子单元</td>
 *       <td>漏拍不可能发生；一段操作只算一步撤销</td></tr>
 *   <tr><td>{@code History} 不知道 {@code Document} 是谁</td>
 *       <td>构造时绑定唯一一个 {@link Editor}</td>
 *       <td>跨文档污染从"可能"变成"编译期就做不到"</td></tr>
 * </table>
 *
 * <p><b>为什么栈底要放一张基线快照？</b>
 * 因为"撤销到底"和"历史上什么都没有"必须能区分开。放了基线，
 * 判断就退化成一句话：{@code undo.size() <= 1} 说明只剩基线。
 * 原项目用 {@code history.size() > 1} 表达了同一件事，但它没有基线这个概念，
 * 于是只能返回 {@code null} 让调用方自己猜（探针 P3）。
 *
 * <p><b>为什么用 {@link ArrayDeque} 而不是 {@link java.util.Stack}？</b>
 * 探针 P7 的三行输出就是答案：{@code Stack} 继承 {@code Vector}，
 * 对外暴露 66 个 public 方法，只有 5 个是栈该有的；
 * 每个方法都 {@code synchronized} 在同一把锁上。
 * {@code ArrayDeque} 既没有继承包袱，也没有同步开销，
 * 官方文档从 Java 6 起就写着"需要栈时优先用它"。
 *
 * <p><b>本类不是线程安全的。</b>真实场景是"一个编辑器窗口一个历史"，
 * 天然单线程。要并发，请在外部加锁，而不是让每个方法都 {@code synchronized} ——
 * 后者只会让"撤销一次"变成 5 次独立的原子操作，中间状态照样会被看见。
 *
 * @author design-patterns-pro
 * @see Editor
 * @see Snapshot
 */
public final class SnapshotHistory {

    private final Editor editor;
    private final int maxDepth;
    private final Supplier<Instant> clock;

    /** 撤销栈：栈顶是"最近一次编辑之前的状态"。栈底永远是基线。 */
    private final Deque<Snapshot> undo = new ArrayDeque<>();

    /** 重做栈：栈顶是"最近一次撤销之前的状态"。 */
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    private final List<HistoryListener> listeners = new ArrayList<>();

    private int sequence;
    private int evicted;
    private int recordedEdits;

    /**
     * 建立历史，并立刻拍下基线快照。
     *
     * <p><b>为什么在构造器里就拍基线？</b>
     * 因为"打开文件时的状态"本来就该是撤销栈的底。让调用方额外调一次
     * {@code markInitial()}，就给了"忘了调"的机会 —— 而忘了调的后果是
     * 第一次 {@code undo()} 返回 {@code false}，用户以为撤销坏了。
     * <b>能自动做的事，不要留给调用方。</b>
     *
     * @param editor   被记录的编辑器，构造后不可更换
     * @param maxDepth 撤销栈最大深度，至少 2（1 个基线 + 1 步编辑）
     * @param clock    时间源；Demo 传确定性时钟即可得到可复现输出
     * @throws NullPointerException     {@code editor} 或 {@code clock} 为 null 时
     * @throws IllegalArgumentException 深度越界时
     */
    public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.clock = Objects.requireNonNull(clock, "clock");
        EditorLimits.requirePositive(maxDepth, "maxDepth");
        if (maxDepth < 2) {
            throw new IllegalArgumentException("撤销深度至少为 2，实际 " + maxDepth);
        }
        if (maxDepth > EditorLimits.MAX_UNDO_DEPTH) {
            throw new IllegalArgumentException(
                    "撤销深度不能超过 " + EditorLimits.MAX_UNDO_DEPTH + "，实际 " + maxDepth);
        }
        this.maxDepth = maxDepth;
        this.undo.push(capture(EditKind.BASELINE, "打开编辑器，正文 " + editor.length() + " 字"));
    }

    /**
     * 把一段编辑包成<b>一次原子操作</b>。
     *
     * <p>这是本类最重要的方法。它同时解决三个问题：
     * <ol>
     *   <li><b>快照粒度</b>：{@code action} 内部改 1 次还是 100 次，
     *       都只拍一张快照、只占一步撤销。原项目是"每调一次
     *       {@code pushMemento} 就一步"，于是"全选 + 输入"变成了两步，
     *       用户按一次撤销只退掉一半。</li>
     *   <li><b>失败回滚</b>：{@code action} 中途抛异常时，编辑器被恢复到
     *       进入前的状态，且这张快照<b>不入栈</b>。原项目没有任何回滚，
     *       改到一半失败就留下一个谁也没记录过的中间态（探针 P9）。</li>
     *   <li><b>重做栈失效</b>：只有编辑<b>成功</b>才清空重做栈。
     *       原项目在 {@code pushMemento} 里无脑 {@code redoStack.clear()}，
     *       连"这次 push 是不是真的发生了编辑"都不看。</li>
     * </ol>
     *
     * @param kind   这次编辑的类型，用于撤销面板
     * @param detail 人类可读的补充说明
     * @param action 真正改动编辑器的动作
     * @throws NullPointerException {@code kind}、{@code detail} 或 {@code action} 为 null 时
     */
    public void edit(EditKind kind, String detail, Consumer<Editor> action) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(action, "action");

        Snapshot before = capture(kind, detail);
        try {
            action.accept(editor);
        } catch (RuntimeException e) {
            // 失败不留痕：恢复现场，快照不入栈，redo 也不动
            editor.restore(before);
            throw e;
        }
        undo.push(before);
        trim();
        redo.clear();
        recordedEdits++;
        notifyChanged();
    }

    /**
     * 撤销一步。
     *
     * <p>返回 {@code boolean} 而不是可能为 null 的快照，是刻意的：
     * <b>"撤销有没有发生"和"撤销到了什么状态"是两个问题</b>，
     * 后者由 {@link Editor} 自己回答。原项目把两个问题塞进一个返回值，
     * 于是调用方既不能忽略它（忽略就脱节），也无法安全地使用它
     * （null 就是 NPE）。
     *
     * @return 真的撤销了一步返回 {@code true}；已到基线返回 {@code false}
     */
    public boolean undo() {
        if (undo.size() <= 1) {
            return false;
        }
        Snapshot target = undo.pop();
        redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));
        editor.restore(target);
        notifyChanged();
        return true;
    }

    /**
     * 重做一步。
     *
     * @return 真的重做了一步返回 {@code true}；重做栈为空返回 {@code false}
     */
    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        Snapshot target = redo.pop();
        undo.push(capture(EditKind.REDO, "准备回到 #" + target.metadata().sequence()));
        trim();
        editor.restore(target);
        notifyChanged();
        return true;
    }

    /**
     * 重置历史：丢弃全部撤销与重做记录，把当前状态记成新基线。
     *
     * <p>典型时机是"用户点了保存"。保存之后再按撤销，不应该回到保存前的版本 ——
     * 因为磁盘上已经有了一个确定的版本，撤销栈的语义要跟着重来。
     *
     * @param label 新基线的说明
     * @throws NullPointerException label 为 null 时
     */
    public void reset(String label) {
        Objects.requireNonNull(label, "label");
        undo.clear();
        redo.clear();
        undo.push(capture(EditKind.BASELINE, label));
        notifyChanged();
    }

    /**
     * 当前统计，供撤销面板渲染。
     *
     * @return 一份不可变的统计快照
     */
    public HistoryStats stats() {
        return new HistoryStats(undo.size(), redo.size(), sequence, evicted, recordedEdits);
    }

    /**
     * 注册历史变更监听器。
     *
     * @param listener 监听器
     * @throws NullPointerException 为 null 时
     */
    public void addListener(HistoryListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 注销历史变更监听器。
     *
     * @param listener 监听器
     */
    public void removeListener(HistoryListener listener) {
        listeners.remove(listener);
    }

    // ==================== 内部 ====================

    /**
     * 拍一张快照。编号自增，时间取自注入的时钟。
     *
     * <p>失败路径也会走到这里（{@link #edit} 进门前就拍），
     * 所以 {@code sequence} 可能出现"跳号" —— 这是有意的：
     * 它记录的是<b>拍摄次数</b>，而不是"成功入栈的快照数"。
     * 后者由 {@code undo.size() + redo.size()} 回答。
     *
     * @param kind   拍摄原因
     * @param detail 补充说明
     * @return 新快照
     */
    private Snapshot capture(EditKind kind, String detail) {
        sequence++;
        SnapshotMetadata metadata = new SnapshotMetadata(sequence, kind, detail, clock.get());
        return editor.snapshot(metadata);
    }

    /**
     * 超出深度时，从栈底淘汰最老的一条。
     *
     * <p>{@link ArrayDeque#push} 压到队首，所以"最老"在队尾，
     * 用 {@link ArrayDeque#removeLast()} 精确移除。
     * 探针 P8 里原项目连压 10 万条全留着，一个下午的编辑就能吃掉几百 MB。
     */
    private void trim() {
        while (undo.size() > maxDepth) {
            undo.removeLast();
            evicted++;
        }
    }

    /**
     * 通知所有监听器。
     *
     * <p>先 {@link List#copyOf} 再遍历：监听器在回调里增删监听器是常见操作
     * （比如窗口关闭时自己注销），直接遍历原列表会抛
     * {@code ConcurrentModificationException}。
     */
    private void notifyChanged() {
        if (listeners.isEmpty()) {
            return;
        }
        HistoryStats stats = stats();
        for (HistoryListener listener : List.copyOf(listeners)) {
            listener.onHistoryChanged(stats, editor);
        }
    }
}
