package com.patterns.behavioral.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 抽象策略：定义"如何计算折后价"这一族可互换的算法。
 *
 * <p><b>为什么返回类型必须是 {@link BigDecimal} 而不是 {@code double}？</b>
 * 这是本次重构最重要的一处修正。原项目用的是 {@code double}：
 * <pre>
 *   double calculate(double originalPrice);   // 原项目
 * </pre>
 * 而 {@code double} 是 IEEE 754 二进制浮点数，<b>无法精确表示大多数十进制小数</b>。
 *
 * <p><b>以下是本项目在 JDK 25 下的真实实测结果</b>（不是从书上抄的例子）：
 * <pre>
 *   99.9 * 0.7                  = 69.92999999999999      // 应为 69.93，打七折算错
 *   58.8 * 0.75                 = 44.099999999999994     // 应为 44.10，七五折算错
 *   0.29 * 100                  = 28.999999999999996     // 元转分时，转成 long 变成 28 分，少了 1 分
 *   0.07 * 100                  = 7.000000000000001      // 多了，转成 long 才是对的 7
 *   Math.round(1.005 * 100)/100 = 1.0                    // 四舍五入错！1.005 应进位到 1.01
 *   3.0 - 2.1                   = 0.8999999999999999     // 减法也不准
 *   0.1 + 0.2                   = 0.30000000000000004    // 经典例子
 *   10 次 0.1 累加               = 0.9999999999999999     // 不是 1.0
 * </pre>
 *
 * <p><b>特别要看第 5 行</b>：{@code Math.round(1.005 * 100) / 100.0} 结果是 {@code 1.0}，
 * 但按四舍五入规则应该是 {@code 1.01}。原因是 {@code 1.005} 在 double 里
 * 实际存的是 {@code 1.00499999999999989...}，本来就小于 1.005，所以舍去而不是进位。
 * <b>这不是“精度差一点点”的学术问题，是真真实实地少收了客户一分钱。</b>
 *
 * <p>后果在真实系统里是灾难性的：
 * <ul>
 *   <li>用户看到的订单金额是 {@code 69.92999999999999} 元；</li>
 *   <li>{@code if (balance == 0.0)} 这种判断永远为 false，对账永远对不平；</li>
 *   <li>“元转分”时 {@code (long)(price * 100)} 会静默丢掉 1 分（因为 28.999... 截断成 28）；</li>
 *   <li>每天百万笔订单，每笔差 0.01 元，一天就是一万元的账目差异；</li>
 *   <li>财务审计不通过——<b>金额计算用浮点数在多数公司的编码规范里是直接判定为 Bug 的</b>。</li>
 * </ul>
 *
 * <p><b>一个诚实的补充</b>：原项目用的恰好是 {@code 120.0 * 0.9}，
 * 而这个算式实测结果是<b>精确的</b> {@code 108.0}（因为 0.9 和 120 的二进制误差恰好相消）。
 * 所以原项目的 Demo 跑起来看不出任何问题——<b>这正是浮点数 bug 最阴险的地方</b>：
 * 测试用例里的那些数字刚好能算对，一上生产遇到真实价格就出错。
 * 不能靠“测了几个数字都对”来证明浮点数安全，必须从类型上根治。
 *
 * <p>{@link BigDecimal} 用"未缩放整数 + 十进制指数"表示数值（例如 108.00 存成 10800 × 10⁻²），
 * 十进制运算完全精确。<b>凡是钱，一律用 BigDecimal（或以"分"为单位的 long）。</b>
 *
 * <p><b>另一个陷阱</b>：构造 BigDecimal 时不要用 {@code new BigDecimal(0.1)}，
 * 它会把 double 的误差一起带进来（得到 {@code 0.1000000000000000055511151231257827...}）。
 * 正确写法是 {@code new BigDecimal("0.1")}（字符串）或 {@code BigDecimal.valueOf(0.1)}。
 * 本项目全部使用字符串构造。
 *
 * <p><b>为什么需要 {@link #name()}？</b>
 * 原项目的 {@code Client} 是这样写的：
 * <pre>
 *   calculator.setStrategy(new FullReductionStrategy());
 *   System.out.println("【满减策略】最终价格：" + calculator.calculatePrice(price));
 * </pre>
 * 那个 {@code "【满减策略】"} 是<b>调用方硬编码的字符串</b>，和策略对象本身没有任何关联。
 * 后果：新增一个策略时，调用方要自己编一个名字；同一个策略在不同调用点可能被写成不同的名字；
 * 想遍历所有策略打印对比表，根本拿不到名字。
 *
 * <p>让策略自己提供 {@code name()}，名字就成了策略的<b>内在属性</b>，
 * 于是可以写出这样的通用代码（见 {@code Client.demo3}）：
 * <pre>
 *   for (DiscountStrategy s : strategies) {
 *       System.out.println(s.name() + " → " + s.apply(price));
 *   }
 * </pre>
 * 一行都不用改就能展示任意多个策略。<b>这就是"把变化的部分封装进对象"的意义。</b>
 *
 * <p><b>为什么这个接口不适合用 lambda 实现？</b>
 * 因为它有<b>两个</b>抽象方法（{@code name()} 和 {@code apply()}），不是函数式接口。
 * 这是一个真实的取舍：
 * <ul>
 *   <li>只留 {@code apply()} → 可以写 {@code price -> price.multiply(RATE)}，极简，但日志里不知道是哪个策略；</li>
 *   <li>加上 {@code name()} → 可观测性大幅提升，但必须写具名类。</li>
 * </ul>
 * 本项目选择后者，因为"能在日志和界面上说清楚用了哪种折扣"在电商场景里是硬需求。
 * 折中方案见 {@link SimpleDiscountStrategy}——用 record 包装一个 lambda，两全其美。
 *
 * @author design-patterns-pro
 * @see PriceCalculator 策略的使用者（Context）
 */
public interface DiscountStrategy {

    /**
     * 策略的可读名称，用于日志、界面展示和调试。
     *
     * <p>建议直接写成"人类能看懂的规则描述"，例如 {@code "满减（满 100 减 20）"}，
     * 而不是 {@code "FullReductionStrategy"} 这种类名——前者对运营人员有意义，后者只对开发者有意义。
     *
     * @return 策略名称，不应为 null 或空串
     */
    String name();

    /**
     * 计算折后价格。
     *
     * <p><b>契约</b>：
     * <ul>
     *   <li>实现必须<b>无副作用</b>——不能修改入参、不能写数据库、不能发请求。
     *       策略是"纯算法"，这一点让它极易测试（给定输入必得确定输出）。</li>
     *   <li>返回值<b>不应为负数</b>。折扣最多把价格减到 0，不能倒找钱给用户。
     *       各实现类负责用 {@code max(ZERO)} 兜底。</li>
     *   <li>返回值应保留 2 位小数（{@link java.math.RoundingMode#HALF_UP}），
     *       因为人民币最小单位是分。</li>
     * </ul>
     *
     * @param originalPrice 原价，必须非 null 且非负
     * @return 折后价格，保留 2 位小数
     * @throws IllegalArgumentException 如果 originalPrice 为 null 或为负数
     */
    BigDecimal apply(BigDecimal originalPrice);

    // ==================================================================
    // 下面两个静态工具方法：把"所有策略都必须遵守的契约"集中在一处
    // ==================================================================

    /**
     * 校验入参价格是否合法。所有具体策略在 {@code apply()} 开头调用它。
     *
     * <p><b>为什么放在接口里做静态方法，而不是抽一个 {@code AbstractDiscountStrategy} 基类？</b>
     * 因为各策略之间<b>只有这两行工具代码是共同的，没有任何共同状态</b>。
     * 为两行无状态的工具方法引入一层继承，代价是：
     * <ul>
     *   <li>读者要先理解基类才能理解子类，阅读路径变长；</li>
     *   <li>子类被迫绑定到单一继承链上，将来如果想用 record 实现策略就做不到了
     *       （record 不能 extends 类）；</li>
     *   <li>基类很容易随着时间演变成"什么都往里塞"的垃圾桶。</li>
     * </ul>
     * 接口的 {@code static} 方法（Java 8+）正好适合放"与契约相关的工具"：
     * 它属于这个抽象，但不强制任何继承关系。<b>能用组合/工具解决的，不要用继承。</b>
     *
     * @param price 待校验的价格
     * @throws IllegalArgumentException 如果为 null 或负数
     */
    static void requireValidPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("原价不能为 null");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("原价不能为负数，实际传入：" + price);
        }
    }

    /**
     * 把计算结果规范成合法的金额：保留 2 位小数（四舍五入），且不低于 0。
     *
     * <p><b>两个动作都不能省</b>：
     * <ol>
     *   <li><b>保留 2 位</b>：{@code BigDecimal.multiply} 会保留两个因子的小数位数之和。
     *       例如 {@code 99.90 × 0.70 = 69.9300}（4 位小数），不处理就会在界面上显示成
     *       {@code ￥69.9300}。必须用 {@code setScale(2, HALF_UP)} 收敛到"分"。</li>
     *   <li><b>不低于 0</b>：满减策略在"满 30 减 50"这种配置错误下会算出负数，
     *       意味着"倒找钱给用户"。用 {@code max(ZERO)} 兜底，把配置错误的影响限制在"免费"，
     *       而不是变成资金流出事故。</li>
     * </ol>
     *
     * <p><b>为什么用 HALF_UP 而不是 HALF_EVEN？</b>
     * {@code HALF_UP} 是大众理解的"四舍五入"（1.005 → 1.01）；
     * {@code HALF_EVEN} 是"银行家舍入"（1.005 → 1.00，1.015 → 1.02），
     * 统计学上偏差更小，但<b>普通用户会认为它算错了</b>。
     * 电商面向消费者，用 HALF_UP；金融清算内部对账，才考虑 HALF_EVEN。
     * 关键是<b>全公司统一一种并写进规范</b>，最怕的是不同模块用不同舍入模式。
     *
     * @param raw 策略算出的原始结果
     * @return 规范后的金额
     */
    static BigDecimal normalize(BigDecimal raw) {
        return raw.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
