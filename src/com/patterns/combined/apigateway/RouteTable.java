package com.patterns.combined.apigateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 路由表：<b>享元工厂</b>（Flyweight Factory）。
 *
 * <p>它做两件事，这两件事在概念上是分开的，代码里也分开放：</p>
 * <ol>
 *   <li>{@link #resolve(String)}：路径 → 路由 key。这是「注册中心数据」，启动时固定；</li>
 *   <li>{@link #config(Key)}：key → <b>共享的</b> {@link RouteConfig}。这是享元池。</li>
 * </ol>
 *
 * <p><b>如果没有 {@code config} 这一层，会发生什么？</b>
 * 每个请求都 {@code new RouteConfig(...)}：对象本身不大，但
 * ① 每次都要重新创建 {@link UpstreamInstance} 列表，
 * ② 「最少连接」策略读到的 {@code inFlight} 会分散到几万个副本上，全部是 0，策略直接失效。
 * 这是享元最容易被忽略的价值：<b>共享不只为省内存，更是为了让「同一份状态」只有一个真相。</b></p>
 *
 * <p><b>为什么池用 {@link ConcurrentHashMap} 而不是 {@link java.util.HashMap}？</b>
 * 本项目在享元模块实测过：{@code HashMap + computeIfAbsent} 在 16 线程 × 3000 轮压力下
 * 有 <b>5/3000 轮</b>出现同一 key 被创建出多个实例。单线程 Demo 看不出来，
 * 但网关是多线程的，所以这里直接用并发容器。</p>
 *
 * @author design-patterns-pro
 */
public final class RouteTable {

    /**
     * 享元池的 key。
     *
     * <p>用 {@code record} 而不是字符串拼接：{@code (null, "v1")} 与 {@code ("null", "v1")}
     * 是两个不同的 key，而字符串拼接会把它们合并成一个。</p>
     *
     * @param service 服务名
     * @param version 版本号
     */
    public record Key(String service, String version) {
    }

    /** 注册中心里的原始数据：路径前缀 + 实例名列表。 */
    private record Blueprint(String pathPrefix, List<String> instanceNames) {
    }

    private final Map<Key, Blueprint> registry = new LinkedHashMap<>();
    private final ConcurrentMap<Key, RouteConfig> pool = new ConcurrentHashMap<>();
    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicInteger creationCount = new AtomicInteger();

    /**
     * 构建一张固定的路由表。真实网关从配置中心拉取，这里写死以保证输出可复现。
     */
    public RouteTable() {
        registry.put(new Key("order", "v1"),
                new Blueprint("/api/order", List.of("order-1", "order-2")));
        registry.put(new Key("pay", "v1"),
                new Blueprint("/api/pay", List.of("pay-1")));
        registry.put(new Key("report", "v1"),
                new Blueprint("/api/report", List.of("report-1")));
        registry.put(new Key("user", "v2"),
                new Blueprint("/api/user", List.of("user-1", "user-2")));
    }

    /**
     * 按路径找路由 key。
     *
     * @param path 请求路径
     * @return 命中的 key，没有匹配时返回 {@code null}
     */
    public Key resolve(String path) {
        for (Map.Entry<Key, Blueprint> entry : registry.entrySet()) {
            if (path.startsWith(entry.getValue().pathPrefix())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 取共享的路由配置。同一个 key 永远返回<b>同一个对象</b>。
     *
     * @param key 路由 key
     * @return 共享的 {@link RouteConfig}
     */
    public RouteConfig config(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为空");
        }
        RouteConfig cached = pool.get(key);
        if (cached != null) {
            hitCount.incrementAndGet();
            return cached;
        }
        // 只有真正发生创建时才会执行 mapping function，计数因此是准确的
        return pool.computeIfAbsent(key, k -> {
            creationCount.incrementAndGet();
            return build(k);
        });
    }

    private RouteConfig build(Key key) {
        Blueprint blueprint = registry.get(key);
        if (blueprint == null) {
            throw new IllegalStateException("注册中心没有这条路由：" + key);
        }
        List<UpstreamInstance> instances = blueprint.instanceNames().stream()
                .map(name -> new UpstreamInstance(name, name.endsWith("-2") ? "az-b" : "az-a"))
                .toList();
        return new RouteConfig(key.service(), key.version(), blueprint.pathPrefix(), instances);
    }

    /**
     * @return 池命中次数
     */
    public int hitCount() {
        return hitCount.get();
    }

    /**
     * @return 池创建次数（等于池内不同 key 的数量）
     */
    public int creationCount() {
        return creationCount.get();
    }

    /**
     * @return 池内条目数
     */
    public int poolSize() {
        return pool.size();
    }

    /**
     * 命中率，用于输出。
     *
     * @return 命中率百分比，分母为 0 时返回 {@code 0.0}
     */
    public double hitRate() {
        int total = hitCount.get() + creationCount.get();
        return total == 0 ? 0.0 : hitCount.get() * 100.0 / total;
    }

    /**
     * 一行汇总，用于输出。
     *
     * @return 形如 {@code 池内 2 条路由，命中 5 次，创建 2 次，命中率 71.4%} 的字符串
     */
    public String describePool() {
        return String.format(Locale.ROOT,
                "池内 %d 条路由，命中 %d 次，创建 %d 次，命中率 %.1f%%",
                poolSize(), hitCount.get(), creationCount.get(), hitRate());
    }
}
