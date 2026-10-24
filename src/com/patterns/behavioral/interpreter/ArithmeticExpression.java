package com.patterns.behavioral.interpreter;

/**
 * 非终结符表达式（NonterminalExpression）—— 二元算术运算。
 *
 * <p>它是"组合"两个字的实体：字段里放着两个 {@link Expression}，
 * 求值时先递归求左右子树，再把自己的运算作用上去。
 * 加一种新运算（比如取余）只需要在 {@link Operator} 里加一行，
 * 不需要改任何调用方 —— 这就是把"运算符"做成对象的收益。</p>
 *
 * <p><b>为什么用 long 而不是 double？</b>
 * 原项目两套实现分别用了 int 和 double：规则引擎用 int，
 * AST 计算器用 {@code Node} 里不明说的整数。
 * 这里统一用 long，并且全部走 {@code Math.*Exact} ——
 * 溢出时立刻抛异常，而不是安静地变成负数。
 * 理由是：解释器的输出会被当成业务结果（年龄、次数、库存），
 * 一个悄无声息的负库存比一个崩溃更难查。</p>
 *
 * <p><b>除零为什么单独处理？</b>
 * {@code 10 / 0} 在 Java 里对整数会抛 {@link ArithmeticException}，
 * 但那条消息是 {@code / by zero}，既不告诉你哪条规则出错，
 * 也没有中文说明。这里提前判断，给出
 * 「除数为 0」+ 完整表达式文本。</p>
 *
 * <p><b>为什么求值时不直接用 describe() 作为错误上下文？</b>
 * 因为 {@code describe()} 要拼出整棵子树的文本。若每个节点
 * 在成功路径上都调一次，总代价是 O(n²)（深链上实测：
 * 10000 项要多花十几秒，50000 项直接卡死）。
 * 所以这里只拼一个「运算符 '+'」这样的短前缀，
 * 完整表达式只在真正抛异常（除零）时才拼一次。</p>
 *
 * <p><b>为什么 describe 一定要加括号？</b>
 * 因为它要证明"树的形状"和你想的一样。
 * {@code "10 + 5 * 2"} 必须描述成 {@code "(10 + (5 * 2))"}
 * 才说明乘法被优先结合；如果输出 {@code "((10 + 5) * 2)"}，
 * 即使某些输入下结果碰巧相同，也是解析错了。
 * 这是"结果对 ≠ 实现对"的检查手段。</p>
 */
public record ArithmeticExpression(Operator operator, Expression left, Expression right)
        implements Expression {

    /**
     * 校验字段。
     *
     * @throws IllegalArgumentException 任一字段为 null
     */
    public ArithmeticExpression {
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
        if (operator == Operator.DIVIDE && rightValue == 0) {
            throw new ExpressionException("除数为 0: " + describe(), null, 0);
        }
        try {
            return Value.of(operator.compute(leftValue, rightValue));
        } catch (ArithmeticException e) {
            throw new ExpressionException(
                    "整数溢出: " + leftValue + " " + operator.symbol() + " " + rightValue, null, 0);
        }
    }

    @Override
    public String describe() {
        return "(" + left.describe() + " " + operator.symbol() + " " + right.describe() + ")";
    }

    /**
     * 四种二元算术运算符。
     *
     * <p>把符号和计算逻辑放在同一个枚举里，
     * 是为了让"多一个运算符"只改一处。
     * 如果让 {@code Parser} 自己 switch 一遍、
     * {@code ArithmeticExpression} 再 switch 一遍，
     * 两处 switch 就会慢慢不同步（原项目 BOGOF 公式
     * 复制两份正是同类问题，探针 V5）。</p>
     */
    public enum Operator {

        /** 加。 */
        ADD("+"),
        /** 减。 */
        SUBTRACT("-"),
        /** 乘。 */
        MULTIPLY("*"),
        /** 除，整除，除数为 0 时由调用方提前拦截。 */
        DIVIDE("/");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * 运算符的源码文本。
         *
         * @return 例如 {@code "+"}
         */
        public String symbol() {
            return symbol;
        }

        /**
         * 计算两个操作数，溢出时抛出 {@link ArithmeticException}。
         *
         * @param leftValue  左操作数
         * @param rightValue 右操作数
         * @return 结果
         */
        long compute(long leftValue, long rightValue) {
            return switch (this) {
                case ADD -> Math.addExact(leftValue, rightValue);
                case SUBTRACT -> Math.subtractExact(leftValue, rightValue);
                case MULTIPLY -> Math.multiplyExact(leftValue, rightValue);
                case DIVIDE -> leftValue / rightValue;
            };
        }
    }
}
