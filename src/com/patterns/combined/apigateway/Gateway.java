package com.patterns.combined.apigateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 网关本体：<b>它自己几乎不写业务逻辑，只负责「组装」和「广播」</b>。
 *
 * <p>整个网关由两段管线组成，中间用 {@link CallContext} 传递数据：</p>
 * <pre>
 *   请求
 *    │
 *    ├─ 过滤器链（责任链）：auth → rate → router → cache   ─┐
 *    │                                                   │ 任一环短路则直接返回
 *    └───────────────────────────────────────────────────┘
 *                                                        │ 全部放行
 *                                                        ▼
 *      装饰器栈：Metrics( Retry( CircuitBreaker( Service ) ) )
 *                                                        │
 *                                                        ▼
 *                                   响应 → 广播给所有 GatewayListener（观察者）
 * </pre>
 *
 * <p><b>为什么「过滤器链」和「装饰器栈」要分成两段，而不是串成一条？</b>
 * 因为两者的「变化轴」不同：过滤器回答<b>「这个请求要不要进来」</b>，
 * 增删过滤器的频率高、顺序敏感、互相独立；装饰器回答<b>「进来之后怎么调上游」</b>，
 * 层数稳定、顺序有硬性要求（重试必须在熔断外面）。
 * 混成一条链的结果是：链长十几环，谁都说不清第 7 环为什么在第 8 环前面。
 * <b>分开的标准不是「看起来整齐」，而是「它们因为不同的原因而变化」。</b></p>
 *
 * <p><b>为什么 {@code handle} 要把 {@link CallContext} 一起返回？</b>
 * 因为「响应」只说结论，「轨迹」说明过程。教学场景里必须能同时看到两者，
 * 否则读者无法判断一个 200 是缓存给的、还是上游给的，也无法判断
 * 一个 503 是熔断器挡的、还是上游真的挂了。</p>
 *
 * @author design-patterns-pro
 */
public final class Gateway {

    private final RouteTable routeTable;
    private final LoadBalancer balancer;
    private final RateLimitFilter rateLimitFilter;
    private final CacheFilter cacheFilter;
    private final ServiceHandler serviceHandler;
    private final CircuitBreakerHandler breakerHandler;
    private final RetryHandler retryHandler;
    private final MetricsHandler metricsHandler;
    private final FilterChain filterChain;
    private final List<GatewayListener> listeners = new ArrayList<>();

    private int served;

    /**
     * 构造一个网关。所有参数都显式传入，<b>没有任何一个模式在内部被 {@code new}</b> 出来——
     * 这正是「组合」与「堆砌」的区别：组合的对象可以单独测试、单独替换。
     *
     * @param balancer          负载均衡策略（策略模式）
     * @param scriptedFailures  上游故障脚本：实例名 → 需要失败的次数
     * @param maxAttempts       上游最多尝试次数（1 表示不重试）
     * @param breakerThreshold  连续失败多少次打开熔断器
     * @param coolDownTicks     熔断打开后冷却多少个 tick
     * @param rateCapacity      每个调用方每窗口的限流配额
     */
    public Gateway(LoadBalancer balancer,
                   Map<String, Integer> scriptedFailures,
                   int maxAttempts,
                   int breakerThreshold,
                   long coolDownTicks,
                   int rateCapacity) {
        if (balancer == null) {
            throw new IllegalArgumentException("balancer 不能为空");
        }
        this.balancer = balancer;
        this.routeTable = new RouteTable();
        this.rateLimitFilter = new RateLimitFilter(rateCapacity);
        this.cacheFilter = new CacheFilter();

        // 装饰器栈由内向外组装：Service 是最内层，Metrics 是最外层。
        this.serviceHandler = new ServiceHandler(scriptedFailures == null ? Map.of() : scriptedFailures);
        this.breakerHandler = new CircuitBreakerHandler(serviceHandler, breakerThreshold, coolDownTicks);
        this.retryHandler = new RetryHandler(breakerHandler, maxAttempts);
        this.metricsHandler = new MetricsHandler(retryHandler);

        List<GatewayFilter> filters = List.of(
                new AuthFilter(),
                rateLimitFilter,
                new RouterFilter(routeTable, balancer),
                cacheFilter);
        this.filterChain = new FilterChain(filters, metricsHandler);
    }

    /**
     * 常用的默认配置：最少连接 + 最多两次尝试 + 熔断阈值 2 + 冷却 3 个 tick + 每窗口 3 次。
     *
     * @param scriptedFailures 上游故障脚本
     * @return 网关实例
     */
    public static Gateway createDefault(Map<String, Integer> scriptedFailures) {
        return new Gateway(LoadBalancer.leastConnections(), scriptedFailures, 2, 2, 3, 3);
    }

    /**
     * 订阅事件。观察者的注册是<b>运行时</b>的，不需要修改网关的构造参数——
     * 这就是「新增一个下游系统不改网关」的具体含义。
     *
     * @param listener 监听器
     */
    public void addListener(GatewayListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener 不能为空");
        }
        listeners.add(listener);
    }

    /**
     * 处理一个请求。
     *
     * <p><b>注意这里没有 try/catch。</b>网关不该吞掉异常：
     * 过滤器或装饰器抛出的运行时异常代表<b>网关自身的缺陷</b>，
     * 让它带着完整堆栈冒到最外层（真实系统由统一的异常兜底层转成 500），
     * 比在这里静默返回一个 500 更容易定位问题。</p>
     *
     * @param request 请求
     * @return 响应 + 上下文轨迹
     */
    public Result handle(GatewayRequest request) {
        FilterChain.Cursor cursor = filterChain.newCall(request);
        GatewayResponse response = cursor.proceed();
        CallContext context = cursor.context();

        GatewayListener.Event event = new GatewayListener.Event(
                request.requestId(),
                request.path(),
                response.status(),
                response.servedBy(),
                context.instance() == null ? null : context.instance().name(),
                context.cacheHit(),
                context.attempts());
        served++;
        for (GatewayListener listener : listeners) {
            listener.onEvent(event);
        }
        return new Result(response, context, event);
    }

    /**
     * 推进逻辑时钟，用于演示「熔断器冷却结束」。
     *
     * @param ticks 推进的 tick 数
     */
    public void advance(long ticks) {
        breakerHandler.advance(ticks);
    }

    /**
     * 进入下一个限流窗口。
     */
    public void rollRateWindow() {
        rateLimitFilter.rollWindow();
    }

    /**
     * 清空缓存（真实网关靠 TTL，这里由调用方显式控制，保证输出可复现）。
     */
    public void clearCache() {
        cacheFilter.clear();
    }

    /**
     * @return 已处理的请求总数
     */
    public int served() {
        return served;
    }

    /**
     * @return 过滤器链的结构，形如 {@code auth → rate → router → cache → Metrics(...)}
     */
    public String describeTopology() {
        return filterChain.describe();
    }

    /**
     * @return 路由表（享元工厂）
     */
    public RouteTable routeTable() {
        return routeTable;
    }

    /**
     * @return 负载均衡策略
     */
    public LoadBalancer balancer() {
        return balancer;
    }

    /**
     * @return 限流过滤器
     */
    public RateLimitFilter rateLimit() {
        return rateLimitFilter;
    }

    /**
     * @return 缓存过滤器
     */
    public CacheFilter cache() {
        return cacheFilter;
    }

    /**
     * @return 最内层的上游服务
     */
    public ServiceHandler service() {
        return serviceHandler;
    }

    /**
     * @return 熔断装饰器，可用来查询每个实例的状态
     */
    public CircuitBreakerHandler breaker() {
        return breakerHandler;
    }

    /**
     * @return 重试装饰器
     */
    public RetryHandler retry() {
        return retryHandler;
    }

    /**
     * @return 指标装饰器（统计进入上游层的逻辑调用次数）
     */
    public MetricsHandler metrics() {
        return metricsHandler;
    }

    /**
     * 一次调用的完整结果：响应、上下文、广播出去的事件。
     *
     * @param response 返回给调用方的响应
     * @param context  处理过程上下文（含轨迹、命中实例、真实尝试次数）
     * @param event    广播给观察者的事件
     */
    public record Result(GatewayResponse response, CallContext context, GatewayListener.Event event) {

        /**
         * @return 处理轨迹，形如 {@code auth: 通过 u-1001 → rate: ... }
         */
        public String trace() {
            return context.describeTrace();
        }
    }
}
