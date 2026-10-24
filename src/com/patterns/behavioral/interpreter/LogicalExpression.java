package com.patterns.behavioral.interpreter;

/**
 * 非终结符表达式 —— 逻辑与 / 逻辑或，<b>短路求值</b>。
 *
 * <p>它是整棵树里唯一"可能不评估右子树"的节点，
 * 也因此是唯一必须自己控制求值顺序的节点：
 * 算术和比较都是"两个子节点都要算"，逻辑运算不是。
 * {@code age > 18 && score < 500} 里若左边已经 false，
 * 右边算不算都不影响结果。</p>
 *
 * <p><b>短路不只是性能优化，它是语义。</b>
 * 规则里常写 {@code count > 0 && total / count > 10}，
 * 左边保证除数不为 0；如果去掉短路、先算右边，
 * 这条规则会在 count 为 0 时抛「除数为 0」而不是返回 false。
 * 换句话说：<em>短路让规则的每一段可以依赖前一段的前提。</em>
 * 这也是为什么本类不能像算术节点那样"先把两个操作数都求出来"。</p>
 *
 * <p>反面提醒：短路也意味着右侧的"未定义变量"可能永远不被检查。
 * {@code false && AGE > 18} 不会报错。这是短路必然的代价，
 * 和 Java 的 {@code &&} 行为一致 —— 一致性比"更严格"更重要，
 * 否则用户会因为位置不同而看到不同的报错。</p>
 */
public record LogicalExpression(Operator operator, Expression left, Expression right)
        implements Expression {

    /**
     * 校验字段。
     *
     * @throws IllegalArgumentException 任一字段为 null
     */
    public LogicalExpression {
        if (operator == null) {
            throw new IllegalArgumentException("operator 不能为 null");
        }
        if (left == null) {
            throw new IllegalArgumentException("left 不能为 null");
        }
        if (right == null) {
            throw new IllegalArgumentException("right 不能为 null");
        }
    }

    @Override
    public Value evaluate(Context context) {
        String role = "运算符 '" + operator.symbol() + "'";
        boolean leftValue = left.evaluate(context).asBoolean(role + " 的左操作数");
        if (operator == Operator.AND && !leftValue) {
            return Value.of(false);
        }
        if (operator == Operator.OR && leftValue) {
            return Value.of(true);
        }
        boolean rightValue = right.evaluate(context).asBoolean(role + " 的右操作数");
        return Value.of(rightValue);
    }

    @Override
    public String describe() {
        return "(" + left.describe() + " " + operator.symbol() + " " + right.describe() + ")";
    }

    /**
     * 两种逻辑运算符。
     */
    public enum Operator {

        /** 逻辑与，左侧为 false 时跳过右侧。 */
        AND("&&"),
        /** 逻辑或，左侧为 true 时跳过右侧。 */
        OR("||");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * 运算符的源码文本。
         *
         * @return 例如 {@code "&&"}
         */
        public String symbol() {
            return symbol;
        }
    }
}
