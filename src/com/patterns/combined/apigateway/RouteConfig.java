package com.patterns.combined.apigateway;

import java.util.List;

/**
 * 一条路由的配置：<b>这是享元（Flyweight）里的「享元」</b>。
 *
 * <p>一个网关每秒可能收到几万个请求，但它们只对应几十条路由。
 * 如果每个请求都新建一份「路径前缀 → 实例列表」的配置，就是几万个一模一样的对象。
 * 把这条配置做成不可变对象、按 {@code (service, version)} 共享一份，
 * 就是享元模式在网关里最典型的落点。</p>
 *
 * <p><b>为什么必须是不可变？</b>
 * 因为它被成千上万个请求共享。只要有一个请求能改到它，
 * 影响面就是「所有走这条路由的请求」——这类 bug 的排查成本极高。
 * 所以 {@code instances} 在构造时 {@link List#copyOf} 一次，
 * 外部拿到的永远是只读视图。</p>
 *
 * @param service    服务名，例如 {@code order}
 * @param version    版本号，例如 {@code v1}
 * @param pathPrefix 路径前缀，例如 {@code /api/order}
 * @param instances  该服务下的实例列表，不可为空
 * @author design-patterns-pro
 */
public record RouteConfig(String service, String version, String pathPrefix, List<UpstreamInstance> instances) {

    /**
     * 紧凑构造器：集中校验 + 防御性拷贝。
     */
    public RouteConfig {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service 不能为空");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (pathPrefix == null || !pathPrefix.startsWith("/")) {
            throw new IllegalArgumentException("pathPrefix 必须以 / 开头，收到：" + pathPrefix);
        }
        if (instances == null || instances.isEmpty()) {
            throw new IllegalArgumentException("路由 " + service + ":" + version + " 至少需要一个实例");
        }
        // 关键一行：调用方之后无论怎么改传进来的 List，都影响不到这份共享配置
        instances = List.copyOf(instances);
    }

    /**
     * 享元池的 key。
     *
     * <p><b>为什么不是字符串拼接？</b>
     * {@code service + ":" + version} 在 {@code service} 为 {@code null} 时
     * 会得到 {@code "null:v1"}，与真的传了字面量 {@code "null"} 撞成同一个 key。
     * 本项目在享元模块实测过这个碰撞：{@code ('a', null)} 与 {@code ('a', "null")}
     * 落到了同一个池条目上。用 {@code record} 当 key，{@code null} 和 {@code "null"} 天然不同。</p>
     *
     * @return 形如 {@code order:v1} 的展示用字符串（<b>不</b>用于查池）
     */
    public String routeKey() {
        return service + ":" + version;
    }

    /**
     * 路径是否命中本路由。
     *
     * @param path 请求路径
     * @return 以 {@code pathPrefix} 开头时返回 {@code true}
     */
    public boolean matches(String path) {
        return path != null && path.startsWith(pathPrefix);
    }

    /**
     * 实例名列表，用于输出。
     *
     * @return 形如 {@code [order-1, order-2]} 的字符串
     */
    public String describeInstances() {
        return instances.stream().map(UpstreamInstance::name).toList().toString();
    }
}
