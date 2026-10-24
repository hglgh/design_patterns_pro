package com.patterns.behavioral.interpreter;

/**
 * 门面（Facade）：把词法、语法、求值三步收敛成四个静态方法。
 *
 * <p><b>为什么要这层门面？</b>
 * 因为解释器模式的"客户端"其实不需要知道树是怎么搭的。
 * 业务代码关心的是"给定一段规则文本和一份数据，结果是什么"，
 * 而不是 {@code new AndExpression(...)} 的构造顺序。
 * 原项目恰恰相反：客户端要手工拼装
 * {@code new AndExpression(new GreaterThanExpression(...), ...)}，
 * 一旦拼错（比如两个条件都用了 {@code >}，导致同一个字段
 * 被赋值两次）就得到一个半残的树（探针 I1/I2）。</p>
 *
 * <p><b>门面不是"多套一层"。</b>
 * 它没有隐藏任何能力：{@link #parse} 照样把树交给你，
 * 想自己遍历、自己缓存、自己 {@code describe} 都行。
 * 它只是把"调用顺序"固化成一句代码，
 * 让 90% 的使用场景只写一行。</p>
 *
 * <p><b>为什么不设计成单例 + 实例方法？</b>
 * 引擎没有任何状态（状态全在 {@link Context} 和表达式树里），
 * 所以静态方法最直白。需要状态的是"规则缓存"，
 * 那是调用方该关心的事 —— 本项目不引入缓存，
 * 因为示例里每次都只解析几条规则，收益为零。</p>
 */
public final class ExpressionEngine {

    private ExpressionEngine() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 解析规则文本，得到可复用的表达式树。
     *
     * <p>同一段文本反复求值时，应该只 parse 一次、多次 evaluate。
     * 解析是 O(文本长度)，求值只依赖上下文。</p>
     *
     * @param source 规则文本
     * @return 表达式树
     * @throws ExpressionException 词法或语法错误
     */
    public static Expression parse(String source) {
        return Parser.parse(source);
    }

    /**
     * 解析并求值。
     *
     * @param source  规则文本
     * @param context 变量表
     * @return 数字或布尔结果
     * @throws ExpressionException 解析或求值失败
     */
    public static Value evaluate(String source, Context context) {
        return Parser.parse(source).evaluate(Expression.requireContext(context));
    }

    /**
     * 解析并求值，要求结果是布尔。
     *
     * @param source  规则文本
     * @param context 变量表
     * @return 布尔结果
     * @throws ExpressionException 解析失败、求值失败，或结果不是布尔
     */
    public static boolean test(String source, Context context) {
        return Parser.parse(source).test(context);
    }

    /**
     * 解析并输出规范文本，用于检查运算符优先级。
     *
     * @param source 规则文本
     * @return 带完整括号的规范文本
     * @throws ExpressionException 解析失败
     */
    public static String describe(String source) {
        return Parser.parse(source).describe();
    }
}
