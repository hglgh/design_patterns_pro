package com.patterns.combined.apigateway;

/**
 * 路由过滤器：把路径解析成路由配置，并选一台上游实例。
 *
 * <p>它把三个模式串在了一起，但<b>没有把任何一个模式的代码搬到一起</b>：</p>
 * <ul>
 *   <li>查路由配置 → {@link RouteTable}（享元工厂，返回共享的 {@link RouteConfig}）；</li>
 *   <li>选实例 → {@link LoadBalancer}（策略接口，具体算法在外部注入）；</li>
 *   <li>结果写入上下文 → 后面的过滤器与装饰器都能读到。</li>
 * </ul>
 * <p><b>这就是组合模式的正确形态：模式之间通过对象协作，而不是通过代码合并。</b>
 * 如果路由过滤器里出现 {@code new RoundRobinBalancer()}，
 * 享元与策略就都被焊死在这里了，单元测试也没法替换。</p>
 *
 * @author design-patterns-pro
 */
public final class RouterFilter extends GatewayFilter {

    private final RouteTable routeTable;
    private final LoadBalancer balancer;

    /**
     * @param routeTable 路由表（享元工厂）
     * @param balancer   负载均衡策略
     */
    public RouterFilter(RouteTable routeTable, LoadBalancer balancer) {
        this.routeTable = routeTable;
        this.balancer = balancer;
    }

    @Override
    public String name() {
        return "router";
    }

    @Override
    public GatewayResponse handle(CallContext context, FilterChain.Cursor next) {
        RouteTable.Key key = routeTable.resolve(context.request().path());
        if (key == null) {
            context.trace("router: 无匹配路由 → 短路 404");
            return GatewayResponse.reject(404, "没有匹配的路由：" + context.request().path());
        }

        // 享元池：同一个 key 拿到的永远是同一个 RouteConfig 对象
        RouteConfig route = routeTable.config(key);
        context.route(route);
        context.balancer(balancer);

        UpstreamInstance instance = balancer.pick(route.instances());
        context.instance(instance);
        context.trace("router: " + route.routeKey() + " → " + instance.name()
                + "（" + balancer.name() + "）");
        return next.proceed();
    }

    /**
     * @return 使用的路由表
     */
    public RouteTable routeTable() {
        return routeTable;
    }

    /**
     * @return 使用的负载均衡策略
     */
    public LoadBalancer balancer() {
        return balancer;
    }
}
