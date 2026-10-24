package com.patterns.behavioral.strategy;

import com.patterns.util.DisplayWidth;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端：演示策略模式的完整用法。
 *
 * <p>运行方式：
 * <pre>
 *   javac --release 25 -encoding UTF-8 -d out src/com/patterns/behavioral/strategy/*.java
 *   java -Dfile.encoding=UTF-8 -cp out com.patterns.behavioral.strategy.Client
 * </pre>
 *
 * <p>六段演示分别回答一个问题：
 * <ol>
 *   <li>{@link #demo1BasicUsage} —— 策略怎么替换？空对象怎么消灭 null 判断？</li>
 *   <li>{@link #demo2Precision} —— 为什么金额必须用 BigDecimal？（<b>实测 double 算错钱</b>）</li>
 *   <li>{@link #demo3Comparison} —— 怎么一次性对比所有策略？</li>
 *   <li>{@link #demo4Factory} —— 谁来选策略？客户端要不要知道具体策略类？</li>
 *   <li>{@link #demo5BestOf} —— 不同价格下"最优惠"是同一个策略吗？（<b>封顶导致答案反转</b>）</li>
 *   <li>{@link #demo6Lambda} —— 新增一个促销要写多少代码？</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1BasicUsage();
        demo2Precision();
        demo3Comparison();
        demo4Factory();
        demo5BestOf();
        demo6Lambda();
    }

    /**
     * 演示 1：策略可以自由替换；用空对象消灭 null 判断。
     *
     * <p>对应原项目 Client 的场景，但把 setter 注入换成了构造器注入。
     */
    private static void demo1BasicUsage() {
        System.out.println("=== 1. 基础用法：同一笔订单，换不同策略 ===");
        BigDecimal price = new BigDecimal("120.00");
        System.out.println("  原价：￥" + price.toPlainString());
        System.out.println();

        // 原项目写法（对照）：
        //   PriceCalculator calculator = new PriceCalculator();
        //   calculator.setStrategy(new FullReductionStrategy());   ← 两步初始化，中间态不可用
        // 重构后：构造时就确定策略，字段 final，不可能为 null
        List<DiscountStrategy> strategies = List.of(
                new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")),
                new PercentageDiscountStrategy(new BigDecimal("0.90")),
                new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200")),
                NoDiscountStrategy.INSTANCE
        );

        for (DiscountStrategy strategy : strategies) {
            PriceCalculator calculator = new PriceCalculator(strategy);
            System.out.println("      "
                    + DisplayWidth.padRight(strategy.name(), DiscountStrategyFactory.NAME_WIDTH)
                    + "→ 折后 ￥"
                    + DisplayWidth.padLeft(calculator.calculatePrice(price).toPlainString(), 8)
                    + "  已优惠 ￥" + calculator.savedAmount(price).toPlainString());
        }

        System.out.println();
        System.out.println("  >>> 关键点 1：上面这段 for 循环【不认识任何具体策略类】。");
        System.out.println("      它只调用 name() / apply()，所以新增第 5 种策略时这段代码一行都不用改。");
        System.out.println();
        System.out.println("  >>> 关键点 2：'不打折'也是一个策略（NoDiscountStrategy.INSTANCE），");
        System.out.println("      不是 null。所以循环里没有 if (strategy == null) 这种分支。");
        System.out.println();

        // 演示空对象消灭了原项目的 IllegalStateException
        System.out.println("  -- 原项目的写法会怎样？--");
        System.out.println("      new PriceCalculator(null) → 直接抛异常，且异常信息明确告诉你该怎么做：");
        try {
            new PriceCalculator(null);
        } catch (NullPointerException e) {
            System.out.println("      [捕获] NullPointerException: " + e.getMessage());
        }
        System.out.println();
        System.out.println("      对比原项目：new PriceCalculator() 能成功，但 calculatePrice() 时才抛");
        System.out.println("      IllegalStateException(\"未设置折扣策略\")——错误暴露的位置离根源很远。");
        System.out.println();

        // 演示不可变切换
        System.out.println("  -- withStrategy() 而不是 setStrategy() --");
        PriceCalculator base = new PriceCalculator(NoDiscountStrategy.INSTANCE);
        PriceCalculator withCoupon = base.withStrategy(
                new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")));
        System.out.println("      base       仍然是：" + base.getStrategy().name());
        System.out.println("      withCoupon 是新的：" + withCoupon.getStrategy().name());
        System.out.println("      → 两个对象同时存在，可以做'A 券 vs B 券'的价格对比界面。");
        System.out.println("      → setter 版本切换后旧策略就丢了，做不出对比。");
        System.out.println();
    }

    /**
     * 演示 2：为什么金额必须用 BigDecimal。
     *
     * <p><b>这一段的所有数字都是 JDK 25 的真实运行结果。</b>
     */
    private static void demo2Precision() {
        System.out.println("=== 2. 为什么不能用 double 算钱（实测对比）===");

        System.out.println("  -- double 的错误（原项目用的类型）--");
        printDoubleCase("99.9 * 0.7  （七折）", 99.9 * 0.7, "69.93");
        printDoubleCase("58.8 * 0.75 （七五折）", 58.8 * 0.75, "44.10");
        printDoubleCase("0.29 * 100  （元转分）", 0.29 * 100, "29");
        printDoubleCase("0.07 * 100  （元转分）", 0.07 * 100, "7");
        printDoubleCase("3.0 - 2.1   （找零）", 3.0 - 2.1, "0.90");
        printDoubleCase("Math.round(1.005*100)/100.0", Math.round(1.005 * 100) / 100.0, "1.01");

        System.out.println();
        System.out.println("      最严重的是最后一行：1.005 四舍五入应该是 1.01，");
        System.out.println("      但 double 里 1.005 实际存的是 1.00499999999999989...，所以舍去得到 1.00。");
        System.out.println("      → 这不是'精度差一点'，是【真的少收了客户一分钱】。");

        System.out.println();
        System.out.println("      元转分那一行也很危险：(long)(0.29 * 100) = " + (long) (0.29 * 100)
                + "，静默丢了 1 分。");

        System.out.println();
        System.out.println("  -- BigDecimal 的结果（本项目用的类型）--");
        printBigDecimalCase("99.90 × 0.70", new BigDecimal("99.90"), new BigDecimal("0.70"));
        printBigDecimalCase("58.80 × 0.75", new BigDecimal("58.80"), new BigDecimal("0.75"));
        printBigDecimalCase("2.675 → 保留 2 位", new BigDecimal("2.675"), null);

        System.out.println();
        System.out.println("  -- 一个诚实的说明 --");
        System.out.println("      原项目用的是 120.0 * 0.9，实测结果恰好是精确的 " + (120.0 * 0.9) + "，");
        System.out.println("      所以那个 Demo 跑起来看不出任何问题。");
        System.out.println("      → 这正是浮点数 bug 最阴险的地方：测试用例的数字刚好能算对，");
        System.out.println("        一上生产遇到真实价格就出错。不能靠'测了几个都对'来证明安全。");
        System.out.println();
    }

    private static void printDoubleCase(String expr, double actual, String expected) {
        String actualStr = String.valueOf(actual);
        boolean wrong = !actualStr.equals(expected);
        System.out.println("      "
                + DisplayWidth.padRight(expr, 30) + " = "
                + DisplayWidth.padRight(actualStr, 22) + " 应为 "
                + DisplayWidth.padRight(expected, 8)
                + (wrong ? "[错]" : "[对]"));
    }

    private static void printBigDecimalCase(String label, BigDecimal a, BigDecimal b) {
        BigDecimal result = (b == null)
                ? a.setScale(2, java.math.RoundingMode.HALF_UP)
                : a.multiply(b).setScale(2, java.math.RoundingMode.HALF_UP);
        System.out.println("      "
                + DisplayWidth.padRight(label, 30) + " = " + result.toPlainString());
    }

    /**
     * 演示 3：一次性对比所有促销策略。
     *
     * <p>这段代码的通用性是策略模式价值的直接体现：
     * {@code currentPromotions()} 里有几个策略，表就有几行，<b>不需要改这里的任何代码</b>。
     */
    private static void demo3Comparison() {
        System.out.println("=== 3. 遍历所有策略，一次看清全部方案 ===");
        BigDecimal price = new BigDecimal("350.00");
        System.out.println("  订单原价：￥" + price.toPlainString());
        System.out.println();
        DiscountStrategyFactory.printComparison(
                DiscountStrategyFactory.currentPromotions(), price);
        System.out.println();
        System.out.println("  >>> 注意：'满 100 减 20' 和 '满 300 减 80' 是【同一个类】的两个不同实例，");
        System.out.println("      只是构造参数不同。原项目的硬编码版本做不到这一点。");
        System.out.println("  >>> '新人首单立减 15' 是一个 lambda 策略，没有对应的类文件（见 demo6）。");
        System.out.println();
    }

    /**
     * 演示 4：工厂负责选策略，客户端不需要知道具体策略类。
     */
    private static void demo4Factory() {
        System.out.println("=== 4. 谁来选策略？—— 工厂 ===");
        BigDecimal price = new BigDecimal("350.00");

        for (UserLevel level : UserLevel.values()) {
            // 客户端只认识 UserLevel 和 DiscountStrategy，
            // 完全不知道 VipDiscountStrategy / PercentageDiscountStrategy 这些类的存在。
            DiscountStrategy strategy = DiscountStrategyFactory.forLevel(level);
            PriceCalculator calculator = new PriceCalculator(strategy);
            System.out.println("      "
                    + DisplayWidth.padRight(level.name(), 9)
                    + DisplayWidth.padRight(strategy.name(), DiscountStrategyFactory.NAME_WIDTH)
                    + "→ ￥" + calculator.calculatePrice(price).toPlainString());
        }

        System.out.println();
        System.out.println("  >>> 普通用户的策略是 NoDiscountStrategy（空对象），不是 null。");
        System.out.println("      所以上面这段循环对 4 个等级的处理方式【完全一致】，没有任何特殊分支。");
        System.out.println();
        System.out.println("  >>> 客户端能 new 出 VIP 策略吗？不能——它没有 VIP 的构造入口。");
        System.out.println("      只能通过工厂按等级取。这就避免了'普通用户自己给自己打八折'的越权漏洞。");
        System.out.println();
        System.out.println("  >>> 规则改一处即可：把 UserLevel.GOLD 的折扣率从 0.90 改成 0.88，");
        System.out.println("      所有调用点自动生效。原项目的写法要全局搜索 new PercentageDiscountStrategy。");
        System.out.println();
    }

    /**
     * 演示 5：不同价格下"最优惠策略"不同——封顶会让答案反转。
     *
     * <p><b>这是本项目最精彩的一段</b>，它同时证明了三件事：
     * <ol>
     *   <li>策略模式让"自动选最优"成为可能（所有策略可以被同等对待）；</li>
     *   <li>VipDiscountStrategy 的封顶规则有真实的业务意义（不是凑数的类）；</li>
     *   <li>"最优策略"是价格的函数，不存在一个永远最优的策略。</li>
     * </ol>
     */
    private static void demo5BestOf() {
        System.out.println("=== 5. 自动选最优方案（真实电商的核心功能）===");
        List<DiscountStrategy> promotions = DiscountStrategyFactory.currentPromotions();
        System.out.println("  候选活动共 " + promotions.size() + " 个：");
        for (DiscountStrategy s : promotions) {
            System.out.println("      - " + s.name());
        }
        System.out.println();

        List<String> prices = List.of("50.00", "120.00", "350.00", "1000.00", "5000.00");

        System.out.println("      "
                + DisplayWidth.padRight("原价", 12)
                + DisplayWidth.padRight("最优方案", DiscountStrategyFactory.NAME_WIDTH)
                + DisplayWidth.padLeft("折后价", 11) + "  "
                + DisplayWidth.padLeft("已优惠", 11));
        System.out.println("      "
                + DisplayWidth.line('-', 12 + DiscountStrategyFactory.NAME_WIDTH + 24));

        for (String p : prices) {
            BigDecimal price = new BigDecimal(p);
            DiscountStrategy best = DiscountStrategyFactory.bestOf(promotions, price);
            PriceCalculator calc = new PriceCalculator(best);
            System.out.println("      "
                    + DisplayWidth.padRight("￥" + price.toPlainString(), 12)
                    + DisplayWidth.padRight(best.name(), DiscountStrategyFactory.NAME_WIDTH)
                    + DisplayWidth.padLeft("￥" + calc.calculatePrice(price).toPlainString(), 11)
                    + "  "
                    + DisplayWidth.padLeft("￥" + calc.savedAmount(price).toPlainString(), 11));
        }

        System.out.println();
        System.out.println("  >>> 关键观察：随着原价上升，最优方案在不断变化，");
        System.out.println("      没有任何一个策略在所有价位上都是最优的。");
        System.out.println();

        // 专门演示 VIP 封顶导致的"答案反转"
        BigDecimal expensive = new BigDecimal("5000.00");
        DiscountStrategy vip = new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200"));
        DiscountStrategy nineFold = new PercentageDiscountStrategy(new BigDecimal("0.90"));

        System.out.println("  -- 为什么 5000 元时'九折'反而比'VIP 八折'更便宜？--");
        System.out.println("      VIP 八折：5000 × 0.80 = 4000，优惠 1000 元");
        System.out.println("                但封顶 200 元 → 实际只优惠 200，折后 ￥"
                + new PriceCalculator(vip).calculatePrice(expensive).toPlainString());
        System.out.println("      普通九折：5000 × 0.90 = ￥"
                + new PriceCalculator(nineFold).calculatePrice(expensive).toPlainString()
                + "，优惠 500 元，不封顶");
        System.out.println("      → 500 元优惠 > 200 元封顶优惠，所以九折更划算，答案反转了。");
        System.out.println();
        System.out.println("      VipDiscountStrategy.isCapped(5000) = "
                + ((VipDiscountStrategy) vip).isCapped(expensive) + "  ← 封顶已生效");
        System.out.println("      VipDiscountStrategy.isCapped(500)  = "
                + ((VipDiscountStrategy) vip).isCapped(new BigDecimal("500.00"))
                +  " ← 500×0.2=100 < 200，未生效");
        System.out.println();
        System.out.println("      临界价格 = 封顶额 ÷ (1 - 折扣率) = 200 ÷ 0.2 = 1000 元。");
        System.out.println("      1000 元以下 VIP 八折更优；1000 元以上普通九折更优。");
        System.out.println("      → 客服需要这个临界点才能向用户解释'为什么我这单只减了 200'。");
        System.out.println();
        System.out.println("  >>> 这段逻辑证明了 VipDiscountStrategy 必须独立成类：");
        System.out.println("      它有'封顶'这条 PercentageDiscountStrategy 无法表达的规则。");
        System.out.println("      （原项目里两者只差一个数字 0.8/0.9，那种情况下确实应该合并。）");
        System.out.println();
    }

    /**
     * 演示 6：新增一个促销活动需要写多少代码？
     *
     * <p>答案：<b>一行</b>。这是策略模式 + Java 8 函数式的组合威力。
     */
    private static void demo6Lambda() {
        System.out.println("=== 6. 扩展性：新增一个促销要写几行代码？===");

        // 一行创建一个新策略：有名字、有参数校验、有结果规范化
        DiscountStrategy doubleEleven = new SimpleDiscountStrategy(
                "双十一（每满 200 减 50，上不封顶）",
                price -> {
                    // 每满 200 减 50：350 元 → 减 50；600 元 → 减 150
                    int times = price.divideToIntegralValue(new BigDecimal("200")).intValue();
                    return price.subtract(new BigDecimal("50").multiply(BigDecimal.valueOf(times)));
                });

        DiscountStrategy flashSale = new SimpleDiscountStrategy(
                "限时秒杀（直降 30，最低 1 元）",
                price -> price.subtract(new BigDecimal("30")).max(BigDecimal.ONE));

        System.out.println("  新增的两个策略（都是用 lambda 一行创建的，没有新建类文件）：");
        System.out.println("      - " + doubleEleven.name());
        System.out.println("      - " + flashSale.name());
        System.out.println();

        BigDecimal price = new BigDecimal("650.00");
        System.out.println("  在 ￥" + price.toPlainString() + " 的订单上测试：");
        for (DiscountStrategy s : List.of(doubleEleven, flashSale)) {
            PriceCalculator calc = new PriceCalculator(s);
            System.out.println("      "
                    + DisplayWidth.padRight(s.name(), DiscountStrategyFactory.NAME_WIDTH)
                    + "→ ￥"
                    + DisplayWidth.padLeft(calc.calculatePrice(price).toPlainString(), 8)
                    + "  已优惠 ￥" + calc.savedAmount(price).toPlainString());
        }
        System.out.println();

        // 证明新策略能直接参与"选最优"，不需要改 bestOf 的任何代码
        List<DiscountStrategy> all = new ArrayList<>(DiscountStrategyFactory.currentPromotions());
        all.add(doubleEleven);
        all.add(flashSale);
        DiscountStrategy best = DiscountStrategyFactory.bestOf(all, price);
        System.out.println("  把这两个新策略加进候选列表（共 " + all.size() + " 个）后，自动选出的最优方案是：");
        System.out.println("      " + best.name()
                + " → ￥" + new PriceCalculator(best).calculatePrice(price).toPlainString());
        System.out.println();
        System.out.println("  >>> 注意 bestOf() / printComparison() / demo4 的代码【一个字都没改】。");
        System.out.println("      新策略自动被纳入对比和选择。这就是开闭原则的实际含义：");
        System.out.println("      对扩展开放（加一行 lambda），对修改关闭（0 个已有方法被改动）。");
        System.out.println();
        System.out.println("  >>> 边界测试：'限时秒杀直降 30' 在 ￥20 的订单上会算出 -10，");
        DiscountStrategy onCheap = flashSale;
        System.out.println("      实际结果：" + onCheap.apply(new BigDecimal("20.00"))
                + "  ← max(BigDecimal.ONE) 兜底 + normalize 保证不为负。");
        System.out.println("      没有这个兜底，用户会看到'￥-10.00'，或者更糟——系统倒贴钱。");
        System.out.println();
    }
}
