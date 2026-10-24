package com.patterns.combined.apigateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟的上游服务：装饰器链的<b>最内层</b>，也是唯一「真的干活」的一层。
 *
 * <p><b>为什么要在 Demo 里注入故障？</b>
 * 因为重试、熔断、降级这些模式，<b>只有在上游出问题时才会被触发</b>。
 * 一个永远返回 200 的上游，会让整条保护链变成装饰品——
 * 读者看不到它们起作用的瞬间，也就学不到它们到底在解决什么。</p>
 *
 * <p>故障是<b>脚本化</b>的（按实例名预先写好「前 N 次失败」），不是随机的。
 * 随机数会让输出无法复现，而本项目的所有输出都要求两次运行逐字节一致。</p>
 *
 * @author design-patterns-pro
 */
public final class ServiceHandler implements UpstreamHandler {

    /** 实例名 → 还需要失败几次。 */
    private final Map<String, Integer> remainingFailures = new LinkedHashMap<>();

    /** 实例名 → 已被调用几次，仅用于生成可复现的响应体。 */
    private final Map<String, Integer> callSeq = new LinkedHashMap<>();

    /**
     * 无故障注入，所有实例都返回 200。
     */
    public ServiceHandler() {
        this(Map.of());
    }

    /**
     * @param scriptedFailures 实例名 → 需要注入的失败次数
     */
    public ServiceHandler(Map<String, Integer> scriptedFailures) {
        scriptedFailures.forEach((name, times) -> remainingFailures.put(name, times));
    }

    /**
     * 处理一次调用：登记在途数 → 决定成功或失败 → 释放在途数。
     *
     * <p>{@code try/finally} 是硬要求：{@link UpstreamInstance#acquire()}
     * 与 {@link UpstreamInstance#release()} 必须成对，否则「最少连接」策略会永远绕开出过错的实例。</p>
     */
    @Override
    public GatewayResponse handle(CallContext context) {
        UpstreamInstance instance = context.instance();
        if (instance == null) {
            throw new IllegalStateException("调用上游前必须先选定实例");
        }
        context.markAttempt();
        instance.acquire();
        try {
            int seq = callSeq.merge(instance.name(), 1, Integer::sum);
            int remaining = remainingFailures.getOrDefault(instance.name(), 0);
            if (remaining > 0) {
                remainingFailures.put(instance.name(), remaining - 1);
                context.trace("upstream: " + instance.name() + " 第 " + seq + " 次调用 → 503");
                return GatewayResponse.upstreamError(instance.name(),
                        "上游过载（第 " + seq + " 次调用，还会失败 " + (remaining - 1) + " 次）");
            }
            context.trace("upstream: " + instance.name() + " 第 " + seq + " 次调用 → 200");
            return GatewayResponse.ok("data#" + seq, instance.name());
        } finally {
            instance.release();
        }
    }

    /**
     * 某实例被真实调用了多少次（含失败）。
     *
     * @param instanceName 实例名
     * @return 调用次数
     */
    public int callCount(String instanceName) {
        return callSeq.getOrDefault(instanceName, 0);
    }

    @Override
    public String describe() {
        return "Service";
    }
}
