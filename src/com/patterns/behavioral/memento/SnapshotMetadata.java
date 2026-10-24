package com.patterns.behavioral.memento;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 撤销面板上显示的一行元信息。
 *
 * <p><b>为什么快照的"身份证"要单独抽一个类型？</b>
 * 备忘录模式的经典难点是：<b>Caretaker 需要看见快照，但不能看见快照的内容</b>。
 * 如果 {@code Snapshot} 接口把 {@code text()}、{@code cursor()} 全暴露出来，
 * 那 Caretaker 就能读、能改、能伪造 —— 备忘录的封装性一分不剩。
 *
 * <p>所以这里做了切分：
 *
 * <pre>
 *   Snapshot            ← Caretaker 能看见的<b>全部</b>（只有 metadata()）
 *     └── metadata()    ← 可以显示的元信息（编号、类型、时间）
 *   Editor.Memento      ← private 嵌套类，装着真正的正文/光标/选区
 * </pre>
 *
 * <p>元信息是"可以公开"的那一半。用户需要知道"第 3 步是输入"，但不需要
 * 通过撤销面板拿到第 3 步的完整正文 —— 那是 {@code Editor} 的事。
 *
 * <p><b>为什么 {@code capturedAt} 是 {@link java.time.Instant} 而不是 {@code long}？</b>
 * 用 {@code long} 存毫秒是最容易写错的类型选择：没有人能从
 * {@code 1742000000000L} 看出这是哪一天，而 {@code Instant} 自带语义、
 * 自带时区无关性，还能被 {@code DateTimeFormatter} 直接格式化。
 *
 * <p><b>为什么字段全部在紧凑构造器里校验？</b>
 * record 的组件是 {@code final} 的，一旦构造完成就再也改不了。所以
 * <b>构造器是唯一的、也是最后的防线</b>。放过了 {@code null}，
 * 它就跟着这个对象活一辈子，直到某天在 {@code toString()} 里炸出 NPE。
 *
 * @param sequence    快照编号，从 1 开始单调递增
 * @param kind        这个快照"拍在哪种编辑之前"
 * @param detail      人类可读的补充说明，例如"在光标 4 处插入「合规」"
 * @param capturedAt  拍摄时刻
 * @author design-patterns-pro
 */
public record SnapshotMetadata(int sequence, EditKind kind, String detail, Instant capturedAt) {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    /**
     * 紧凑构造器：所有校验都在这里，构造完成即合法。
     *
     * @throws IllegalArgumentException 编号小于 1 时
     * @throws NullPointerException     {@code kind}、{@code capturedAt} 为 null 时
     */
    public SnapshotMetadata {
        if (sequence < 1) {
            throw new IllegalArgumentException("快照编号从 1 开始，实际 " + sequence);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(capturedAt, "capturedAt");
        detail = detail == null ? "" : detail;
    }

    /**
     * 格式化成撤销面板上的一行。
     *
     * <p>时间只用 {@code HH:mm:ss.SSS}，因为撤销面板显示的是"这场会话里
     * 谁先谁后"，跨越日期的时间戳反而占地方。真要跨天，已经有 {@code sequence} 兜底。
     *
     * <p>格式器固定用 UTC，不用 {@code ZoneId.systemDefault()} —— 否则同一份
     * Demo 在不同时区的机器上会打出不同的小时数，"输出可复现"就成了空话。
     *
     * @return 形如 {@code #3 输入 · 在光标 4 处插入「合规」 · 09:00:04.500}
     */
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(sequence).append(' ').append(kind.label());
        if (!detail.isEmpty()) {
            sb.append(" · ").append(detail);
        }
        sb.append(" · ").append(TIME.format(capturedAt));
        return sb.toString();
    }
}
