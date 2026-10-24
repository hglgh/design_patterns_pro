package com.patterns.combined.apigateway;

/**
 * 「把请求真正打到上游」的那一环。
 *
 * <p>它是一个只有一个方法的接口，因此可以像洋葱一样层层包裹：
 * <pre>
 *   Metrics( Retry( CircuitBreaker( Service ) ) )
 * </pre>
 * 每一层只做一件事，任何一层都能独立增删——<b>这就是装饰器模式（Decorator）</b>。</p>
 *
 * <p><b>为什么不用继承？</b>
 * 三层需求两两组合是 2³ = 8 个子类（要不要指标 × 要不要重试 × 要不要熔断），
 * 而且顺序固定死。用包装，顺序在组装时用一行代码决定，
 * 想加第四层也不用改任何一个已有类。</p>
 *
 * <p><b>为什么这一环不放进过滤器链？</b>
 * 因为过滤器链处理的是「请求要不要进入系统」（认证、限流、缓存），
 * 而这一环处理的是「进入之后怎么调上游」（重试、熔断、指标）。
 * 两类关注点混在一条链上，链会变得既长又难排顺序。</p>
 *
 * @author design-patterns-pro
 */
public interface UpstreamHandler {

    /**
     * 处理一次上游调用。
     *
     * @param context 本次调用的上下文，已包含选中的实例
     * @return 上游或包装层产生的响应
     */
    GatewayResponse handle(CallContext context);

    /**
     * 描述自身的包装结构，用于输出「洋葱有几层」。
     *
     * @return 形如 {@code Metrics(Retry(CircuitBreaker(Service)))} 的字符串
     */
    default String describe() {
        return getClass().getSimpleName();
    }
}
