package com.patterns.combined.apigateway;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 缓存过滤器：GET 命中就短路，未命中就放行并回填。
 *
 * <p><b>缓存 key 为什么必须包含「主体」？</b></p>
 * <p>这是本篇最想让人记住的一行代码：</p>
 * <pre>
 *   // 错误：只按路径缓存
 *   String key = request.path();
 *   // 结果：用户 A 的订单列表被返回给用户 B —— 一个缓存引发的越权漏洞
 *
 *   // 正确：路径 + 主体
 *   String key = principal + " | " + request.path();
 * </pre>
 * <p>很多真实事故就是这么来的：缓存层是「公共的」，而响应内容是「私人的」。
 * 判断标准很简单——<b>如果这个响应对不同调用方不同，key 里就必须有区分调用方的东西</b>。</p>
 *
 * <p><b>为什么只缓存 GET？</b>
 * HTTP 语义里 GET 应当是<b>幂等且无副作用</b>的。POST/PUT/DELETE 一旦被缓存，
 * 就等于「第二次下单没有真的下单」，属于严重错误。
 * 这条规则不是风格问题，是协议语义问题。</p>
 *
 * <p><b>为什么不缓存失败响应？</b>
 * 一次 503 可能只是瞬时抖动；把它缓存住，等于把 1 秒的抖动放大成整个 TTL 的故障。
 * <b>缓存只应该固化「好结果」。</b></p>
 *
 * @author design-patterns-pro
 */
public final class CacheFilter extends GatewayFilter {

    private final Map<String, GatewayResponse> cache = new LinkedHashMap<>();
    private int hits;
    private int misses;
    private int bypassed;

    @Override
    public String name() {
        return "cache";
    }

    @Override
    public GatewayResponse handle(CallContext context, FilterChain.Cursor next) {
        if (!context.request().isGet()) {
            bypassed++;
            context.trace("cache: " + context.request().method() + " 不缓存");
            return next.proceed();
        }

        String cacheKey = context.principal() + " | " + context.request().path();
        GatewayResponse cached = cache.get(cacheKey);
        if (cached != null) {
            hits++;
            context.cacheHit(true);
            context.trace("cache: 命中 [" + cacheKey + "]");
            return cached;
        }

        misses++;
        context.trace("cache: 未命中 [" + cacheKey + "] → 放行");
        GatewayResponse response = next.proceed();
        if (response.success()) {
            cache.put(cacheKey, response);
            context.trace("cache: 回填 [" + cacheKey + "]");
        } else {
            context.trace("cache: " + response.status() + " 不缓存");
        }
        return response;
    }

    /**
     * @return 命中次数
     */
    public int hits() {
        return hits;
    }

    /**
     * @return 未命中次数
     */
    public int misses() {
        return misses;
    }

    /**
     * @return 因方法不可缓存而绕过的次数
     */
    public int bypassed() {
        return bypassed;
    }

    /**
     * @return 缓存条目数
     */
    public int size() {
        return cache.size();
    }

    /**
     * 清空缓存。仅用于 Demo 分段，真实网关靠 TTL 淘汰。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 一行统计，用于输出。
     *
     * @return 形如 {@code 缓存 3 条，命中 1 / 未命中 2 / 绕过 1} 的字符串
     */
    public String describeStats() {
        int lookups = hits + misses;
        double rate = lookups == 0 ? 0.0 : hits * 100.0 / lookups;
        return String.format(Locale.ROOT,
                "缓存 %d 条，命中 %d / 未命中 %d / 绕过 %d，命中率 %.1f%%",
                cache.size(), hits, misses, bypassed, rate);
    }
}
