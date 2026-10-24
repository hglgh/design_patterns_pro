package com.patterns.behavioral.strategy;

import com.patterns.util.DisplayWidth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 策略工厂：回答策略模式里<b>最常被忽略的问题——「谁来选策略？」</b>
 *
 * <p><b>为什么这个类是必需的？</b>
 *
 * <p>很多策略模式的示例（包括原项目）到"客户端 new 一个具体策略传给 Context"就结束了。
 * 但这留下了一个致命空白：<b>"用户等级 → 该用哪个策略"这条业务规则写在哪里？</b>
 *
 * <p>如果没有工厂，这条规则只能写在客户端：
 * <pre>
 *   // 下单接口里
 *   DiscountStrategy s;
 *   if (user.getLevel() == UserLevel.VIP) {
 *       s = new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200"));
 *   } else if (user.getLevel() == UserLevel.GOLD) {
 *       s = new PercentageDiscountStrategy(new BigDecimal("0.90"));
 *   } else if (...) { ... }
 *
 *   // 购物车接口里 —— 同样的 if-else 又抄了一遍
 *   // 订单预览接口里 —— 再抄一遍
 *   // 促销活动页里 —— 再抄一遍
 * </pre>
 *
 * <p>这就完全违背了策略模式的初衷。<b>策略模式消灭的是"算法实现"里的 if-else，
 * 但如果"算法选择"处又长出一片 if-else，而且还被复制了 N 份，那模式只成功了一半。</b>
 *
 * <p>把选择逻辑收进工厂之后：
 * <ul>
 *   <li>规则<b>只有一处</b>，改一次全局生效；</li>
 *   <li>客户端只认识 {@code UserLevel} 和 {@code DiscountStrategy} 两个抽象，
 *       <b>完全不需要知道有哪些具体策略类</b>（这才是真正的解耦）；</li>
 *   <li>客户端无法"越权"——它拿不到构造 VIP 策略的入口，只能通过工厂按等级取。</li>
 * </ul>
 *
 * <p><b>为什么 {@link #forLevel} 用 switch 表达式而不是 Map？</b>
 * 两种写法都对，选择依据是：
 * <table border="1">
 *   <caption>switch vs Map</caption>
 *   <tr><th></th><th>switch 表达式</th><th>Map 注册表</th></tr>
 *   <tr><td>新增枚举值忘了处理</td><td><b>编译报错</b>（enum switch 要求穷尽）</td><td>运行时才发现（get 返回 null）</td></tr>
 *   <tr><td>策略创建需要参数/条件</td><td>直接写代码，灵活</td><td>只能存固定实例</td></tr>
 *   <tr><td>规则数量很大（几十上百）</td><td>switch 会很长</td><td>更清晰</td></tr>
 *   <tr><td>规则来自数据库/配置</td><td>做不到</td><td>天然适合</td></tr>
 * </table>
 * 本例只有 4 个等级、且 VIP 需要额外参数（封顶金额），所以用 switch 表达式。
 * <b>「编译器帮我检查有没有漏」这一点在教学和小型系统里价值极高。</b>
 *
 * <p><b>Spring 项目里怎么写？</b>
 * 通常是让所有策略类加 {@code @Component}，然后注入 {@code Map<String, DiscountStrategy>}
 * 或 {@code List<DiscountStrategy>}，Spring 会自动按 bean 名收集：
 * <pre>
 *   &#64;Service
 *   public class DiscountStrategyFactory {
 *       private final Map&lt;String, DiscountStrategy&gt; registry;   // Spring 自动注入所有实现
 *       public DiscountStrategyFactory(Map&lt;String, DiscountStrategy&gt; registry) {
 *           this.registry = registry;
 *       }
 *   }
 * </pre>
 * 这就是"策略模式 + Spring 依赖注入"的标准写法，新增策略只需加一个 {@code @Component} 类，
 * 工厂代码<b>一行都不用改</b>。本项目不引入 Spring，用 switch 表达同样的意图。
 *
 * @author design-patterns-pro
 * @see UserLevel 承载"等级 → 折扣配置"的映射数据
 */
public final class DiscountStrategyFactory {

    /** 对比表里"策略名称"列的显示宽度。 */
    static final int NAME_WIDTH = 36;

    /** 对比表里金额列的显示宽度。 */
    static final int PRICE_WIDTH = 11;

    /** 工具类，禁止实例化。 */
    private DiscountStrategyFactory() {
        throw new AssertionError("工厂类不允许实例化");
    }

    /**
     * 根据会员等级创建对应的折扣策略。
     *
     * <p><b>为什么每次调用都 new 一个新对象，而不缓存？</b>
     * 因为这些策略对象都很小（1~2 个 BigDecimal 字段），创建成本远低于一次 HashMap 查找。
     * 而且策略是<b>不可变</b>的，即使 new 出多个也不会有状态冲突。
     * 如果策略创建成本真的高（比如要读配置文件、要初始化规则引擎），
     * 那就该在这里加缓存，或者用 {@code EnumMap<UserLevel, DiscountStrategy>} 预建好。
     * <b>不要为了"看起来高效"而提前优化。</b>
     *
     * @param level 会员等级
     * @return 对应的折扣策略，永远不会返回 null（无折扣时返回 {@link NoDiscountStrategy}）
     */
    public static DiscountStrategy forLevel(UserLevel level) {
        Objects.requireNonNull(level, "会员等级不能为 null");

        // switch 表达式（Java 14+ 正式特性）：
        //   ① 有返回值，可以直接 return，不需要临时变量
        //   ② 箭头语法不会有 case 穿透（fall-through）的坑
        //   ③ 对 enum 做穷尽性检查——将来给 UserLevel 加一个新等级，
        //      这里会【编译报错】提醒你必须处理，而 Map 方案只会静默返回 null
        //   ④ 不需要 default 分支（正因为穷尽了），所以"忘记处理"不会被 default 掩盖
        return switch (level) {
            case NORMAL -> NoDiscountStrategy.INSTANCE;
            case SILVER, GOLD -> new PercentageDiscountStrategy(level.getRate());
            case VIP -> new VipDiscountStrategy(level.getRate(), level.getMaxDiscount());
        };
    }

    /**
     * 当前正在进行的所有促销活动策略。
     *
     * <p><b>为什么提供这个方法？</b>
     * 因为它让"新增一个促销活动"这件事变得极其简单——
     * 只要往这个列表里加一行，{@link #bestOf} 和 {@code Client} 的对比表
     * 就会<b>自动</b>把新活动纳入计算，不需要改任何其他代码。
     *
     * <p>真实系统里这个列表来自数据库的"活动配置表"，
     * 并且要按活动时间过滤（{@code now BETWEEN start_time AND end_time}）。
     * 结构是一样的：<b>一个策略列表 + 一个选择算法</b>。
     *
     * @return 不可变的策略列表
     */
    public static List<DiscountStrategy> currentPromotions() {
        return List.of(
                new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")),
                new FullReductionStrategy(new BigDecimal("300"), new BigDecimal("80")),
                new PercentageDiscountStrategy(new BigDecimal("0.90")),
                new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200")),
                new SimpleDiscountStrategy("新人首单立减 15",
                        p -> p.subtract(new BigDecimal("15")))
        );
    }

    /**
     * 从候选策略中选出<b>对用户最有利</b>（折后价最低）的那一个。
     *
     * <p><b>这是策略模式最有说服力的应用</b>，也是真实电商的核心功能
     * ——结算页那句"已为你选择最优惠方案，共省 ￥80.00"。
     *
     * <p>它能写得这么简洁，完全依赖于一个前提：
     * <b>所有策略都实现同一个接口，所以可以被放进 List 里被同等对待。</b>
     * <pre>
     *   for (DiscountStrategy s : candidates) {
     *       BigDecimal price = s.apply(originalPrice);   // ← 多态调用，不关心具体是哪个策略
     *       if (price.compareTo(bestPrice) &lt; 0) { ... }
     *   }
     * </pre>
     *
     * <p><b>如果没有策略模式，同样的功能要怎么写？</b>
     * <pre>
     *   double p1 = fullReduction(originalPrice);        // 四个独立的方法
     *   double p2 = percentageOff(originalPrice, 0.9);
     *   double p3 = vipDiscount(originalPrice, 0.8, 200);
     *   double p4 = newCustomerDiscount(originalPrice);
     *   double best = Math.min(Math.min(p1, p2), Math.min(p3, p4));
     *   // 而且 best 只是一个数字，你不知道它是哪个活动算出来的，没法告诉用户"你用了哪张券"
     * </pre>
     * 新增第 5 个活动，就要改这段代码（加一个变量、加一层 Math.min）。
     * 而策略版本<b>只需要往 currentPromotions() 里加一行</b>，bestOf 一个字都不用动。
     * <b>这就是"对扩展开放、对修改关闭"的实际含义。</b>
     *
     * @param candidates     候选策略列表，不能为空
     * @param originalPrice  原价
     * @return 折后价最低的策略
     * @throws IllegalArgumentException 候选列表为空时抛出
     */
    public static DiscountStrategy bestOf(List<DiscountStrategy> candidates, BigDecimal originalPrice) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("候选策略列表不能为空");
        }
        DiscountStrategy.requireValidPrice(originalPrice);

        DiscountStrategy best = null;
        BigDecimal bestPrice = null;

        for (DiscountStrategy candidate : candidates) {
            BigDecimal price = candidate.apply(originalPrice);
            // compareTo < 0 表示"更便宜"。注意 BigDecimal 不能用 < 运算符，必须用 compareTo。
            if (bestPrice == null || price.compareTo(bestPrice) < 0) {
                bestPrice = price;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 打印所有候选策略在指定价格下的对比表。
     *
     * <p>纯教学用途：让"策略可替换"这件事变成一张能直接看到的表。
     * 生产代码里这类展示逻辑应该放在视图层，不该混进工厂。
     *
     * <p><b>为什么用 {@link DisplayWidth} 而不是 {@code printf("%-36s")}？</b>
     * 因为策略名里混着中文和数字，而<b>一个汉字在终端里占两列</b>。
     * {@code printf} 按字符个数补齐，会让金额列参差不齐。
     * 详见 {@link DisplayWidth} 的类注释。
     *
     * @param candidates    候选策略
     * @param originalPrice 原价
     */
    public static void printComparison(List<DiscountStrategy> candidates, BigDecimal originalPrice) {
        System.out.println("      "
                + DisplayWidth.padRight("策略名称", NAME_WIDTH)
                + DisplayWidth.padLeft("折后价", PRICE_WIDTH) + "  "
                + DisplayWidth.padLeft("已优惠", PRICE_WIDTH));
        System.out.println("      "
                + DisplayWidth.line('-', NAME_WIDTH + PRICE_WIDTH * 2 + 2));

        for (DiscountStrategy s : candidates) {
            PriceCalculator calc = new PriceCalculator(s);
            System.out.println("      "
                    + DisplayWidth.padRight(s.name(), NAME_WIDTH)
                    + DisplayWidth.padLeft("￥" + calc.calculatePrice(originalPrice).toPlainString(), PRICE_WIDTH)
                    + "  "
                    + DisplayWidth.padLeft("￥" + calc.savedAmount(originalPrice).toPlainString(), PRICE_WIDTH));
        }
    }
}
