package com.patterns.combined.apigateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 限流过滤器：<b>固定窗口计数</b>，按调用方 IP 限流。
 *
 * <p><b>为什么排在认证之后？</b>
 * 因为限流要针对<b>能识别的调用方</b>。放在认证之前，就只能按 IP 限，
 * 而一个出口 NAT 后面可能有几千个真实用户——误杀一大片。
 * 认证之后本可以按「主体」限流，本 Demo 仍按 IP 计数，
 * 是为了让「同一台机器连打 4 次」这个场景更直观。</p>
 *
 * <p><b>为什么用固定窗口，而不是令牌桶？</b>
 * 固定窗口实现最简单（一个计数器），代价是<b>窗口边界效应</b>：
 * 每秒 3 次的限额，在 0.9 秒打 3 次、1.1 秒再打 3 次，实际是 0.2 秒 6 次。
 * 令牌桶/滑动窗口能解决这个问题，但需要记录时间戳。
 * 本 Demo 用「逻辑窗口」：由 {@link #rollWindow()} 显式推进到下个窗口，
 * 这样既能演示窗口边界，又不用引入真实时间（否则输出不可复现）。</p>
 *
 * <p><b>为什么限流必须在缓存之前？</b>
 * 这是顺序影响安全的典型案例：如果缓存排在限流前面，
 * 命中缓存的请求<b>根本不会经过限流</b>，攻击者只要反复请求同一个热门路径，
 * 就能无限次打穿网关（而且每次都很快）。<b>「便宜的操作」恰恰不能作为
 * 「安全判断」的挡箭牌。</b></p>
 *
 * @author design-patterns-pro
 */
public final class RateLimitFilter extends GatewayFilter {

    private final int capacity;
    private final Map<String, Integer> used = new LinkedHashMap<>();

    /**
     * @param capacity 每个窗口内每个 IP 允许的请求数
     */
    public RateLimitFilter(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity 必须 ≥ 1");
        }
        this.capacity = capacity;
    }

    @Override
    public String name() {
        return "rate";
    }

    @Override
    public GatewayResponse handle(CallContext context, FilterChain.Cursor next) {
        String ip = context.request().clientIp();
        int count = used.merge(ip, 1, Integer::sum);
        context.trace("rate: " + ip + " 第 " + count + "/" + capacity + " 次");

        if (count > capacity) {
            context.trace("rate: 超出阈值 → 短路 429");
            return GatewayResponse.reject(429, "请求过于频繁（每窗口上限 " + capacity + " 次）");
        }
        return next.proceed();
    }

    /**
     * 进入下一个固定窗口：清空计数。
     *
     * <p>真实实现由定时器或「窗口编号 = 时间戳 / 窗口长度」自动完成，
     * 这里由调用方显式推进，保证「什么时候跨窗口」是确定的。</p>
     */
    public void rollWindow() {
        used.clear();
    }

    /**
     * @return 每个 IP 在当前窗口已消耗的配额，按 IP 排序
     */
    public Map<String, Integer> snapshot() {
        // 用 TreeMap 而不是 Map.copyOf：后者返回的迭代顺序不保证，
        // 而本项目要求「同一份代码两次运行输出逐字节一致」。
        return new TreeMap<>(used);
    }

    /**
     * @return 每窗口容量
     */
    public int capacity() {
        return capacity;
    }
}
