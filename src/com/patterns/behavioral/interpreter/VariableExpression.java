package com.patterns.behavioral.interpreter;

/**
 * 终结符表达式（TerminalExpression）—— 变量。
 *
 * <p>它在求值时去 {@link Context} 里取对应名字的值。
 * 这是整棵树唯一"和外部数据发生关系"的节点，
 * 也是解释器模式和"硬编码规则"的分界线：
 * 规则文本可以写死，但 {@code age} 到底是 25 还是 10
 * 由上下文决定，换一份上下文就能复用同一棵树。</p>
 *
 * <p><b>为什么要带 position？</b>
 * 因为最常见的错误是变量名拼错。原项目的做法是
 * 认不出名字就 {@code return false}（探针 I5/I6），
 * 规则于是悄悄失效。这里记下变量在原文里的下标，
 * 报错时就能画出 {@code ^} 指到具体位置：</p>
 *
 * <pre>{@code
 * 未定义的变量 'AGE'（已定义: age, score）
 *   规则: AGE > 18
 *         ^
 * }</pre>
 *
 * <p>position 只在解析阶段有意义，求值时不参与任何计算，
 * 所以它对 {@code equals} 是有影响的（record 会把所有字段算进去）。
 * 这是有意为之：两个来自不同位置的同名变量，
 * 报错时应该指向各自的位置，不该被当成"相同对象"。</p>
 */
public record VariableExpression(String name, int position) implements Expression {

    /**
     * 校验字段。
     *
     * @throws IllegalArgumentException 变量名为 null / 空白，或位置为负
     */
    public VariableExpression {
        if (name == null) {
            throw new IllegalArgumentException("变量名不能为 null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("变量名不能为空白字符串");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position 不能为负，实际 " + position);
        }
    }

    @Override
    public Value evaluate(Context context) {
        return Value.of(Expression.requireContext(context).require(name, position));
    }

    @Override
    public String describe() {
        return name;
    }
}
