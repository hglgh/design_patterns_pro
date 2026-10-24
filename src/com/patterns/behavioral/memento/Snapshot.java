package com.patterns.behavioral.memento;

/**
 * 备忘录的<b>窄接口</b>—— 这是整个模式最容易被写错的地方。
 *
 * <p><b>GoF 原话的落地：</b>备忘录必须提供两种接口。
 * <ul>
 *   <li><b>宽接口</b>给 Originator（{@link Editor}）：能读回全部字段，用于恢复。</li>
 *   <li><b>窄接口</b>给 Caretaker（{@link SnapshotHistory}）：只能握住、不能读内容。</li>
 * </ul>
 *
 * <p>原项目的写法是：
 *
 * <pre>
 *   public record DocumentMemento(String content) { }
 *   void restore(DocumentMemento memento) { this.content = memento.content(); }
 * </pre>
 *
 * 这个 record 的两个成员 {@code content()} 和 {@code new DocumentMemento(...)}
 * 都是 {@code public} 的，于是：
 * <ul>
 *   <li>任何一个类都能 <b>读</b> 出快照内容 —— 封装失效；</li>
 *   <li>任何一个类都能 <b>造</b> 一个假快照塞进 {@code restore} —— 见探针 P4。</li>
 * </ul>
 *
 * <p><b>本项目的做法：</b>
 *
 * <pre>
 *   public interface Snapshot {            // 窄：只有一个方法
 *       SnapshotMetadata metadata();       // 且返回的是"可以公开"的元信息
 *   }
 *
 *   private static final class Memento implements Snapshot {   // 宽：藏在 Editor 里
 *       private final String text; ...
 *   }
 * </pre>
 *
 * <p>关键在于 {@code Memento} 是 {@link Editor} 的
 * <b>{@code private static} 嵌套类</b>：
 * <ul>
 *   <li>外部代码写不出 {@code new Editor.Memento(...)} —— 类型根本不可见；</li>
 *   <li>外部代码写不出 {@code snapshot instanceof Memento} —— 编译器不认识这个名字；</li>
 *   <li>{@code Editor.restore} 内部的 {@code instanceof Memento} 则是合法的。
 *       类型系统能保证"只有 Editor 能造快照"。</li>
 * </ul>
 *
 * <p><b>但光靠类型还不够 —— 这是本节最关键的一句话。</b>
 * 因为 {@code Memento} 是 {@link Editor} 的<b>嵌套</b>类，所有 {@code Editor}
 * 实例<b>共享同一个类型</b>。于是下面这段代码能通过 {@code instanceof}：
 *
 * <pre>
 *   Editor a = new Editor("文档 A");
 *   Editor b = new Editor("文档 B");
 *   b.restore(a.snapshot(...));   // 类型对得上，但归属不对
 * </pre>
 *
 * <p>后果和原项目探针 P10 一模一样：B 的正文被换成了 A 的快照。
 * 所以每份快照还携带一个<b>归属令牌</b>（每个 {@link Editor} 实例独一份），
 * {@code restore} 时用引用相等比较。详见 {@link Editor} 里
 * {@code ownerToken} 的注释。
 *
 * <p><b>记忆点：</b>类型回答“是谁造的”，令牌回答“是<b>哪个</b>造的”。
 * 前者编译器能管，后者只能自己写。
 *
 * <p><b>为什么接口里不写 {@code text()}？</b>
 * 一旦写了，Caretaker 就能在 {@code undo()} 里读正文，
 * 于是"谁改了文档"这件事就说不清了；更糟的是，任何拿到 {@code Snapshot}
 * 的第三方都能把正文导出到日志、缓存、埋点里，一张快照就此扩散。
 * <b>能拿到对象 ≠ 能拿到内容</b>，这是备忘录存在的全部理由。
 *
 * @author design-patterns-pro
 * @see Editor#snapshot(SnapshotMetadata)
 * @see Editor#restore(Snapshot)
 */
public interface Snapshot {

    /**
     * 快照的元信息。这是 Caretaker 唯一被允许看见的东西。
     *
     * @return 编号、类型、说明、拍摄时刻
     */
    SnapshotMetadata metadata();
}
