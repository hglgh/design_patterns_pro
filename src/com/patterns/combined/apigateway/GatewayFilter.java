package com.patterns.combined.apigateway;

/**
 * 网关过滤器：<b>这是责任链模式（Chain of Responsibility）</b>。
 *
 * <p>每一个过滤器只回答一个问题：「这个请求能不能继续往下走？」</p>
 * <ul>
 *   <li>能 → 调用 {@code next.proceed()}，把决定权交给下一个；</li>
 *   <li>不能 → 直接返回响应，<b>后面的过滤器完全不会被执行</b>。</li>
 * </ul>
 *
 * <p><b>「短路」是责任链最有价值、也最容易被写错的地方。</b>
 * 认证失败时必须短路：一个没登录的请求不该消耗限流配额、不该读缓存、
 * 更不该打到上游。如果 {@link AuthFilter} 忘了 {@code return}，
 * 后面的代码继续执行，那么「认证」就退化成一个只打印日志的摆设。</p>
 *
 * <p><b>为什么是抽象类而不是接口？</b>
 * 这里两者都行，选抽象类是为了后续可以放 {@code final} 的模板逻辑
 * （比如统一的耗时统计）。<b>接口给能力，抽象类给骨架</b>——
 * 现在还没有骨架，所以这个选择是可逆的、不重要的；
 * 项目里其他模块用接口，是因为它们确实只需要能力。</p>
 *
 * @author design-patterns-pro
 */
public abstract class GatewayFilter {

    /**
     * @return 过滤器名，会出现在处理轨迹里
     */
    public abstract String name();

    /**
     * 处理请求。
     *
     * @param context 调用上下文，可读可写
     * @param next    下一环。<b>调用它才代表放行</b>
     * @return 最终响应：要么是自己拒绝的，要么是链尾（或更后面的过滤器）产生的
     */
    public abstract GatewayResponse handle(CallContext context, FilterChain.Cursor next);
}
