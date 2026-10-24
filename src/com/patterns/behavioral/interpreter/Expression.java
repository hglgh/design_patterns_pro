package com.patterns.behavioral.interpreter;

/**
 * 抽象表达式（AbstractExpression）—— 解释器模式的核心。
 *
 * <p><b>它只有一个求值方法，但这一条方法撑起了整棵树。</b>
 * 每个节点都实现 {@link #evaluate}，而实现里又去调用子节点的
 * {@code evaluate} —— 这就是解释器模式的"递归组合"：
 * 树的形状决定了求值顺序，调用方完全不需要 if/else 判断节点类型。</p>
 *
 * <p><b>为什么 evaluate 返回 {@link Value} 而不是 boolean？</b>
 * 原项目的 {@code Expression.interpret(Context)} 只返回 boolean，
 * 于是它只能表达"规则"，不能表达"算术"。这也是原项目为什么
 * 另外搞了一套 {@code AST_Demo}（{@code Node/NumberNode/AddNode}）——
 * 两套体系完全不通用（探针 I10）。返回 {@code Value} 之后，
 * 同一棵树里可以既有算术节点又有布尔节点，
 * {@code (age + 5) * 2 > 50} 这种混合表达式才成为可能。</p>
 *
 * <p><b>为什么还要 describe()？</b>
 * 它把树还原成"带完整括号的规范文本"，用来验证优先级有没有解析对。
 * {@code "10 + 5 * 2"} 解析完 describe 出来是
 * {@code "(10 + (5 * 2))"} —— 一眼就能看出乘法先算。
 * 没有这个方法，验证优先级只能靠求值结果猜（10+5*2 是 20 还是 30），
 * 而"结果对"不等于"树对"：{@code 2 * 3 + 4} 与 {@code 2 * (3 + 4)}
 * 在某种输入下可能碰巧同值。</p>
 *
 * <p><b>为什么是 sealed？</b>
 * 节点类型是封闭的六种，编译器可以在新增节点时
 * 找出所有需要同步修改的地方。这和访问者模式里
 * {@code Document} 用 sealed 是同一个理由。</p>
 */
public sealed interface Expression permits NumberExpression, VariableExpression,
        ArithmeticExpression, UnaryExpression, ComparisonExpression, LogicalExpression {

    /**
     * 在当前上下文下求值。
     *
     * @param context 变量表，不能为 null
     * @return 数字或布尔结果
     * @throws ExpressionException 变量未定义、类型不匹配、除零、溢出
     */
    Value evaluate(Context context);

    /**
     * 还原成带完整括号的规范文本，用于验证运算符优先级。
     *
     * @return 形如 "(10 + (5 * 2))" 的文本
     */
    String describe();

    /**
     * 求值并要求结果是布尔值。
     *
     * <p>这是给"规则"场景准备的便利方法：调用方拿到
     * {@code "age > 18"} 的树之后，直接 {@code test(ctx)} 就能得 true/false，
     * 不用自己写 {@code asBoolean}。</p>
     *
     * @param context 变量表，不能为 null
     * @return 布尔结果
     * @throws ExpressionException 结果不是布尔，或求值失败
     */
    default boolean test(Context context) {
        return evaluate(requireContext(context)).asBoolean(describe());
    }

    /**
     * 校验上下文不为 null。
     *
     * <p>放在接口里而不是每个节点里，是为了让叶子节点
     * （数字、变量）在"真正用到上下文"的那一刻给出统一的消息，
     * 而不是各自抛一个 NPE。组合节点只是把上下文往下传，
     * 所以它们不需要重复检查。</p>
     *
     * @param context 待校验的上下文
     * @return 原上下文
     * @throws IllegalArgumentException context 为 null
     */
    static Context requireContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为 null");
        }
        return context;
    }
}
