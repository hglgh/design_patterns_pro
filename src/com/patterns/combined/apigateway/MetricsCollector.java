package com.patterns.combined.apigateway;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 指标订阅者：把事件流累计成可观测数据。
 *
 * <p><b>为什么它「自己数」而不去问网关要数据？</b>
 * 因为观察者的价值就在于<b>解耦出独立的视角</b>。
 * 网关只知道「这次请求怎么了」，指标系统关心的是「这一分钟的分布」。
 * 如果让网关维护所有统计口径，每加一个图表就要改一次网关。</p>
 *
 * <p><b>为什么用 {@link TreeMap}？</b>
 * 为了让输出顺序稳定。<b>顺序不稳定，输出就不可复现</b>，
 * 而本项目的所有文档都声明「两次运行逐字节一致」。</p>
 *
 * @author design-patterns-pro
 */
public final class MetricsCollector implements GatewayListener {

    private final Map<Integer, Integer> statusCounts = new TreeMap<>();
    private final Map<String, Integer> servedByCounts = new TreeMap<>();
    private int total;
    private int success;
    private int failed;
    private int cacheHits;

    @Override
    public void onEvent(Event event) {
        total++;
        statusCounts.merge(event.status(), 1, Integer::sum);
        servedByCounts.merge(event.servedBy(), 1, Integer::sum);
        if (event.success()) {
            success++;
        } else {
            failed++;
        }
        if (event.cacheHit()) {
            cacheHits++;
        }
    }

    /**
     * @return 事件总数
     */
    public int total() {
        return total;
    }

    /**
     * @return 成功数
     */
    public int success() {
        return success;
    }

    /**
     * @return 失败数
     */
    public int failed() {
        return failed;
    }

    /**
     * @return 缓存命中数
     */
    public int cacheHits() {
        return cacheHits;
    }

    /**
     * 成功率。
     *
     * @return 百分比，总数为 0 时返回 0
     */
    public double successRate() {
        return total == 0 ? 0.0 : success * 100.0 / total;
    }

    /**
     * 状态码分布。
     *
     * @return 状态码 → 次数，按状态码升序
     */
    public Map<Integer, Integer> statusCounts() {
        return Collections.unmodifiableMap(new TreeMap<>(statusCounts));
    }

    /**
     * 「谁产生了响应」的分布。
     *
     * @return servedBy → 次数，按名字升序
     */
    public Map<String, Integer> servedByCounts() {
        return Collections.unmodifiableMap(new TreeMap<>(servedByCounts));
    }
}
