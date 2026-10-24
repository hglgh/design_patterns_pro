package com.patterns.behavioral.memento;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 确定性的时间源：从固定基点出发，每调用一次前进固定步长。
 *
 * <p><b>为什么要造这个东西？</b>
 * 因为 {@code SnapshotMetadata} 里有 {@code capturedAt}，如果直接用
 * {@code Instant::now}，那么同一份 Demo 每次跑出来的时间戳都不同 ——
 * <b>输出就无法逐字比对</b>，"三次运行哈希一致"这条验证也就做不成了。
 *
 * <p>更重要的理由在测试侧：真实项目里，"撤销栈按时间排序对不对"
 * "超过 5 分钟的快照要不要合并"这类逻辑，一旦依赖真实时钟，
 * 就只能靠 {@code sleep} 去测 —— 慢、且会随机失败。
 * 把时钟做成可注入的 {@link Supplier}，这些逻辑全部变成
 * "喂几个固定时刻，断言输出"，毫秒级跑完。
 *
 * <p><b>为什么用 {@link java.time.Instant} 而不是 {@code System.currentTimeMillis()}？</b>
 * 因为 JDK 8 起时间 API 的规范就是"业务代码用 {@code java.time}，
 * 只在边界上和 {@code long} 互转"。{@code Instant} 能直接
 * {@code plusMillis}、能直接格式化、能直接比较，而 {@code long} 每用一次
 * 都要问一遍"这是秒还是毫秒"。
 *
 * <p><b>为什么用 {@link AtomicLong}？</b>
 * 本 Demo 是单线程，其实 {@code long} 就够。用原子类型是为了说明：
 * 这个时钟<b>可以被并发调用而不出错</b> —— 因为下一个要做的验证
 * 很可能是"两个编辑器同时编辑"。
 *
 * @author design-patterns-pro
 */
public final class SteppedClock implements Supplier<Instant> {

    private final Instant base;
    private final Duration step;
    private final AtomicLong ticks = new AtomicLong();

    /**
     * @param base 第一次调用返回的时刻
     * @param step 之后每次调用的间隔，必须为正
     * @throws NullPointerException     {@code base} 或 {@code step} 为 null 时
     * @throws IllegalArgumentException {@code step} 非正时
     */
    public SteppedClock(Instant base, Duration step) {
        this.base = Objects.requireNonNull(base, "base");
        this.step = Objects.requireNonNull(step, "step");
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("步长必须为正，实际 " + step);
        }
    }

    @Override
    public Instant get() {
        return base.plus(step.multipliedBy(ticks.getAndIncrement()));
    }
}
