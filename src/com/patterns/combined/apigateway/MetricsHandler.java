package com.patterns.combined.apigateway;

/**
 * 指标装饰器：统计「真正进入上游层」的调用次数。
 *
 * <p><b>为什么它必须在最外层？</b>
 * 它统计的是「一次逻辑调用」，而不是「一次物理重试」。
 * 如果放在 {@link RetryHandler} 里面，3 次重试会被记成 3 次调用，
 * 于是「QPS 300、错误率 0%」这种自相矛盾的监控数据就出来了。</p>
 *
 * <p><b>为什么不用 {@link java.util.concurrent.atomic.LongAdder}？</b>
 * 本 Demo 是单线程的，用不到。真实网关里应该用 {@code LongAdder}
 * （高并发下比 {@code AtomicLong} 快，代价是读取时只能拿到近似值）。</p>
 *
 * @author design-patterns-pro
 */
public final class MetricsHandler implements UpstreamHandler {

    private final UpstreamHandler delegate;
    private int calls;

    /**
     * @param delegate 被包装的处理链
     */
    public MetricsHandler(UpstreamHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public GatewayResponse handle(CallContext context) {
        calls++;
        return delegate.handle(context);
    }

    /**
     * @return 进入上游层的逻辑调用次数（不含缓存命中的请求）
     */
    public int calls() {
        return calls;
    }

    @Override
    public String describe() {
        return "Metrics(" + delegate.describe() + ")";
    }
}
