package com.patterns.combined.apigateway;

/**
 * 重试装饰器：<b>失败时换一台实例再试一次</b>。
 *
 * <p><b>它必须包在熔断器外面。</b>看两种顺序的区别：</p>
 * <pre>
 *   正确：Retry( CircuitBreaker( Service ) )   每次重试都经过熔断器判断
 *   错误：CircuitBreaker( Retry( Service ) )   熔断器只看到"最终结果"，
 *                                              上游已经连挂 3 次它才知道，
 *                                              而且 3 次重试全部打在了同一个故障实例上
 * </pre>
 * <p><b>装饰器的顺序不是审美问题，是正确性问题。</b>这也是组合模式最难的部分：
 * 单个模式都简单，难在你得说清楚「谁在外面、为什么」。</p>
 *
 * <p><b>为什么只重试可重试的上游失败？</b>
 * {@link GatewayResponse#upstreamFailure()} 已经把规则写死了：只重 5xx，
 * 而且<b>排除网关自己拒的 503</b>（熔断打开）。
 * 重试 401 只会让用户多等一会儿还是 401；重试 429 更是主动加剧限流；
 * 重试被熔断器挡下的请求，只会被同一道门再拒一次。</p>
 *
 * <p><b>为什么循环条件用本地变量 {@code tried}，而不是 {@code context.attempts()}？</b>
 * 这是本篇踩过的真实坑：{@code context.attempts()} 统计的是
 * 「真实打到上游的次数」，而熔断器拦下的尝试<b>不会增加它</b>。
 * 两者一旦不等，`尝试次数 &lt; 上限` 这个条件就会永远成立——
 * 本 Demo 第一次运行就在「熔断打开期间的请求」处无限循环直至 OOM。
 * <b>重试的循环变量必须由重试层自己维护</b>，这是一个很小但很容易犯的错误。</p>
 *
 * @author design-patterns-pro
 */
public final class RetryHandler implements UpstreamHandler {

    private final UpstreamHandler delegate;
    private final int maxAttempts;

    /**
     * @param delegate    被包装的处理链
     * @param maxAttempts 最多尝试次数（含首次），必须 ≥ 1
     */
    public RetryHandler(UpstreamHandler delegate, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 ≥ 1，收到：" + maxAttempts);
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public GatewayResponse handle(CallContext context) {
        GatewayResponse response = delegate.handle(context);
        int tried = 1;
        while (response.upstreamFailure() && tried < maxAttempts) {
            UpstreamInstance from = context.instance();
            UpstreamInstance to = context.repickInstance();
            tried++;
            context.trace("retry: 第 " + tried + " 次尝试，" + from + " → " + to);
            response = delegate.handle(context);
        }
        if (!response.success() && maxAttempts > 1) {
            context.trace("retry: 本层已尝试 " + tried + " 次，不再重试");
        }
        return response;
    }

    /**
     * @return 最多尝试次数
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public String describe() {
        return "Retry(" + delegate.describe() + ")";
    }
}
