package com.patterns.behavioral.interpreter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 演示入口：十段场景，逐条对照原项目探针 I1-I10 发现的问题。
 *
 * <p><b>为什么 Demo 要按「原项目问题 → 现在的行为」组织？</b>
 * 因为设计模式最容易被讲成"多写了几个类"。
 * 只有把同一段输入放到两套实现下对比，
 * 才能看清那些类到底换来了什么：
 * 一次明确的报错、一个不会丢的条件、一条可复现的结果。</p>
 *
 * <p>运行方式（Windows，JDK 25）：</p>
 * <pre>{@code
 * javac -Xlint:all -encoding UTF-8 -d out src\com\patterns\behavioral\interpreter\*.java
 * java -Dstdout.encoding=UTF-8 -cp out com.patterns.behavioral.interpreter.Client
 * }</pre>
 */
public final class Client {

    private Client() {
    }

    /**
     * 程序入口。
     *
     * @param args 未使用
     * @throws Exception Demo 9 会等待后台线程结束
     */
    public static void main(String[] args) throws Exception {
        demo1TextToTreeAndReuse();
        demo2ThreeConditions();
        demo3SingleCondition();
        demo4ComparisonOperators();
        demo5ErrorPosition();
        demo6UnknownVariable();
        demo7PrecedenceAndAssociativity();
        demo8TrailingGarbage();
        demo9LongChain();
        demo10SafetyNet();
    }

    private static void demo1TextToTreeAndReuse() {
        section("Demo 1 文本进、树出来，同一棵树换上下文复用");
        String rule = "age > 18 && score < 500";
        Expression tree = ExpressionEngine.parse(rule);
        System.out.println("  规则文本: " + rule);
        System.out.println("  语法树  : " + tree.describe());

        Context minor = Context.empty().with("age", 17).with("score", 300);
        Context adult = Context.empty().with("age", 25).with("score", 300);
        Context adultHighScore = Context.empty().with("age", 25).with("score", 1000);
        System.out.println("  " + minor.describe() + " -> " + tree.test(minor));
        System.out.println("  " + adult.describe() + " -> " + tree.test(adult));
        System.out.println("  " + adultHighScore.describe() + " -> " + tree.test(adultHighScore));
        System.out.println("  只解析一次、求值三次；Context 不可变，三次互不影响");
        System.out.println("  原项目：手工 new AndExpression(new GreaterThanExpression(...), ...)，文本不参与");
    }

    private static void demo2ThreeConditions() {
        section("Demo 2 三个条件不再丢一个（原项目探针 I1a：应 false 实 true）");
        String rule = "age > 18 && score < 500 && age < 30";
        System.out.println("  规则文本: " + rule);
        System.out.println("  语法树  : " + ExpressionEngine.describe(rule));

        Context failed = Context.empty().with("age", 25).with("score", 1000);
        System.out.println("  " + failed.describe() + " -> " + ExpressionEngine.test(rule, failed));
        System.out.println("  期望 false：score=1000 不满足 score < 500");
        System.out.println("  原项目：第三条 age < 30 覆盖了 score < 500，只比较 age，错判为 true");

        Context passed = Context.empty().with("age", 25).with("score", 300);
        System.out.println("  " + passed.describe() + " -> " + ExpressionEngine.test(rule, passed));
    }

    private static void demo3SingleCondition() {
        section("Demo 3 单条件不再空指针（原项目探针 I2）");
        Context context = Context.empty().with("age", 25).with("score", 300);
        System.out.println("  age > 18    -> " + ExpressionEngine.test("age > 18", context));
        System.out.println("  score < 500 -> " + ExpressionEngine.test("score < 500", context));
        System.out.println("  age + 5     -> " + ExpressionEngine.evaluate("age + 5", context).display());
        System.out.println("  原项目：单独的 age > 18 让 AndExpression 的 right 保持 null，求值即 NPE");
    }

    private static void demo4ComparisonOperators() {
        section("Demo 4 六种比较运算符，包括原项目误判的 >=（探针 I3）");
        Context context = Context.empty()
                .with("age", 18)
                .with("score", 500)
                .with("weight", 61);
        String[] rules = {
                "age > 18",
                "age >= 18",
                "score < 500",
                "score <= 500",
                "age == 18",
                "age != 18"
        };
        for (String rule : rules) {
            System.out.println("  " + pad(rule, 14) + " -> " + ExpressionEngine.test(rule, context));
        }
        System.out.println("  原项目：age >= 18 被按 > 切开，数字部分剩下 = 18，抛 NumberFormatException");

        Context slight = Context.empty().with("weight", 60);
        System.out.println("  边界值 " + slight.describe() + "：weight > 60 -> "
                + ExpressionEngine.test("weight > 60", slight)
                + "，weight >= 60 -> " + ExpressionEngine.test("weight >= 60", slight));
    }

    private static void demo5ErrorPosition() {
        section("Demo 5 错误带位置，caret 指到你写错的那个字符（探针 I4/I8）");
        expectFailure("age > @18（非法字符）", () -> ExpressionEngine.parse("age > @18"));
        expectFailure("age = 18（单个等号）", () -> ExpressionEngine.parse("age = 18"));
        expectFailure("10 +（缺右操作数）", () -> ExpressionEngine.parse("10 +"));
        expectFailure("10 + 5)（多右括号）", () -> ExpressionEngine.parse("10 + 5)"));
        expectFailure("（空规则）", () -> ExpressionEngine.parse(""));
        System.out.println("  原项目：NumberFormatException: For input string: \"\"，不含规则原文也不含位置");
    }

    private static void demo6UnknownVariable() {
        section("Demo 6 未定义变量立刻报错，并列出已定义变量（探针 I5/I6）");
        Context context = Context.empty().with("age", 25).with("score", 300);
        System.out.println("  上下文: " + context.describe());
        expectFailure("AGE > 18（大小写不一致）", () -> ExpressionEngine.test("AGE > 18", context));
        expectFailure("weight > 60（变量不存在）", () -> ExpressionEngine.test("weight > 60", context));
        System.out.println("  原项目：两种情况都静默返回 false，规则悄悄失效，没有任何日志");
    }

    private static void demo7PrecedenceAndAssociativity() {
        section("Demo 7 优先级与结合性：看 describe()，而不是只看结果");
        Context context = Context.empty().with("age", 25);
        String[] rules = {
                "10 + 5 * 2",
                "(10 + 5) * 2",
                "10 - 3 - 2",
                "2 * 3 + 4 * 5",
                "100 / 7",
                "-5 + 3",
                "age + 5"
        };
        for (String rule : rules) {
            Expression tree = ExpressionEngine.parse(rule);
            System.out.println("  " + pad(rule, 14) + " 树 " + pad(tree.describe(), 26)
                    + " 值 " + tree.evaluate(context).display());
        }
        System.out.println("  10 - 3 - 2 = 5 说明减法左结合；若写成右结合会得 9");
        System.out.println("  原项目：只支持 +，10-5 算成 10，10/2 也算成 10（探针 I10）");
    }

    private static void demo8TrailingGarbage() {
        section("Demo 8 尾部垃圾不再被静默丢弃（探针 I7）");
        System.out.println("  「10*5」   -> " + ExpressionEngine.evaluate("10*5", Context.empty()).display());
        System.out.println("  「  10  +  5  」 -> "
                + ExpressionEngine.evaluate("  10  +  5  ", Context.empty()).display());
        expectFailure("10abc", () -> ExpressionEngine.evaluate("10abc", Context.empty()));
        expectFailure("10 20", () -> ExpressionEngine.evaluate("10 20", Context.empty()));
        System.out.println("  原项目：10abc、10 中文、10 20 全部算出 10，多余内容无人过问");
    }

    private static void demo9LongChain() throws Exception {
        section("Demo 9 长链：解析用循环，求值仍是递归（探针 I9）");
        int[] sizes = {1000, 10000, 50000};
        Expression biggest = null;
        int describeLength = 0;
        for (int size : sizes) {
            String rule = sumRule(size);
            Expression tree = ExpressionEngine.parse(rule);
            String outcome;
            try {
                outcome = "结果 " + tree.evaluate(Context.empty()).display();
            } catch (StackOverflowError e) {
                outcome = "StackOverflowError";
            }
            System.out.println("  " + pad(size + " 项", 10) + "文本 " + pad(rule.length() + " 字符", 12)
                    + "解析成功，默认栈求值 -> " + outcome);
            if (size == sizes[0]) {
                describeLength = tree.describe().length();
            }
            biggest = tree;
        }
        final Expression lastTree = biggest;

        System.out.println("  4 项的形状: " + ExpressionEngine.describe(sumRule(4)) + "（左深树，左结合的证据）");
        System.out.println("  " + sizes[0] + " 项的 describe() 长度 " + describeLength + " 字符");

        AtomicReference<String> result = new AtomicReference<>("（线程未完成）");
        Thread thread = new Thread(null, () -> {
            try {
                result.set("结果 " + lastTree.evaluate(Context.empty()).display());
            } catch (StackOverflowError e) {
                result.set("StackOverflowError");
            }
        }, "big-stack-demo", 256L * 1024 * 1024);
        thread.start();
        thread.join();
        System.out.println("  50000 项换 256 MB 栈的线程 -> " + result.get());
        System.out.println("  结论：递归求值的深度上限由 JVM 栈决定，不是算法算错；");
        System.out.println("        真要跑百万项，得改成显式栈或把同级链摊平成 N 元节点");
    }

    private static String sumRule(int terms) {
        StringBuilder text = new StringBuilder(terms * 4);
        for (int index = 0; index < terms; index++) {
            if (index > 0) {
                text.append(" + ");
            }
            text.append('1');
        }
        return text.toString();
    }

    private static void demo10SafetyNet() {
        section("Demo 10 安全网：类型、除零、溢出、短路、嵌套上限");
        expectFailure("10 && 5（逻辑运算拿到数字）",
                () -> ExpressionEngine.evaluate("10 && 5", Context.empty()));
        expectFailure("(age > 18) + 1（算术运算拿到布尔）",
                () -> ExpressionEngine.evaluate("(age > 18) + 1", Context.empty().with("age", 25)));
        expectFailure("10 / 0", () -> ExpressionEngine.evaluate("10 / 0", Context.empty()));
        expectFailure("9223372036854775807 + 1（溢出）",
                () -> ExpressionEngine.evaluate("9223372036854775807 + 1", Context.empty()));

        String guarded = "count > 0 && total / count > 10";
        Context zero = Context.empty().with("count", 0).with("total", 100);
        Context two = Context.empty().with("count", 2).with("total", 100);
        System.out.println("  短路保护除零: 「" + guarded + "」");
        System.out.println("    " + zero.describe() + " -> " + ExpressionEngine.test(guarded, zero)
                + "（右侧未求值，没有抛除零）");
        System.out.println("    " + two.describe() + " -> " + ExpressionEngine.test(guarded, two));

        String deep = "(".repeat(Parser.MAX_NESTING + 1) + "1" + ")".repeat(Parser.MAX_NESTING + 1);
        expectFailure((Parser.MAX_NESTING + 1) + " 层括号（超过上限 " + Parser.MAX_NESTING + "）",
                () -> ExpressionEngine.parse(deep));
        String allowed = "(".repeat(Parser.MAX_NESTING) + "1" + ")".repeat(Parser.MAX_NESTING);
        System.out.println("  正好 " + Parser.MAX_NESTING + " 层括号 -> "
                + ExpressionEngine.evaluate(allowed, Context.empty()).display() + "（边界值仍然可用）");
    }

    private static void expectFailure(String label, Runnable action) {
        System.out.println("  · " + label);
        try {
            action.run();
            System.out.println("    -> 没有抛异常（与预期不符）");
        } catch (ExpressionException e) {
            for (String line : e.render().split("\n")) {
                System.out.println("    " + line);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("    -> IllegalArgumentException: " + e.getMessage());
        }
    }

    private static String pad(String text, int width) {
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println(title);
        System.out.println("================================================================");
    }
}
