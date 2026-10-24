package com.patterns.combined.apigateway;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>一次调用的上下文</b>：随请求创建、随请求销毁，承载过滤器之间传递的数据。
 *
 * <p><b>为什么需要它？</b>
 * 如果没有它，{@link AuthFilter} 认证出的用户身份要传给 {@link CacheFilter} 做缓存 key，
 * 就只能把返回值改成一个巨大的「复合结果」对象，让每个过滤器都去拆包。
 * 有了上下文，过滤器就变成「读请求、写上下文、决定要不要继续」三件事。
 * 这和 Servlet 的 {@code request.setAttribute()}、Spring 的
 * {@code RequestContextHolder} 是同一个思路。</p>
 *
 * <p><b>为什么它可以可变，而 {@link GatewayRequest} 不可变？</b>
 * 两者的生命周期不同：请求对象描述「调用方发来的东西」，任何过滤器都无权修改它；
 * 上下文描述「网关处理到哪一步了」，天生就是要被不断写入的。
 * <b>把不变的东西做成不可变，把要变的东西集中到一个明确的对象里</b>，
 * 这是本项目在订单示例里用的同一套办法（{@code OrderContext}）。</p>
 *
 * <p><b>为什么 {@code trace} 是 {@link ArrayList} 而不是线程安全的集合？</b>
 * 因为上下文<b>只属于一个请求</b>，永远只被一个线程处理。
 * 给它加锁等于给「只有一个人用的抽屉」上锁，只会变慢。</p>
 *
 * @author design-patterns-pro
 */
public final class CallContext {

    private final GatewayRequest request;
    private final List<String> trace = new ArrayList<>();

    private String principal;
    private RouteConfig route;
    private LoadBalancer balancer;
    private UpstreamInstance instance;
    private int attempts;
    private boolean cacheHit;

    /**
     * @param request 本次调用的请求，构造后不再替换
     */
    public CallContext(GatewayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        this.request = request;
    }

    /**
     * @return 原始请求
     */
    public GatewayRequest request() {
        return request;
    }

    /**
     * @return 认证后的主体标识，未认证时为 {@code null}
     */
    public String principal() {
        return principal;
    }

    /**
     * 记录认证结果。由 {@link AuthFilter} 写入。
     *
     * @param principal 主体标识
     */
    public void principal(String principal) {
        this.principal = principal;
    }

    /**
     * @return 命中的路由配置，未路由时为 {@code null}
     */
    public RouteConfig route() {
        return route;
    }

    /**
     * 记录路由结果（享元对象）。由 {@link RouterFilter} 写入。
     *
     * @param route 共享的路由配置
     */
    public void route(RouteConfig route) {
        this.route = route;
    }

    /**
     * @return 选路策略，未路由时为 {@code null}
     */
    public LoadBalancer balancer() {
        return balancer;
    }

    /**
     * 记录本次调用使用的负载均衡策略。由 {@link RouterFilter} 写入。
     *
     * @param balancer 选路策略
     */
    public void balancer(LoadBalancer balancer) {
        this.balancer = balancer;
    }

    /**
     * @return 当前选中的上游实例
     */
    public UpstreamInstance instance() {
        return instance;
    }

    /**
     * 设置当前上游实例。首次选路和重试换实例都调用它。
     *
     * @param instance 上游实例
     */
    public void instance(UpstreamInstance instance) {
        this.instance = instance;
    }

    /**
     * 换一个上游实例，用于重试。
     *
     * <p><b>为什么重试必须换实例？</b>
     * 如果刚才那台机器正在过载，原样再打一次大概率还是 503，
     * 只是把同样的失败重放一遍，还多占了一次配额。</p>
     *
     * @return 换到的实例，没有其他候选时返回原实例
     */
    public UpstreamInstance repickInstance() {
        if (balancer == null || route == null) {
            throw new IllegalStateException("尚未路由，无法换实例");
        }
        UpstreamInstance next = balancer.pickExcluding(route.instances(), instance);
        this.instance = next;
        return next;
    }

    /**
     * @return 已经打了几次上游（含重试），被缓存短路时为 0
     */
    public int attempts() {
        return attempts;
    }

    /**
     * 记录一次真实的上游调用。
     */
    public void markAttempt() {
        attempts++;
    }

    /**
     * @return 是否由缓存直接返回
     */
    public boolean cacheHit() {
        return cacheHit;
    }

    /**
     * 标记本次响应来自缓存。
     *
     * @param cacheHit 是否命中缓存
     */
    public void cacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    /**
     * 追加一条处理轨迹。轨迹是「拆解讲解」的基础：
     * 结果相同但轨迹不同，说明模式起了作用。
     *
     * @param step 一步描述
     */
    public void trace(String step) {
        trace.add(step);
    }

    /**
     * @return 处理轨迹的只读副本
     */
    public List<String> trace() {
        return List.copyOf(trace);
    }

    /**
     * 把轨迹拼成一行。
     *
     * @return 形如 {@code auth: 通过 u-1001 → rate: 1/3 → ...} 的字符串
     */
    public String describeTrace() {
        return String.join(" → ", trace);
    }
}
