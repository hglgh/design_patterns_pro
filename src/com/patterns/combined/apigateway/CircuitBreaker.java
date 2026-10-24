package com.patterns.combined.apigateway;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断器：<b>这是状态模式（State）</b>。
 *
 * <p>熔断器的行为<b>完全由它当前处在哪个状态决定</b>：</p>
 * <table border="1">
 *   <caption>三种状态</caption>
 *   <tr><th>状态</th><th>是否放行</th><th>何时切换</th></tr>
 *   <tr><td>{@code CLOSED}</td><td>放行</td>
 *       <td>连续失败达到阈值 → {@code OPEN}</td></tr>
 *   <tr><td>{@code OPEN}</td><td><b>拒绝</b>（快速失败）</td>
 *       <td>冷却时间到 → {@code HALF_OPEN}</td></tr>
 *   <tr><td>{@code HALF_OPEN}</td><td>放行一次探路</td>
 *       <td>成功 → {@code CLOSED}；失败 → {@code OPEN}</td></tr>
 * </table>
 *
 * <p><b>为什么用状态模式，而不是一堆 if-else？</b>
 * 因为三件事必须成组变化：<b>能否放行、成功怎么办、失败怎么办</b>。
 * 写成 if-else 时，这三段会分散在 {@code tryPass()} / {@code onSuccess()} /
 * {@code onFailure()} 三个方法里，每个方法里都有一份
 * {@code if (state == OPEN) ... else if (state == HALF_OPEN) ...}。
 * 加第四种状态（比如「强制打开，供运维手动切」）就要改三处，
 * 漏改一处就是线上事故。用状态对象，新增状态＝新增一个类，
 * 编译器会替你检查有没有把三个方法都实现。</p>
 *
 * <p><b>为什么不用 {@code enum} + {@code switch}？</b>
 * 这也是合法写法，JDK 21+ 的 {@code switch} 模式匹配对它支持很好。
 * 用 {@code sealed interface} 的好处是：每个状态可以携带<b>自己的字段</b>
 * （{@code OPEN} 需要记「什么时候打开的」），而 {@code enum} 只能把字段摊在外部类上。
 * 代价是多写三个类。<b>状态少且无个性数据时用 enum，状态有各自数据时用对象。</b></p>
 *
 * @author design-patterns-pro
 */
public final class CircuitBreaker {

    /** 三种状态。{@code sealed} 保证 switch / 分支不会漏。 */
    private sealed interface BreakerState
            permits Closed, Open, HalfOpen {

        /** @return 状态名，用于输出 */
        String name();

        /** @return 本次调用是否放行 */
        boolean tryPass(CircuitBreaker breaker);

        /** 一次放行后的成功回调。 */
        void onSuccess(CircuitBreaker breaker);

        /** 一次放行后的失败回调。 */
        void onFailure(CircuitBreaker breaker);
    }

    /** 关闭态：正常放行，只负责数连续失败。 */
    private static final class Closed implements BreakerState {

        @Override
        public String name() {
            return "CLOSED";
        }

        @Override
        public boolean tryPass(CircuitBreaker breaker) {
            return true;
        }

        @Override
        public void onSuccess(CircuitBreaker breaker) {
            breaker.consecutiveFailures = 0;
        }

        @Override
        public void onFailure(CircuitBreaker breaker) {
            breaker.consecutiveFailures++;
            if (breaker.consecutiveFailures >= breaker.failureThreshold) {
                breaker.openedAt = breaker.now();
                breaker.transition(new Open());
            }
        }
    }

    /** 打开态：一律拒绝，直到冷却时间结束。 */
    private static final class Open implements BreakerState {

        @Override
        public String name() {
            return "OPEN";
        }

        @Override
        public boolean tryPass(CircuitBreaker breaker) {
            if (breaker.now() - breaker.openedAt >= breaker.coolDownTicks) {
                breaker.transition(new HalfOpen());
                return true;
            }
            return false;
        }

        @Override
        public void onSuccess(CircuitBreaker breaker) {
            // OPEN 不放行，因此不会收到回调；留空实现而不是抛异常，
            // 因为「状态刚切走但回调晚到一步」在真实系统里是可能发生的
        }

        @Override
        public void onFailure(CircuitBreaker breaker) {
            // 同上，保持幂等
        }
    }

    /** 半开态：放行一次探路请求，用结果决定回到哪边。 */
    private static final class HalfOpen implements BreakerState {

        @Override
        public String name() {
            return "HALF_OPEN";
        }

        @Override
        public boolean tryPass(CircuitBreaker breaker) {
            return true;
        }

        @Override
        public void onSuccess(CircuitBreaker breaker) {
            breaker.consecutiveFailures = 0;
            breaker.transition(new Closed());
        }

        @Override
        public void onFailure(CircuitBreaker breaker) {
            breaker.openedAt = breaker.now();
            breaker.transition(new Open());
        }
    }

    private final String instanceName;
    private final AtomicLong clock;
    private final int failureThreshold;
    private final long coolDownTicks;

    private BreakerState state = new Closed();
    private int consecutiveFailures;
    private long openedAt;
    private int transitionCount;

    /**
     * @param instanceName     被保护的实例名
     * @param clock            共享的逻辑时钟，单位是「tick」
     * @param failureThreshold 连续失败多少次触发熔断
     * @param coolDownTicks    打开后至少过多少个 tick 才允许探路
     */
    CircuitBreaker(String instanceName, AtomicLong clock, int failureThreshold, long coolDownTicks) {
        this.instanceName = instanceName;
        this.clock = clock;
        this.failureThreshold = failureThreshold;
        this.coolDownTicks = coolDownTicks;
    }

    /**
     * @return 被保护的实例名
     */
    public String instanceName() {
        return instanceName;
    }

    /**
     * @return 当前状态名
     */
    public String stateName() {
        return state.name();
    }

    /**
     * @return 连续失败次数
     */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * @return 状态切换次数
     */
    public int transitionCount() {
        return transitionCount;
    }

    /**
     * 询问本次调用是否放行。注意这一步<b>可能顺带完成 {@code OPEN → HALF_OPEN} 的切换</b>。
     *
     * @return 放行返回 {@code true}
     */
    public boolean tryPass() {
        return state.tryPass(this);
    }

    /**
     * 上报一次放行后的结果。
     *
     * @param success 是否成功
     */
    public void record(boolean success) {
        if (success) {
            state.onSuccess(this);
        } else {
            state.onFailure(this);
        }
    }

    /**
     * @return 冷却时长（tick）
     */
    public long coolDownTicks() {
        return coolDownTicks;
    }

    long now() {
        return clock.get();
    }

    long openedAt() {
        return openedAt;
    }

    private void transition(BreakerState next) {
        this.state = next;
        this.transitionCount++;
    }
}
