package com.patterns.combined.apigateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 熔断装饰器：为<b>每个上游实例</b>维护一个 {@link CircuitBreaker}。
 *
 * <p><b>为什么按实例而不是按服务熔断？</b>
 * 一个服务有 10 台机器，只有 1 台出问题。按服务熔断的结果是
 * 「因为有 1 台坏了，所以整个服务 800 个请求全部被拒」——
 * 这是把局部故障放大成全局故障。按实例熔断，坏的被隔离，好的继续服务，
 * 这才是熔断的意义。</p>
 *
 * <p><b>为什么熔断器挡住请求时要返回 503，而不是继续打上游？</b>
 * 熔断的作用是<b>给下游喘息时间</b>，同时让调用方快速失败、立刻走降级逻辑。
 * 如果还打上游，那它和没有熔断的唯一区别就是多了一层代码。</p>
 *
 * <p><b>时间为什么是「tick」而不是毫秒？</b>
 * 因为本项目的所有输出必须可复现。真实实现用
 * {@code System.nanoTime()} 或 {@code Clock}；这里用一个由
 * {@link #advance(long)} 手动推进的逻辑时钟，把「等待 30 秒」
 * 压缩成「推进 3 个 tick」，同时保证两次运行结果逐字节一致。</p>
 *
 * @author design-patterns-pro
 */
public final class CircuitBreakerHandler implements UpstreamHandler {

    private final UpstreamHandler delegate;
    private final AtomicLong clock = new AtomicLong();
    private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
    private final int failureThreshold;
    private final long coolDownTicks;

    /**
     * @param delegate         被包装的处理链
     * @param failureThreshold 连续失败多少次触发熔断
     * @param coolDownTicks    打开后冷却多少个 tick 才允许探路
     */
    public CircuitBreakerHandler(UpstreamHandler delegate, int failureThreshold, long coolDownTicks) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold 必须 ≥ 1");
        }
        if (coolDownTicks < 0) {
            throw new IllegalArgumentException("coolDownTicks 不能为负");
        }
        this.delegate = delegate;
        this.failureThreshold = failureThreshold;
        this.coolDownTicks = coolDownTicks;
    }

    @Override
    public GatewayResponse handle(CallContext context) {
        CircuitBreaker breaker = breakerOf(context.instance().name());
        String before = breaker.stateName();

        boolean passed = breaker.tryPass();
        String afterPass = breaker.stateName();

        // OPEN → HALF_OPEN 是在 tryPass() 内部顺带完成的，必须单独打一条轨迹，
        // 否则读者只会看到后面的「HALF_OPEN → CLOSED」，却不知道它是怎么进入 HALF_OPEN 的。
        if (!afterPass.equals(before)) {
            context.trace("breaker: " + breaker.instanceName() + " " + before + " → " + afterPass
                    + "（冷却 " + breaker.coolDownTicks() + " tick 已到，放行一次探路）");
        }

        if (!passed) {
            context.trace("breaker: " + breaker.instanceName() + " 处于 " + breaker.stateName()
                    + " → 快速失败，未调用上游");
            return GatewayResponse.reject(503, "熔断器打开（" + breaker.instanceName() + "），请求未打到上游");
        }

        GatewayResponse response = delegate.handle(context);
        breaker.record(response.success());

        String after = breaker.stateName();
        if (!after.equals(afterPass)) {
            context.trace("breaker: " + breaker.instanceName() + " " + afterPass + " → " + after);
        }
        return response;
    }

    /**
     * 推进逻辑时钟，用于演示「冷却时间到达」。
     *
     * @param ticks 推进的 tick 数
     */
    public void advance(long ticks) {
        clock.addAndGet(ticks);
    }

    /**
     * @return 当前逻辑时间
     */
    public long now() {
        return clock.get();
    }

    /**
     * 取某个实例的熔断器，不存在则创建。
     *
     * @param instanceName 实例名
     * @return 该实例的熔断器
     */
    public CircuitBreaker breakerOf(String instanceName) {
        return breakers.computeIfAbsent(instanceName, name ->
                new CircuitBreaker(name, clock, failureThreshold, coolDownTicks));
    }

    /**
     * 取所有已创建的熔断器。
     *
     * @return 实例名 → 熔断器
     */
    public Map<String, CircuitBreaker> breakers() {
        return Map.copyOf(breakers);
    }

    /**
     * 一行汇总所有熔断器状态，用于输出。
     *
     * @return 形如 {@code order-1=CLOSED, report-1=OPEN} 的字符串
     */
    public String describeStates() {
        return breakers.values().stream()
                .map(breaker -> breaker.instanceName() + "=" + breaker.stateName())
                .collect(Collectors.joining(", "));
    }

    @Override
    public String describe() {
        return "CircuitBreaker(" + delegate.describe() + ")";
    }
}
