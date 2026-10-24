package com.patterns.behavioral.state;

import java.util.Objects;

/**
 * 审计轨迹上的一格：第几步、尝试了什么、结果如何。
 *
 * <p><b>为什么要单独一个类，而不是直接往 {@code List<TransitionResult>} 里塞？</b>
 * 因为"第几步"这件信息并不属于转移结果本身——它是<b>历史的位置</b>。
 * 如果硬塞进 {@link TransitionResult}，那么同一个转移结果在
 * 不同位置就变成不同的值，{@code equals} 也就失去意义了。
 *
 * <p>这跟责任链里 {@code ApprovalContext.Step} 的思路一致：
 * <b>结果描述"发生了什么"，轨迹描述"它排在第几位"。</b>
 * 两个职责，两个类型。
 *
 * <p><b>为什么它也必须带上失败的那几步？</b>
 * 这是本模式最容易被省掉的东西。只记录成功的转移，看起来"干净"，
 * 但现实中最需要排查的恰恰是<b>被拒绝的那几次</b>：
 * <ul>
 *   <li>用户投诉"我点不了取消"——查轨迹，能看到是"已发货"状态拒绝了 CANCEL；</li>
 *   <li>接口突然大量报错——查轨迹，发现是上游把 CANCEL 发给了已完成订单；</li>
 *   <li>运营问"这张单到底经历了什么"——轨迹里每一步都在，包括没走通的那步。</li>
 * </ul>
 *
 * <blockquote>
 * <b>只记成功 = 只留下了结果，丢掉了过程。</b>
 * 而排查问题时，过程永远比结果值钱。
 * </blockquote>
 *
 * @param step   第几步（从 1 开始，含被拒绝的尝试）
 * @param result 那一步的转移结果
 * @author design-patterns-pro
 */
public record HistoryEntry(int step, TransitionResult result) {

    /**
     * 紧凑构造器：步号必须是正数。
     *
     * <p>这条检查的实际价值：如果哪天有人用 {@code history.size()} 当步号
     * （从 0 开始），第一笔记录就会在构造时炸掉，而不是等到日志里
     * 出现"第 0 步"才被人发现。
     */
    public HistoryEntry {
        if (step <= 0) {
            throw new IllegalArgumentException("步号从 1 开始，实际传入：" + step);
        }
        Objects.requireNonNull(result, "轨迹条目必须带结果");
    }

    /**
     * 从当前轨迹长度推导出下一个步号。
     *
     * <p>把"步号怎么算"收在一处，调用方永远只需要
     * {@code HistoryEntry.next(history.size(), result)}，
     * 不会出现一个地方从 0 数、另一个地方从 1 数。
     *
     * @param existingSize 当前已有多少条记录
     * @param result       这一步的结果
     * @return 步号为 {@code existingSize + 1} 的轨迹条目
     */
    public static HistoryEntry next(int existingSize, TransitionResult result) {
        return new HistoryEntry(existingSize + 1, result);
    }

    /**
     * 一行式描述，用于打印完整轨迹。
     *
     * <p>格式固定成 {@code #1 支付：待支付 → 已支付}，
     * 步号左对齐补位由调用方决定——本类只负责内容，不负责排版。
     *
     * @return 形如 {@code 1 支付：待支付 → 已支付} 的描述
     */
    public String describe() {
        return step + " " + result.describe();
    }
}
