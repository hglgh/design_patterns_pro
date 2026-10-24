package com.patterns.structural.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存代理（Cache Proxy）：控制"<b>是否真的需要访问</b>真实对象"。
 *
 * <p>真实对象的操作很昂贵（渲染一张 4K 图片要几十毫秒），
 * 而同一个图片可能被反复显示（页面滚动、来回切换标签页）。
 * 缓存代理把首次渲染的结果（位图快照）存下来，后续请求直接返回快照，
 * 真实对象完全不参与。
 *
 * <p><b>为什么不把缓存写进 RealImage 自己？</b>
 * <ul>
 *   <li><b>缓存策略会变</b>：今天是"永久缓存"，明天要改成 LRU + 5 分钟过期，
 *       后天要改成 Redis 分布式缓存。写在 RealImage 里意味着每次改策略都要动核心渲染类。</li>
 *   <li><b>不是所有场景都要缓存</b>：批量导出工具需要每次都拿最新渲染结果，
 *       缓存反而是 bug。用代理就能"要缓存的地方套一层，不要的地方直接用 RealImage"。</li>
 *   <li><b>关注点分离</b>：RealImage 只管"怎么渲染"，缓存代理只管"什么时候不用渲染"。</li>
 * </ul>
 *
 * <p><b>典型应用</b>：Spring 的 {@code @Cacheable}（AOP 生成的就是缓存代理）、
 * MyBatis 的一级/二级缓存、Hibernate 的二级缓存、CDN 边缘节点、浏览器 HTTP 缓存。
 *
 * <p><b>与享元模式的区别</b>（容易混）：
 * 缓存代理缓存的是<b>昂贵操作的结果</b>，通常有淘汰策略（LRU/TTL）、可能失效；
 * 享元共享的是<b>不可变对象的内部状态</b>，常驻不淘汰、永不失效。
 * 见 flyweight 包的 README 第 8 节对比表。
 *
 * @author design-patterns-pro
 */
public class CachingImageProxy implements Image {

    /** 缓存：文件名 → 渲染好的位图快照。 */
    private final Map<String, String> cache = new HashMap<>();

    private final Image target;

    private int hitCount = 0;
    private int missCount = 0;

    /**
     * @param target 被代理的真实对象（通常包装 VirtualImageProxy，形成代理链）
     */
    public CachingImageProxy(Image target) {
        this.target = target;
    }

    @Override
    public void display() {
        String key = target.getFilename();
        String snapshot = cache.get(key);

        if (snapshot != null) {
            hitCount++;
            System.out.println("      [CacheProxy] ◆ 缓存命中，跳过真实渲染（第 "
                    + hitCount + " 次命中）");
            System.out.println("      [CacheProxy]   使用快照：" + snapshot);
            return;
        }

        missCount++;
        System.out.println("      [CacheProxy] ◇ 缓存未命中，转发给真实对象");
        target.display();                       // 真正干活
        cache.put(key, "bitmap-snapshot-of-" + key + "@" + System.nanoTime());
    }

    @Override
    public String getFilename() {
        return target.getFilename();
    }

    /**
     * 清空缓存。真实场景中"图片文件被修改"时必须调用，否则会一直显示旧图。
     *
     * <p><b>这是缓存代理最容易被忽略的责任</b>：
     * 缓存一致性问题（数据变了但缓存没变）是生产环境最常见的 bug 来源之一。
     */
    public void invalidate() {
        System.out.println("      [CacheProxy] 缓存已清空（原 " + cache.size() + " 条）");
        cache.clear();
        hitCount = 0;
        missCount = 0;
    }

    public int getHitCount() {
        return hitCount;
    }

    public int getMissCount() {
        return missCount;
    }
}
