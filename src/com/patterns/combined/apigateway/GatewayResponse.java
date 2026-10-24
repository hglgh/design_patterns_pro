package com.patterns.combined.apigateway;

/**
 * 网关返回给调用方的响应。<b>不可变</b>，因此可以安全地被 {@link CacheFilter} 缓存、
 * 被多个观察者同时读取。
 *
 * <p><b>为什么要把「谁产生的」也记下来（{@code servedBy}）？</b>
 * 因为这是排查线上问题的第一个问题：「这个 503 是网关自己拒的，还是上游服务吐的？」
 * 两者含义完全不同——前者说明是限流/熔断在保护系统，后者说明上游真的出故障了。
 * 响应里不带这个信息，就只能去翻日志猜。</p>
 *
 * @param status   HTTP 状态码
 * @param body     响应体
 * @param servedBy 产生响应的主体：{@code gateway} 表示网关自身，否则是上游实例名
 * @author design-patterns-pro
 */
public record GatewayResponse(int status, String body, String servedBy) {

    /**
     * 紧凑构造器：把 {@code null} 归一化成合法值，避免下游到处判空。
     */
    public GatewayResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("非法 HTTP 状态码：" + status);
        }
        body = (body == null) ? "" : body;
        servedBy = (servedBy == null || servedBy.isBlank()) ? "gateway" : servedBy;
    }

    /**
     * 上游成功响应。
     *
     * @param body     响应体
     * @param servedBy 具体处理请求的上游实例名
     * @return 200 响应
     */
    public static GatewayResponse ok(String body, String servedBy) {
        return new GatewayResponse(200, body, servedBy);
    }

    /**
     * 网关自身拒绝（认证失败、限流、无路由、熔断快速失败）。
     *
     * @param status 状态码，通常是 401 / 404 / 429 / 503
     * @param reason 拒绝原因，会直接返回给调用方
     * @return {@code servedBy = "gateway"} 的响应
     */
    public static GatewayResponse reject(int status, String reason) {
        return new GatewayResponse(status, reason, "gateway");
    }

    /**
     * 上游返回的失败响应。
     *
     * @param servedBy 出错的上游实例名
     * @param reason   失败原因
     * @return 503 响应
     */
    public static GatewayResponse upstreamError(String servedBy, String reason) {
        return new GatewayResponse(503, reason, servedBy);
    }

    /**
     * 是否 2xx。
     *
     * @return 成功时为 {@code true}
     */
    public boolean success() {
        return status >= 200 && status < 300;
    }

    /**
     * 是否值得重试。
     *
     * <p><b>只重试 5xx，绝不重试 4xx。</b>
     * 400 是「你发错了」，重试只会把错误再发一遍；503 是「我这边忙」，换个实例还有机会。
     * 把「什么该重试」写进方法名，比散落在 {@code if (status == 500 || status == 502 ...)} 里可靠。</p>
     *
     * @return 5xx 时为 {@code true}
     */
    public boolean retryable() {
        return status >= 500;
    }

    /**
     * 是否是<b>上游真的挂了</b>（而不是网关自己拒的）。
     *
     * <p><b>为什么需要它？为什么不能直接用 {@link #retryable()}？</b>
     * 因为「状态码是 5xx」不等同于「换一个实例还有机会」：
     * 熔断器打开时返回的 503 是<b>网关自己</b>拒的，重试只会被同一道门再拒一次，
     * 白白多占一个重试名额；限流返回的 429 同理。</p>
     *
     * <p><b>这个方法是一次真实事故的沉淀。</b>本项目最初的
     * {@code RetryHandler} 用「上游被调用次数」当循环条件，而熔断器拦下的请求
     * 并不会增加这个计数，于是循环条件永远成立——
     * {@code Client} 第一次运行就在「熔断打开期间的请求」那一步 OOM 了。
     * 两个教训：① 循环条件要用<b>本层自己的计数</b>；
     * ② 重试的判定要能区分「上游失败」与「网关拒绝」。</p>
     *
     * @return 5xx 且不是网关自身产生时为 {@code true}
     */
    public boolean upstreamFailure() {
        return retryable() && !"gateway".equals(servedBy);
    }

    /**
     * 一行描述，用于输出。
     *
     * @return 形如 {@code 200  ...  <- order-1} 的字符串
     */
    public String describe() {
        return status + "  " + body + "  <- " + servedBy;
    }
}
