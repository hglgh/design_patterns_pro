package com.patterns.behavioral.interpreter;

/**
 * 终结符表达式（TerminalExpression）—— 数字。
 *
 * <p>它是整棵树里唯一"不依赖任何子节点"的节点，
 * 也是递归的终点：{@code evaluate} 不再往下调用谁，
 * 直接把常量包成 {@link Value.Num} 交回去。</p>
 *
 * <p><b>为什么单独用一个 record？</b>
 * 因为解释器模式要求"每个语法成分对应一个类"。
 * 如果把数字塞进 {@code ArithmeticExpression} 里用
 * {@code Integer leftLiteral} 之类的字段表示，
 * 很快就需要同时维护"左操作数是字面量还是子树"两套分支，
 * 代码会退化成 if/else 大杂烩 —— 那正是模式要消灭的东西。</p>
 *
 * <p><b>它真的需要 context 吗？</b>
 * 不需要，但签名必须带着 —— 这是组合的代价：
 * 父节点拿到的总是 {@link Expression}，不能为叶子节点
 * 特化一个不带参数的求值方法。参数被忽略是正常的。</p>
 */
public record NumberExpression(long value) implements Expression {

    @Override
    public Value evaluate(Context context) {
        Expression.requireContext(context);
        return Value.of(value);
    }

    @Override
    public String describe() {
        return Long.toString(value);
    }
}
