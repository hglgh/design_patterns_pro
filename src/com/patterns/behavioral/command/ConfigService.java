package com.patterns.behavioral.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接收者：配置中心（用 Map 模拟）。
 *
 * <p><b>为什么本项目需要一个"改配置"的接收者？</b>
 * 因为它是演示<b>撤销（undo）</b>最干净的载体：
 * <pre>
 *   执行：payment.timeout 从 10s 改成 30s
 *   撤销：把 payment.timeout 改回 10s
 * </pre>
 * 状态的变化是<b>可观测、可对比</b>的，不像"发了一封邮件"那样只能看流水。
 *
 * <p><b>这个类里藏着一个真实项目里经常搞错的地方：</b>
 * {@link #get(String)} 返回 {@code null} 表示<b>这个 key 不存在</b>，
 * 而不是"值等于空串"。所以撤销时必须能区分两种情况：
 * <ul>
 *   <li>原来<b>有</b>这个 key，值是 {@code "10s"} → 撤销 = 改回 {@code "10s"}；</li>
 *   <li>原来<b>没有</b>这个 key → 撤销 = <b>删掉它</b>，而不是设成 {@code ""}。</li>
 * </ul>
 * 如果把两者混为一谈，撤销之后配置里就会多出一个空值的 key，
 * 而"空值"在下游代码里往往意味着"显式配置为空"，行为完全不同。
 * 这和 {@code OrderContext} 里用 {@code -1} 而不是 {@code 0} 表示"未检查"
 * 是同一类思考：<b>要能区分"没有"和"是 0/空"</b>。
 *
 * @author design-patterns-pro
 */
public class ConfigService {

    /** 用 LinkedHashMap：输出顺序 = 插入顺序，保证 Demo 输出可复现。 */
    private final Map<String, String> config = new LinkedHashMap<>();

    /**
     * @param initial 初始配置（会被复制，之后外部修改不影响本服务）
     */
    public ConfigService(Map<String, String> initial) {
        if (initial != null) {
            config.putAll(initial);
        }
    }

    /**
     * 便利构造器：用「键1, 值1, 键2, 值2, ...」的方式初始化。
     *
     * <p><b>为什么需要它，直接传 {@code Map.of(...)} 不行吗？</b>
     * 因为 {@code Map.of()} 的<b>迭代顺序是不确定的</b>（它内部带一个哈希盐，
     * 甚至同一个程序两次运行都可能不同）。一旦快照被打印出来，
     * 文档里的输出就无法复现了。<b>凡是要展示的内容，底层容器必须有序。</b>
     *
     * @param keyValues 键值交替出现，个数必须为偶数
     * @throws IllegalArgumentException 参数个数为奇数
     */
    public ConfigService(String... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("初始化参数必须成对出现（键, 值），实际个数："
                    + (keyValues == null ? "null" : keyValues.length));
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            config.put(keyValues[i], keyValues[i + 1]);
        }
    }

    /**
     * 读取配置。
     *
     * @param key 键
     * @return 配置值；<b>{@code null} 表示该键不存在</b>
     */
    public String get(String key) {
        return config.get(key);
    }

    /**
     * 写入配置。
     *
     * @param key   键
     * @param value 值（不允许 {@code null}——要"删除"请调用 {@link #remove(String)}）
     */
    public void set(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        if (value == null) {
            throw new IllegalArgumentException("配置值不能为 null；如需删除请调用 remove()");
        }
        String old = config.put(key, value);
        if (old == null) {
            System.out.println("        └ 配置服务：" + key + " = " + value + "（新增）");
        } else if (!old.equals(value)) {
            System.out.println("        └ 配置服务：" + key + " " + old + " → " + value);
        }
    }

    /**
     * 删除配置。
     *
     * @param key 键；不存在时静默返回（删除一个不存在的东西，结果与预期一致）
     */
    public void remove(String key) {
        String old = config.remove(key);
        if (old != null) {
            System.out.println("        └ 配置服务：移除 " + key + "（原值 " + old + "）");
        }
    }

    /**
     * 当前配置快照。
     *
     * <p>这里用 {@code new LinkedHashMap<>(config)} 再包一层不可修改视图，
     * 是因为直接 {@code Collections.unmodifiableMap(config)} 只是视图——
     * 调用方拿到之后，本服务再改配置，它看到的内容也会跟着变。
     * 想要"快照"就必须<b>先复制</b>。
     *
     * @return 不可变的配置快照
     */
    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }
}
