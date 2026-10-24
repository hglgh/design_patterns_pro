package com.patterns.behavioral.interpreter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 解释上下文：变量名到整数值的映射。
 *
 * <p><b>它替代了原项目的 {@code record Context(int age, int score)}。</b>
 * 那个 record 把"上下文"写死成了两个业务字段，
 * 带来三个后果：</p>
 * <ol>
 *   <li>加一个变量要同时改 {@code Context}、{@code GreaterThanExpression}、
 *       {@code LessThanExpression} 三个类；</li>
 *   <li>字段名只认 {@code age} 和 {@code score}，
 *       写 {@code weight} 时静默返回 {@code false}（探针 I5）；</li>
 *   <li>大小写敏感（{@code AGE} ≠ {@code age}），而且没有提示（探针 I6）。</li>
 * </ol>
 *
 * <p>用 {@code Map<String, Long>} 之后，这三个问题一起消失：
 * 加变量只是多一次 {@link #with}，变量名拼错会抛"未定义的变量"
 * 并列出已定义的变量名，大小写不一致同样会被当成未定义 ——
 * 错误从"静默的 false"变成"明确的异常"。</p>
 *
 * <p><b>为什么是不可变的？</b>
 * {@link #with} 返回新实例而不是改自己。这样可以放心地把同一个
 * 上下文传给多个表达式、多个线程，而不用担心某个表达式
 * 在中途改了变量导致另一个表达式算错。
 * 代价是每加一个变量复制一次 Map —— 变量通常只有几个，
 * 复制成本可以忽略。</p>
 *
 * <p><b>为什么要用 LinkedHashMap 而不是 HashMap？</b>
 * 因为 {@link #describe()} 和未定义变量的错误消息都会列出
 * 已定义的变量名，需要按插入顺序输出才能让 Demo 的输出
 * 逐字节可复现。{@code HashMap} 的迭代顺序虽然在同一 JDK 版本内
 * 稳定，但不应该依赖它。</p>
 */
public final class Context {

    private final Map<String, Long> variables;

    private Context(Map<String, Long> variables) {
        this.variables = variables;
    }

    /**
     * 空上下文。
     *
     * @return 不含任何变量的上下文
     */
    public static Context empty() {
        return new Context(Collections.emptyMap());
    }

    /**
     * 派生出多一个变量的新上下文。
     *
     * @param name  变量名，不能为 null 或空白
     * @param value 变量值
     * @return 新的上下文，{@code this} 不受影响
     * @throws IllegalArgumentException 变量名非法
     */
    public Context with(String name, long value) {
        if (name == null) {
            throw new IllegalArgumentException("变量名不能为 null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("变量名不能为空白字符串");
        }
        Map<String, Long> copy = new LinkedHashMap<>(variables);
        copy.put(name, value);
        return new Context(Collections.unmodifiableMap(copy));
    }

    /**
     * 是否定义了某个变量。
     *
     * @param name 变量名
     * @return 存在时返回 true
     */
    public boolean contains(String name) {
        return variables.containsKey(name);
    }

    /**
     * 取变量值；未定义时抛出带位置的异常。
     *
     * <p>原项目的做法是：字段名不认识就 {@code return false}。
     * 对规则引擎来说这是最坏的失败方式 —— 规则悄悄失效，
     * 数据看起来"只是没命中"，没人会去查是不是拼错了。</p>
     *
     * @param name     变量名
     * @param position 变量在源码中的起始下标
     * @return 变量值
     * @throws ExpressionException 变量未定义
     */
    public long require(String name, int position) {
        Long value = variables.get(name);
        if (value == null) {
            throw new ExpressionException(
                    "未定义的变量 '" + name + "'（已定义: " + namesText() + "）", null, position);
        }
        return value;
    }

    /**
     * 全部变量名，按插入顺序。
     *
     * @return 不可变集合
     */
    public Set<String> names() {
        return variables.keySet();
    }

    /**
     * 变量个数。
     *
     * @return 个数
     */
    public int size() {
        return variables.size();
    }

    /**
     * 单行描述，例如 {@code "age=25, score=1000"}。
     *
     * @return 按插入顺序拼接的键值对
     */
    public String describe() {
        StringBuilder text = new StringBuilder(64);
        for (Map.Entry<String, Long> entry : variables.entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
    }

    private String namesText() {
        if (variables.isEmpty()) {
            return "（无）";
        }
        StringBuilder text = new StringBuilder(64);
        for (String name : variables.keySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(name);
        }
        return text.toString();
    }

    /**
     * 与 {@link #describe()} 一致，便于调试时直接打印。
     *
     * @return 键值对描述
     */
    @Override
    public String toString() {
        return describe();
    }
}
