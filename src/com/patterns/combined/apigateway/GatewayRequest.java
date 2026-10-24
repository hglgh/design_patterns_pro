package com.patterns.combined.apigateway;

import java.util.Locale;

/**
 * 网关收到的一个请求。<b>不可变</b>——它在整条过滤器链里被反复读取，
 * 一旦可变，任何一个过滤器都能改掉 {@code path} 而让后面的路由跑到别的服务上。
 *
 * <p><b>为什么 {@code token} 允许为 {@code null}？</b>
 * 匿名请求必须能被「认证过滤器」拒绝并返回 401，
 * 而不是在构造阶段就抛异常——那样错误就变成了「网关崩了」而不是「你没登录」。
 * <b>校验的位置决定了错误的语义</b>。</p>
 *
 * @param requestId 请求编号，用于串联日志
 * @param method    HTTP 方法，构造时统一转大写
 * @param path      请求路径，必须以 {@code /} 开头
 * @param token     {@code Authorization} 头，可为 {@code null}
 * @param clientIp  调用方 IP，限流按它计数
 * @author design-patterns-pro
 */
public record GatewayRequest(String requestId, String method, String path, String token, String clientIp) {

    /**
     * 紧凑构造器：所有字段集中校验一次，方法体内不再防御性判空。
     */
    public GatewayRequest {
        requestId = require(requestId, "requestId");
        method = require(method, "method").toUpperCase(Locale.ROOT);
        path = require(path, "path");
        clientIp = require(clientIp, "clientIp");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path 必须以 / 开头，收到：" + path);
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    /**
     * 是否为读请求。缓存过滤器只缓存 GET，原因见 {@link CacheFilter}。
     *
     * @return {@code method} 为 GET 时返回 {@code true}
     */
    public boolean isGet() {
        return "GET".equals(method);
    }

    /**
     * 一行描述，用于日志与文档输出。
     *
     * @return 形如 {@code GET /api/order  (r-1, 10.0.0.1)} 的字符串
     */
    public String describe() {
        return method + " " + path + "  (" + requestId + ", " + clientIp + ")";
    }
}
