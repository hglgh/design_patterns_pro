package com.patterns.behavioral.interpreter.beginner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 解释器入门演示：把「VIP 且金额大于 100」这样的资格规则表示成表达式树。
 *
 * <p>改造前：规则写死在 if 里，换一条规则就改代码；
 * 改造后：每种规则是一个 Expression 类，用 And/Or/Not 组合成树，逐节点求值。
 *
 * <p>注意：本示例只演示「表达式树怎么求值」，不包含词法分析和语法分析。
 * 从字符串解析出这棵树，是另一个独立的问题。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：规则写死在 if 里 ==========");

        DiscountRule rule = new DiscountRule();
        System.out.println("  VIP / 150 元：" + rule.isEligible(true, 150, false));
        System.out.println("  VIP / 80 元：" + rule.isEligible(true, 80, false));
        System.out.println("  新用户 / 50 元：" + rule.isEligible(false, 50, true));

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 规则「VIP 且金额大于 100」写死在方法里；");
        System.out.println("  2. 想改成「或新用户」，必须改方法、重新编译；");
        System.out.println("  3. 规则无法在运行时组合，也无法单独测试每个条件。");
    }

    static class DiscountRule {

        boolean isEligible(boolean vip, int amount, boolean newUser) {
            return vip && amount > 100;        // 新规则来了，只能改这一行
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：规则是一棵表达式树 ==========");

        // 规则 1：vip AND amount > 100
        Expression vipAndAmount = new And(
                new Flag("vip"),
                new NumberGreaterThan("amount", 100));

        Context c1 = new Context();
        c1.setNumber("amount", 150);
        c1.setFlag("vip", true);

        Context c2 = new Context();
        c2.setNumber("amount", 80);
        c2.setFlag("vip", true);

        System.out.println("  VIP / 150 元：" + vipAndAmount.interpret(c1));
        System.out.println("  VIP / 80 元：" + vipAndAmount.interpret(c2));

        System.out.println();
        System.out.println("  —— 新规则：VIP 且金额大于 100，或者是新用户 ——");
        // 不改 And / Flag / NumberGreaterThan，只新增 Or 并重新组合
        Expression vipOrNewUser = new Or(vipAndAmount, new Flag("newUser"));

        Context c3 = new Context();
        c3.setNumber("amount", 50);
        c3.setFlag("newUser", true);

        System.out.println("  新用户 / 50 元：" + vipOrNewUser.interpret(c3));

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每条语法一个类，规则一多类就多；");
        System.out.println("  2. 从字符串解析成表达式树需要额外的解析器，本示例没做；");
        System.out.println("  3. 只有两三条固定规则时，一个方法或几个 Predicate 更简单。");
    }

    /** 上下文：保存规则里用到的变量。 */
    static class Context {

        private final Map<String, Integer> numbers = new HashMap<>();
        private final Set<String> flags = new HashSet<>();

        void setNumber(String name, int value) {
            numbers.put(name, value);
        }

        void setFlag(String name, boolean value) {
            if (value) {
                flags.add(name);
            } else {
                flags.remove(name);
            }
        }

        int number(String name) {
            Integer value = numbers.get(name);
            if (value == null) {
                throw new IllegalArgumentException("未定义的变量：" + name);
            }
            return value;
        }

        boolean flag(String name) {
            return flags.contains(name);
        }
    }

    /** 抽象表达式：所有节点都能被求值。 */
    interface Expression {
        boolean interpret(Context context);
    }

    /** 终结符：一个布尔变量。 */
    static class Flag implements Expression {

        private final String name;

        Flag(String name) {
            this.name = name;
        }

        @Override
        public boolean interpret(Context context) {
            return context.flag(name);
        }
    }

    /** 终结符：数字比较。 */
    static class NumberGreaterThan implements Expression {

        private final String variable;
        private final int threshold;

        NumberGreaterThan(String variable, int threshold) {
            this.variable = variable;
            this.threshold = threshold;
        }

        @Override
        public boolean interpret(Context context) {
            return context.number(variable) > threshold;
        }
    }

    /** 非终结符：与。 */
    static class And implements Expression {

        private final Expression left;
        private final Expression right;

        And(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean interpret(Context context) {
            return left.interpret(context) && right.interpret(context);
        }
    }

    /** 非终结符：或。 */
    static class Or implements Expression {

        private final Expression left;
        private final Expression right;

        Or(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean interpret(Context context) {
            return left.interpret(context) || right.interpret(context);
        }
    }

    /** 非终结符：非。本示例没有用到，留给读者扩展。 */
    static class Not implements Expression {

        private final Expression inner;

        Not(Expression inner) {
            this.inner = inner;
        }

        @Override
        public boolean interpret(Context context) {
            return !inner.interpret(context);
        }
    }
}
