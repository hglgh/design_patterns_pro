package com.patterns.structural.flyweight;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 享元工厂（FlyweightFactory）：管理享元池，保证相同内部状态的对象只创建一次。
 *
 * <p><b>为什么必须有工厂，客户端不能自己 new？</b>
 * 如果客户端写 {@code new CharacterGlyph('a', "Arial")}，那每次都是新对象，
 * 池子根本建立不起来。<b>享元的"共享"不是自动发生的，必须由一个统一的入口来保证。</b>
 * 工厂就是那个唯一入口：它先查池，命中就复用，未命中才创建并放入池。
 *
 * <p><b>相比原项目改了什么？</b>
 * <ul>
 *   <li>把 {@code if (!pool.containsKey(key)) pool.put(...); return pool.get(key);}
 *       三步换成一次 {@code computeIfAbsent}。原写法对同一个 key 做了
 *       <b>三次哈希计算 + 三次桶查找</b>，{@code computeIfAbsent} 只做一次，
 *       而且是原子语义友好的写法（将来换成 ConcurrentHashMap 时不需要额外加锁）。</li>
 *   <li>新增 {@code creationCount} 与统计方法。原实现无法从外部看出"到底共享了多少"，
 *       学习者只能靠读代码推断。加上计数后，运行结果会直接打印
 *       "请求 1000 次，实际只创建 26 个对象"，享元的价值一眼可见。</li>
 *   <li>池改为不可变视图对外暴露，防止外部往里塞非享元对象破坏共享语义。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public class GlyphFactory {

    /**
     * 享元池。key 由全部内部状态拼成——<b>这是享元工厂最容易出错的地方</b>：
     * key 必须完整覆盖所有内部状态字段。
     * 若只用 {@code symbol} 作 key 而忽略 {@code font}，
     * 那么 {@code ('a', "Arial")} 和 {@code ('a', "宋体")} 会返回同一个对象，
     * 后者拿到的是 Arial 字体——共享变成了数据错误。
     */
    private final Map<String, Glyph> pool = new HashMap<>();

    /** 累计创建过的享元数量，用于量化共享效果。 */
    private int creationCount = 0;

    /** 累计请求次数（含命中缓存的），用于量化共享效果。 */
    private int requestCount = 0;

    /**
     * 获取指定内部状态的字形享元。命中池则复用，否则新建并放入池。
     *
     * @param symbol 字符（内部状态）
     * @param font   字体（内部状态）
     * @return 享元对象。<b>相同参数多次调用返回的是同一个对象引用</b>
     */
    public Glyph getGlyph(char symbol, String font) {
        requestCount++;
        String key = symbol + ":" + font;
        return pool.computeIfAbsent(key, k -> {
            creationCount++;
            System.out.println("    [Factory] 池未命中，新建享元：" + k);
            return new CharacterGlyph(symbol, font);
        });
    }

    /**
     * @return 当前池中享元的数量（= 被创建过的享元数量）
     */
    public int getPoolSize() {
        return pool.size();
    }

    /**
     * @return 累计创建次数
     */
    public int getCreationCount() {
        return creationCount;
    }

    /**
     * @return 累计请求次数
     */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * @return 节省的对象数量（请求次数 - 实际创建次数）
     */
    public int getSavedCount() {
        return requestCount - creationCount;
    }

    /**
     * @return 池中所有 key 的只读视图，便于观察共享了哪些字形
     */
    public Set<String> getPooledKeys() {
        return Collections.unmodifiableSet(pool.keySet());
    }
}
