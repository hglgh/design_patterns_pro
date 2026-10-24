package com.patterns.combined.apigateway;

import java.util.Map;

/**
 * 认证过滤器：<b>链上的第一环，也是最典型的「短路」场景</b>。
 *
 * <p><b>为什么它必须排第一？</b>
 * 因为排在它后面的每一环都有成本：限流要读写计数器、路由要查享元池、
 * 缓存要算 key。一个没登录的请求不值得花这些成本，
 * 更重要的是——<b>未认证请求不该有权消耗任何人的配额</b>。</p>
 *
 * <p><b>为什么把 token 到用户的映射写死？</b>
 * 真实系统会验签 JWT 或查会话。这里写死是为了输出可复现，
 * 但保留了三个必须区分的分支，它们对应三种完全不同的线上问题：</p>
 * <ol>
 *   <li>没带 token —— 客户端忘了传；</li>
 *   <li>token 过期 —— 客户端该刷新了；</li>
 *   <li>token 不认识 —— 可能是伪造，也可能是网关和鉴权服务不同步。</li>
 * </ol>
 * <p>三者在监控上应该用不同的错误码/标签区分，否则「401 突增」
 * 这条告警无法定位到底是哪一类。</p>
 *
 * @author design-patterns-pro
 */
public final class AuthFilter extends GatewayFilter {

    /** 合法 token → 主体标识。 */
    private static final Map<String, String> TOKENS = Map.of(
            "Bearer t-1001", "u-1001",
            "Bearer t-1002", "u-1002");

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public GatewayResponse handle(CallContext context, FilterChain.Cursor next) {
        String token = context.request().token();

        if (token == null || token.isBlank()) {
            context.trace("auth: 缺少 Authorization → 短路 401");
            return GatewayResponse.reject(401, "缺少 Authorization 头");
        }
        if ("Bearer expired".equals(token)) {
            context.trace("auth: token 已过期 → 短路 401");
            return GatewayResponse.reject(401, "token 已过期，请刷新后重试");
        }
        String principal = TOKENS.get(token);
        if (principal == null) {
            context.trace("auth: 未知 token → 短路 401");
            return GatewayResponse.reject(401, "无法识别的 token");
        }

        context.principal(principal);
        context.trace("auth: 通过 " + principal);
        return next.proceed();
    }
}
