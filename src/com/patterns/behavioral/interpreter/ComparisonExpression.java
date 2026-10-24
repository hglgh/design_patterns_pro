package com.patterns.behavioral.interpreter;

/**
 * 非终结符表达式 —— 比较运算，结果一定是布尔值。
 *
 * <p>它是"规则"和"算术"接缝处的那块砖：
 * 左边的算术子树算出数字，本节点比较出 true/false，
 * 上面的逻辑节点再把布尔们组合成最终规则。
 * {@code (age + 5) * 2 > 50 && age < 30} 这种混合表达式
 * 能成立，靠的就是每一层都统一用 {@link Value} 传递结果。</p>
 *
 * <p><b>为什么六个运算符放在一个类的枚举里，而不是
 * 六个类？</b>
 * 因为它们的结构完全相同（两个数字进、一个布尔出），
 * 差别只有一行比较逻辑。拆成六个类会得到六份
 * 一模一样的字段声明与 {@code evaluate} 骨架。
 * 判断标准很简单：<em>结构不同才拆类，行为不同用枚举。</em>
 * 算术、比较、逻辑、一元四类节点结构各不相同，所以拆成四类；
 * 每类内部的运算符只换行为，所以用枚举。</p>
 *
 * <p><b>为什么不支持字符串比较？</b>
 * 因为 {@link Value} 只允许两种类型。要加字符串，
 * 得先给 Value 加一个 {@code Str} 分支、给词法层加字符串字面量、
 * 给{@code <}定义"字典序"语义 —— 那是另一个需求，
 * 不该顺手塞进这个示例里。这正是"不为套用模式而过度设计"。</p>
 */
public record ComparisonExpression(Operator operator, Expression left, Expression right)
        implements Expression {

    /**
     * 校验字段。
     *
     * @throws IllegalArgumentException 任一字段为 null
     */
    public ComparisonExpression {
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
        long leftValue = left.evaluate(context).asNumber(role + " 的左操作数");
        long rightValue = right.evaluate(context).asNumber(role + " 的右操作数");
        return Value.of(operator.test(leftValue, rightValue));
    }

    @Override
    public String describe() {
        return "(" + left.describe() + " " + operator.symbol() + " " + right.describe() + ")";
    }

    /**
     * 六种比较运算符。
     */
    public enum Operator {

        /** 大于。 */
        GREATER(">"),
        /** 大于等于。 */
        GREATER_EQUAL(">="),
        /** 小于。 */
        LESS("<"),
        /** 小于等于。 */
        LESS_EQUAL("<="),
        /** 等于。 */
        EQUAL("=="),
        /** 不等于。 */
        NOT_EQUAL("!=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * 运算符的源码文本。
         *
         * @return 例如 {@code ">="}
         */
        public String symbol() {
            return symbol;
        }

        /**
         * 比较两个整数。
         *
         * @param leftValue  左操作数
         * @param rightValue 右操作数
         * @return 比较结果
         */
        boolean test(long leftValue, long rightValue) {
            return switch (this) {
                case GREATER -> leftValue > rightValue;
                case GREATER_EQUAL -> leftValue >= rightValue;
                case LESS -> leftValue < rightValue;
                case LESS_EQUAL -> leftValue <= rightValue;
                case EQUAL -> leftValue == rightValue;
                case NOT_EQUAL -> leftValue != rightValue;
            };
        }
    }
}
