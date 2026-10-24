package com.patterns.combined.apigateway;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警订阅者：只看「坏事」，连续出问题才告警。
 *
 * <p><b>为什么它和 {@link MetricsCollector} 分开，而不是在指标里加个 if？</b>
 * 因为两者的关注点完全不同：指标关心<b>全量分布</b>，告警关心<b>异常连续性</b>。
 * 混在一起的结果是「告警逻辑藏在统计代码里」，改告警阈值要动指标类。
 * 观察者让它们各自独立演化。</p>
 *
 * <p><b>为什么是「连续」而不是「总数」？</b>
 * 因为总量阈值会被流量本身放大：QPS 从 10 涨到 10000，
 * 失败数当然从 1 涨到 1000，但错误率可能一点没变。
 * 连续失败才真正说明「现在出问题了」，这也是多数熔断器数连续失败的原因。</p>
 *
 * <p><b>为什么告警不直接 {@code println}？</b>
 * 因为输出顺序会与客户端打印交错，破坏可读性。
 * 这里把告警<b>收集成列表</b>，由 {@link Client} 在合适的位置统一展示——
 * 「副作用收敛到调用方」也是可测试性的一部分。</p>
 *
 * @author design-patterns-pro
 */
public final class AlertListener implements GatewayListener {

    private final int streakThreshold;
    private final List<String> alerts = new ArrayList<>();
    private int failureStreak;
    private int maxStreak;

    /**
     * @param streakThreshold 连续失败多少次触发一次告警
     */
    public AlertListener(int streakThreshold) {
        if (streakThreshold < 1) {
            throw new IllegalArgumentException("streakThreshold 必须 ≥ 1");
        }
        this.streakThreshold = streakThreshold;
    }

    @Override
    public void onEvent(Event event) {
        if (event.success()) {
            failureStreak = 0;
            return;
        }
        failureStreak++;
        maxStreak = Math.max(maxStreak, failureStreak);
        if (failureStreak >= streakThreshold) {
            alerts.add("[ALERT] 连续 " + failureStreak + " 次失败，最近一次："
                    + event.requestId() + " " + event.path() + " → " + event.status()
                    + "（" + event.servedBy() + "）");
            // 触发后归零，避免同一个故障刷屏——这叫"告警抑制"，线上必须做
            failureStreak = 0;
        }
    }

    /**
     * @return 已产生的告警，按发生顺序
     */
    public List<String> alerts() {
        return List.copyOf(alerts);
    }

    /**
     * @return 历史最长连续失败次数
     */
    public int maxStreak() {
        return maxStreak;
    }

    /**
     * @return 告警阈值
     */
    public int streakThreshold() {
        return streakThreshold;
    }
}
