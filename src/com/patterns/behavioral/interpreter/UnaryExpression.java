package com.patterns.behavioral.interpreter;

/**
 * 非终结符表达式 —— 一元运算（取负、逻辑非）。
 *
 * <p>它只有一个子节点，看起来"没必要单独成类"。
 * 但如果把 {@code -5} 当成数字的一部分（让词法层直接产出负数），
 * {@code 3 - 5} 和 {@code 3 -5} 在词法阶段就无法区分了：
 * 前者是减法，后者也是减法，可 {@code 3 * -5} 里的负号
 * 又不是减法。唯一的解法是让语法层在"期望一个操作数"的位置
 * 接受一个可选的 {@code -}，这正是本类存在的位置。</p>
 *
 * <p><b>这就是为什么 {@code Lexer} 不吞前导负号。</b>
 * 它只负责切出 {@code MINUS} 这个记号，
 * 由 {@link Parser} 在 {@code unary} 层决定它到底是
 * "一元负号"还是"二元减号"。同样一个字符在两种语法位置
 * 有不同含义，这个判断只能由语法层做。</p>
 *
 * <p><b>为什么 NOT 也放在这里？</b>
 * 因为它同样满足"一个操作数 + 一个前置符号"的形状。
 * 合并成一个节点后，{@code describe} 和 {@code Parser}
 * 都只需要处理一种结构。代价是 {@code evaluate} 里
 * 必须区分"要数字"还是"要布尔" —— 这个区分的真正价值
 * 是类型检查：{@code !5} 会报错，而不是算出 {@code false}。</p>
 */
public record UnaryExpression(Operator operator, Expression operand) implements Expression {

    /**
     * 校验字段。
     *
     * @throws IllegalArgumentException 任一字段为 null
     */
    public UnaryExpression {
        if (operator == null) {
            throw new IllegalArgumentException("operator 不能为 null");
        }
        if (operand == null) {
            throw new IllegalArgumentException("operand 不能为 null");
        }
    }

    @Override
    public Value evaluate(Context context) {
        String role = "运算符 '" + operator.symbol() + "'";
        Value value = operand.evaluate(context);
        return switch (operator) {
            case NEGATE -> Value.of(negate(value.asNumber(role + " 的操作数"), role));
            case NOT -> Value.of(!value.asBoolean(role + " 的操作数"));
        };
    }

    private static long negate(long number, String role) {
        try {
            return Math.negateExact(number);
        } catch (ArithmeticException e) {
            throw new ExpressionException("整数溢出: " + role + " 操作数 " + number + "（取负后超出 long 范围）", null, 0);
        }
    }

    @Override
    public String describe() {
        return "(" + operator.symbol() + operand.describe() + ")";
    }

    /**
     * 两种一元运算符。
     */
    public enum Operator {

        /** 取负。 */
        NEGATE("-"),
        /** 逻辑非。 */
        NOT("!");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * 运算符的源码文本。
         *
         * @return 例如 {@code "-"}
         */
        public String symbol() {
            return symbol;
        }
    }
}
