package com.patterns.combined.apigateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 负载均衡策略：<b>这是策略模式（Strategy）</b>。
 *
 * <p>网关里「选哪台机器」是一个会不断变化的需求：今天轮询，明天按连接数，
 * 后天要按机房就近、按权重、按一致性哈希。变的是算法，不变的是
 * 「给一批实例，挑一个」这个契约。所以把它抽成接口，
 * 让 <b>路由器只依赖接口，不依赖任何具体算法</b>。</p>
 *
 * <p><b>为什么两个实现写在这个接口里，而不是各占一个文件？</b>
 * 因为它们都只有十几行，且离开这个接口就没有独立意义。
 * Java 标准库里 {@code Comparator.naturalOrder()} / {@code Comparator.reverseOrder()}
 * 就是这么放的——<b>策略家族小的时候，接口内嵌实现比散落成文件更好读</b>。
 * 一旦某个实现长到需要自己的状态和测试，再拆出去。</p>
 *
 * @author design-patterns-pro
 */
public interface LoadBalancer {

    /**
     * @return 策略名，会出现在处理轨迹里
     */
    String name();

    /**
     * 从候选实例中挑一个。
     *
     * @param instances 候选实例，调用方保证非空
     * @return 选中的实例
     */
    UpstreamInstance pick(List<UpstreamInstance> instances);

    /**
     * 排除指定实例后再挑一个，用于重试换实例。
     *
     * <p>放在接口里做默认实现，而不是让每个策略各自实现一遍：
     * 「排除一个再挑」对任何策略都成立，属于<b>契约的一部分</b>，
     * 不属于某个算法的细节。</p>
     *
     * @param instances 候选实例
     * @param exclude   要排除的实例，可为 {@code null}
     * @return 选中的实例；没有其他候选时返回原实例
     */
    default UpstreamInstance pickExcluding(List<UpstreamInstance> instances, UpstreamInstance exclude) {
        if (exclude == null) {
            return pick(instances);
        }
        List<UpstreamInstance> candidates = instances.stream()
                .filter(instance -> !instance.equals(exclude))
                .toList();
        return candidates.isEmpty() ? pick(instances) : pick(candidates);
    }

    /**
     * 轮询：按顺序一台一台发。
     *
     * <p>用 {@link AtomicInteger} 而不是 {@code int}：真实网关并发处理请求，
     * {@code cursor++} 在多线程下会重复发到同一台机器上。</p>
     *
     * @return 轮询策略
     */
    static LoadBalancer roundRobin() {
        return new RoundRobin();
    }

    /**
     * 最少连接：挑当前在途请求最少的实例。
     *
     * @return 最少连接策略
     */
    static LoadBalancer leastConnections() {
        return new LeastConnections();
    }

    /** 轮询实现。 */
    final class RoundRobin implements LoadBalancer {

        private final AtomicInteger cursor = new AtomicInteger();

        RoundRobin() {
        }

        @Override
        public String name() {
            return "round-robin";
        }

        @Override
        public UpstreamInstance pick(List<UpstreamInstance> instances) {
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("没有可用实例");
            }
            int index = Math.floorMod(cursor.getAndIncrement(), instances.size());
            return instances.get(index);
        }
    }

    /** 最少连接实现。 */
    final class LeastConnections implements LoadBalancer {

        LeastConnections() {
        }

        @Override
        public String name() {
            return "least-connections";
        }

        @Override
        public UpstreamInstance pick(List<UpstreamInstance> instances) {
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("没有可用实例");
            }
            UpstreamInstance best = null;
            for (UpstreamInstance instance : instances) {
                if (best == null || instance.inFlight() < best.inFlight()) {
                    best = instance;
                }
            }
            // 全部在途数相同（例如都是 0）时会一直返回第一个 —— 这个"退化"在 Client 里有实测
            return best;
        }
    }
}
