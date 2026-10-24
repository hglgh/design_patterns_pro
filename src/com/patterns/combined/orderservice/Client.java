package com.patterns.combined.orderservice;

import com.patterns.util.DisplayWidth;
import com.patterns.util.Money;

/**
 * 演示入口：<b>同一批折扣，两套实现，十个场景</b>。
 *
 * <p>组合示例最怕讲成「四个模式的名词堆在一起」。
 * 所以每一段 Demo 都遵循同一个结构：</p>
 * <ol>
 *   <li>先说清楚要证明什么；</li>
 *   <li>用 {@link LegacyOrderService} 跑一遍旧实现，拿到真实数字；</li>
 *   <li>用新链路跑同样的输入，给出明细；</li>
 *   <li>最后一行点明「差异来自哪个模式」。</li>
 * </ol>
 *
 * <p><b>为什么旧实现要真的跑，而不是在注释里写"旧实现会算错"？</b></p>
 * 因为「会算错」是断言，「算出 -0.09999999999999964」是证据。
 * 本文档所有旧实现的数字都是 JDK 25 下真实跑出来的，
 * 读者可以自己复现；而注释里的断言，读者只能选择相信。</p>
 *
 * <p>运行方式（Windows，JDK 25）：</p>
 * <pre>{@code
 * javac -Xlint:all -encoding UTF-8 -sourcepath src -d out src\com\patterns\combined\orderservice\*.java
 * java -Dstdout.encoding=UTF-8 -cp out com.patterns.combined.orderservice.Client
 * }</pre>
 *
 * @author design-patterns-pro
 */
public final class Client {

    private Client() {
    }

    /**
     * 程序入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        demo1LegacyBaseline();
        demo2SameResultNewWay();
        demo3MoneySafety();
        demo4OrderMatters();
        demo5CappedDecorator();
        demo6RequirementSkip();
        demo7ExclusiveGroup();
        demo8ResultViews();
        demo9Errors();
        demo10RoleSummary();
    }

    // ==================================================================
    // Demo 1：旧实现的基线输出（必须与原项目逐字一致）
    // ==================================================================

    /**
     * 复现原项目的 11 行输出。
     *
     * <p>这一段的意义不是"展示旧代码"，而是<b>证明这个复现是忠实的</b>：
     * 如果复现的输出与原项目不一致，后面所有对比都失去意义。</p>
     */
    @SuppressWarnings("deprecation")
    private static void demo1LegacyBaseline() {
        section("Demo 1 旧实现基线：150 元 + 满减 + 打折 + 积分");
        System.out.println("  调用：LegacyOrderService.placeOrder(150.0, \"full\", \"percent\", \"points\")");
        System.out.println();
        LegacyOrderService.placeOrder(150.0, "full", "percent", "points");
        System.out.println();
        System.out.println("  这 11 行与原项目 CombineApplicationExamples.OrderService 的输出逐字一致。");
        System.out.println("  注意两点：日志里没有任何策略名；每一行都是 println，无法关闭。");
    }

    // ==================================================================
    // Demo 2：同样的输入，新链路给出同样的数字 + 明细
    // ==================================================================

    /**
     * 用新链路算 150 元，目标是与旧实现同结果（107.00），但过程可解释。
     */
    private static void demo2SameResultNewWay() {
        section("Demo 2 新链路：同样的 107 元，但每一步都有名字和原因");
        DiscountPipeline pipeline = PipelineFactory.builder()
                .stage("满减", "full:100:20")
                .stage("会员九折", "percent:0.9")
                .stage("积分抵扣", "points:0.01:1000")
                .build();
        System.out.println("  链路：" + pipeline.describeChain());

        OrderContext context = OrderContext.builder("150")
                .userLevel(3)
                .availablePoints(1000)
                .build();
        System.out.println("  上下文：" + context.describe());
        System.out.println();

        DiscountResult result = pipeline.calculate(context);
        printIndented(result.render());
        System.out.println();
        System.out.println("  策略层负责「怎么减」，责任链负责「按顺序减、并说明每步」");
        System.out.println("  旧实现只打印前后价格，没有人知道 107 是怎么来的");
    }

    // ==================================================================
    // Demo 3：金额安全 —— 负价与浮点误差
    // ==================================================================

    /**
     * 三个真实价格，旧实现两个出错、一个语义错误。
     */
    @SuppressWarnings("deprecation")
    private static void demo3MoneySafety() {
        section("Demo 3 金额安全：负价、浮点尾巴、无积分也抵扣");

        double old11 = LegacyOrderService.silentPrice(11.0, "percent", "points");
        System.out.println("  旧 11.00 元 + 打折 + 积分 -> " + old11);

        DiscountPipeline smallPipeline = PipelineFactory.builder()
                .stage("九折", "percent:0.9")
                .stage("积分", "points:0.1:1000")
                .build();
        DiscountResult small = smallPipeline.calculate(
                OrderContext.builder("11").availablePoints(1000).build());
        System.out.println("  新 11.00 元 + 打折 + 积分 -> " + Money.yuan(small.finalPrice())
                + "（积分最多抵到 0）");

        double oldDecimal = LegacyOrderService.silentPrice(58.8, "percent", "points");
        System.out.println("  旧 58.80 元 + 打折 + 积分 -> " + oldDecimal);

        DiscountPipeline decimalPipeline = PipelineFactory.builder()
                .stage("九折", "percent:0.9")
                .stage("积分", "points:0.01:1000")
                .build();
        DiscountResult decimal = decimalPipeline.calculate(
                OrderContext.builder("58.8").availablePoints(1000).build());
        System.out.println("  新 58.80 元 + 打折 + 积分 -> " + Money.yuan(decimal.finalPrice()));

        double oldNoPoints = LegacyOrderService.silentPrice(150.0, "points");
        System.out.println("  旧 150.00 元 + 积分（用户 0 分）-> " + oldNoPoints);

        DiscountResult noPoints = PipelineFactory.builder()
                .stage("积分", "points:0.01:1000")
                .build()
                .calculate(OrderContext.builder("150").availablePoints(0).build());
        System.out.println("  新 150.00 元 + 积分（用户 0 分）-> " + Money.yuan(noPoints.finalPrice())
                + "  轨迹：" + noPoints.entries().get(0).decision().describe());
        System.out.println();
        System.out.println("  前两条是「钱不能用 double」：负价与 0.000000000000005 的尾巴；");
        System.out.println("  第三条是「策略不该无条件执行」：不看余额就减，等于平台倒贴");
    }

    // ==================================================================
    // Demo 4：顺序决定结果
    // ==================================================================

    /**
     * 同样的三个折扣，两种顺序，差 2 元。
     */
    @SuppressWarnings("deprecation")
    private static void demo4OrderMatters() {
        section("Demo 4 顺序敏感：满减先还是打折先，差 2 元");
        double a = LegacyOrderService.silentPrice(150.0, "full", "percent", "points");
        double b = LegacyOrderService.silentPrice(150.0, "percent", "full", "points");
        System.out.println("  旧 满减→打折→积分 -> " + a);
        System.out.println("  旧 打折→满减→积分 -> " + b);
        System.out.println("  旧实现里顺序完全由调用方传参决定，没有任何规则可查");

        DiscountPipeline fullFirst = PipelineFactory.builder()
                .stage("满减", "full:100:20")
                .stage("九折", "percent:0.9")
                .stage("积分", "points:0.01:1000")
                .build();
        DiscountPipeline percentFirst = PipelineFactory.builder()
                .stage("九折", "percent:0.9")
                .stage("满减", "full:100:20")
                .stage("积分", "points:0.01:1000")
                .build();
        OrderContext context = OrderContext.builder("150").availablePoints(1000).build();
        System.out.println("  新 满减先 -> " + fullFirst.calculate(context).finalPrice().toPlainString()
                + "  链路：" + fullFirst.describeChain());
        System.out.println("  新 打折先 -> " + percentFirst.calculate(context).finalPrice().toPlainString()
                + "  链路：" + percentFirst.describeChain());
        System.out.println();
        System.out.println("  差 2 元的原因：满减的门槛看「当时的价」。");
        System.out.println("  先减 20 再打折，等于 130 打九折；先打折再减 20，等于 135 减 20。");
        System.out.println("  新实现不改变这个事实，但把顺序变成链上的显式配置 —— 可审查、可对比");
    }

    // ==================================================================
    // Demo 5：装饰器 —— 封顶与追踪
    // ==================================================================

    /**
     * 1000 元的九折本应减 100，但活动规定单步最多减 50。
     */
    private static void demo5CappedDecorator() {
        section("Demo 5 装饰器：单步封顶 50，且封顶不改变策略本身");
        DiscountPolicy bare = PolicyFactory.create("percent:0.9");
        DiscountPolicy capped = new CappedPolicy(bare, Money.of("50"));
        OrderContext context = OrderContext.builder("1000").userLevel(5).build();

        System.out.println("  裸策略  " + DisplayWidth.padRight(bare.name(), 22)
                + " 1000 元 -> 减 " + Money.yuan(bare.discount(Money.of("1000"), context)));
        System.out.println("  封顶后  " + DisplayWidth.padRight(capped.name(), 22)
                + " 1000 元 -> 减 " + Money.yuan(capped.discount(Money.of("1000"), context)));
        System.out.println();

        System.out.println("  追踪装饰器（显式打开，输出可重定向）：");
        DiscountPipeline traced = PipelineFactory.builder()
                .stage("九折", "percent:0.9")
                .cap("50")
                .trace(true)
                .build();
        traced.calculate(context);
        System.out.println();
        System.out.println("  三层的名字：" + capped.name());
        System.out.println("  被装饰者仍是 " + bare.name() + "，算法一行没改");
    }

    // ==================================================================
    // Demo 6：条件 —— 跳过是有原因的
    // ==================================================================

    /**
     * 同一批折扣，两个用户，一个全跳过、一个生效两条。
     */
    private static void demo6RequirementSkip() {
        section("Demo 6 条件：不满 Lv.3、不是新客、不是会员日，各自跳过");
        DiscountPipeline pipeline = PipelineFactory.builder()
                .stage("满减", "full:100:20", Requirement.minLevel(3))
                .stage("新客礼", "full:50:15", Requirement.newCustomerOnly())
                .stage("会员日九折", "percent:0.9", Requirement.memberDayOnly())
                .build();
        System.out.println("  链路：" + pipeline.describeChain());

        OrderContext newbie = OrderContext.builder("200")
                .userLevel(1)
                .availablePoints(0)
                .newCustomer(false)
                .memberDay(false)
                .build();
        System.out.println();
        System.out.println("  用户 A：" + newbie.describe());
        printIndented(pipeline.calculate(newbie).render());

        OrderContext member = OrderContext.builder("200")
                .userLevel(3)
                .availablePoints(0)
                .newCustomer(true)
                .memberDay(false)
                .build();
        System.out.println();
        System.out.println("  用户 B：" + member.describe());
        printIndented(pipeline.calculate(member).render());
        System.out.println();
        System.out.println("  条件从策略里拆出来之后，同一个满减策略可以配任意条件；");
        System.out.println("  旧实现没有条件概念 —— 在链上就一定执行");
    }

    // ==================================================================
    // Demo 7：互斥组 —— 终止整条链
    // ==================================================================

    /**
     * 满减与折扣券互斥，用了满减之后链路终止，积分也不再执行。
     */
    private static void demo7ExclusiveGroup() {
        section("Demo 7 互斥组：满减与折扣券不可同享，后续节点全部终止");
        DiscountPipeline pipeline = PipelineFactory.builder()
                .exclusiveStage("满减", "券", "full:100:20")
                .exclusiveStage("折扣券", "券", "percent:0.85")
                .stage("积分", "points:0.01:1000")
                .build();
        System.out.println("  链路：" + pipeline.describeChain());
        OrderContext context = OrderContext.builder("150")
                .userLevel(3)
                .availablePoints(1000)
                .coupon("SALE2026")
                .build();
        System.out.println("  上下文：" + context.describe());
        System.out.println();
        DiscountResult result = pipeline.calculate(context);
        printIndented(result.render());
        System.out.println();
        System.out.println("  ② 返回的是「终止」而不是「跳过」：链在这里停住，③ 根本没被执行；");
        System.out.println("  旧实现的两个「无条件」让节点既不会跳过、也不会终止 —— 那是管道，不是责任链");
    }

    // ==================================================================
    // Demo 8：结果的两种视图
    // ==================================================================

    /**
     * 同一个结果对象，一份给客服、一份给用户。
     */
    private static void demo8ResultViews() {
        section("Demo 8 结果对象：同一份数据，两种视图");
        DiscountPipeline pipeline = PipelineFactory.builder()
                .stage("满减", "full:100:20")
                .stage("会员九折", "percent:0.9")
                .stage("积分抵扣", "points:0.01:1000")
                .build();
        DiscountResult result = pipeline.calculate(OrderContext.builder("150")
                .userLevel(3)
                .availablePoints(1000)
                .build());

        System.out.println("  给用户看（只有生效项，不显示跳过和原因）：");
        printIndented(result.renderUserFacing());
        System.out.println();
        int[] counts = result.appliedCounts();
        System.out.println("  给客服看：轨迹 " + counts[1] + " 步，生效 " + counts[0]
                + " 步，共减 " + Money.yuan(result.totalReduction())
                + "，占比 " + result.reductionPercent().toPlainString() + "%");
        System.out.println("  给财务看：原价 " + Money.yuan(result.originalPrice())
                + "，应付 " + Money.yuan(result.finalPrice()));
        System.out.println();
        System.out.println("  旧实现只有一个 double 返回值 —— 三种角色都只能看到「107.0」");
    }

    // ==================================================================
    // Demo 9：异常与帮助文本
    // ==================================================================

    /**
     * 四类配置错误，每条消息都能直接指导修复。
     */
    private static void demo9Errors() {
        section("Demo 9 配置错误：错误消息必须告诉你「怎么改」");
        expectFailure("未知折扣码 fullx:100", () -> PolicyFactory.create("fullx:100"));
        expectFailure("参数个数不对 full:100", () -> PolicyFactory.create("full:100"));
        expectFailure("比例越界 percent:1.5", () -> PolicyFactory.create("percent:1.5"));
        expectFailure("额度不小于门槛 full:100:120", () -> PolicyFactory.create("full:100:120"));
        expectFailure("订单金额为负", () -> OrderContext.of("-5"));
        System.out.println();
        System.out.println("  对比旧实现：IllegalArgumentException(\"未知的折扣类型: fullx\")");
        System.out.println("  —— 只说错了，不说有哪些可用、各自什么格式");
    }

    // ==================================================================
    // Demo 10：四个模式的分工
    // ==================================================================

    /**
     * 用一张表收束：每个模式负责什么、拿掉会怎样。
     */
    private static void demo10RoleSummary() {
        section("Demo 10 四个模式各自负责什么");
        String[][] rows = {
                {"策略", "单一折扣的算法", "三个 discount() 方法", "所有折扣挤进一个 switch"},
                {"装饰器", "横切能力：封顶、追踪", "CappedPolicy / TracePolicy", "每个策略都要自己实现封顶"},
                {"责任链", "顺序、跳过、终止", "PolicyHandler / ExclusiveGroupHandler", "顺序与条件写死在调用方"},
                {"工厂", "配置文本 → 对象图", "PolicyFactory / PipelineFactory", "new 散落在每个调用点"},
        };
        System.out.println("  " + DisplayWidth.padRight("模式", 10)
                + DisplayWidth.padRight("负责", 24)
                + DisplayWidth.padRight("在本示例里是", 38) + "拿掉会怎样");
        System.out.println("  " + "-".repeat(96));
        for (String[] row : rows) {
            System.out.println("  " + DisplayWidth.padRight(row[0], 10)
                    + DisplayWidth.padRight(row[1], 24)
                    + DisplayWidth.padRight(row[2], 38) + row[3]);
        }
        System.out.println();
        System.out.println("  一条纪律：四个模式没有一个是「为了用模式而加」的 ——");
        System.out.println("  每个都能回答「不做会缺什么」，答不上来的抽象就该删掉");
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /**
     * 打印段标题。
     *
     * @param title 标题
     */
    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(96));
        System.out.println(title);
        System.out.println("=".repeat(96));
    }

    /**
     * 给多行文本统一加两格缩进后输出。
     *
     * @param text 多行文本
     */
    private static void printIndented(String text) {
        for (String line : text.split("\n", -1)) {
            System.out.println("  " + line);
        }
    }

    /**
     * 执行一个必然失败的动作，打印异常消息（多行消息逐行缩进）。
     *
     * @param label 场景标签
     * @param action 动作
     */
    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  [" + label + "] 没有抛异常 —— 这本身就是 bug");
        } catch (IllegalArgumentException e) {
            System.out.println("  [" + label + "] IllegalArgumentException:");
            for (String line : e.getMessage().split("\n", -1)) {
                System.out.println("      " + line);
            }
        }
    }
}
