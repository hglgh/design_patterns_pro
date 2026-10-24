package com.patterns.combined.apigateway;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个上游服务实例（例如 {@code order-1}）。
 *
 * <p><b>为什么是类而不是 {@code record}？</b>
 * 因为它有一个会变的字段：当前在途请求数 {@code inFlight}。
 * {@code record} 的语义是「不可变的数据载体」，把计数器塞进去，
 * 就等于宣称「这个对象不会变」却在背后改它——这是本项目在享元模块里踩过的坑
 * （{@code record} 的字段连反射都改不了，只能靠把可变状态藏在别的对象里，反而更难懂）。
 * <b>有身份、有状态的东西就该是普通类。</b></p>
 *
 * <p><b>为什么 {@code inFlight} 用 {@link AtomicInteger}？</b>
 * 真实网关是多线程的，{@code inFlight++} 不是原子操作。
 * 本项目在享元模块用探针实测过：{@code int} 计数器在 16 线程下 500 轮里有 485 轮漏计，
 * 最狠一轮 3200 次请求只记到 540 次。这里直接一步到位。</p>
 *
 * @author design-patterns-pro
 */
public final class UpstreamInstance {

    private final String name;
    private final String zone;
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * @param name 实例名，全局唯一，例如 {@code order-1}
     * @param zone 可用区，仅用于输出展示，不参与选路
     */
    public UpstreamInstance(String name, String zone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("实例名不能为空");
        }
        this.name = name;
        this.zone = (zone == null || zone.isBlank()) ? "default" : zone;
    }

    /**
     * @return 实例名
     */
    public String name() {
        return name;
    }

    /**
     * @return 可用区
     */
    public String zone() {
        return zone;
    }

    /**
     * 当前在途请求数。
     *
     * @return 在途数
     */
    public int inFlight() {
        return inFlight.get();
    }

    /**
     * 请求进入时登记，供「最少连接」策略读取。
     */
    public void acquire() {
        inFlight.incrementAndGet();
    }

    /**
     * 请求结束时释放。必须放在 {@code finally} 里——漏掉一次，
     * 这个实例的「在途数」就永远偏高，最少连接策略会一直绕开它。
     */
    public void release() {
        inFlight.decrementAndGet();
    }

    /**
     * 按实例名判等：同一个实例被不同对象包装时，仍然代表同一个上游。
     */
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof UpstreamInstance that && name.equals(that.name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
