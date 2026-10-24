package com.patterns.behavioral.strategy;

import java.math.BigDecimal;

/**
 * 具体策略：不打折（<b>空对象模式 Null Object Pattern</b>）。
 *
 * <p><b>这个类是本次重构新增的，它解决了一个原项目里的真实缺陷。</b>
 *
 * <p>原项目的 {@code PriceCalculator} 是这样处理"没有策略"的：
 * <pre>
 *   public class PriceCalculator {
 *       private DiscountStrategy strategy;          // ← 可能为 null
 *
 *       public void setStrategy(DiscountStrategy strategy) { this.strategy = strategy; }
 *
 *       public double calculatePrice(double originalPrice) {
 *           if (strategy == null) {                  // ← 每次调用都要判空
 *               throw new IllegalStateException("未设置折扣策略");
 *           }
 *           return strategy.calculate(originalPrice);
 *       }
 *   }
 * </pre>
 *
 * <p>这段代码暴露了三个问题：
 * <ol>
 *   <li><b>对象存在"半初始化"状态</b>：{@code new PriceCalculator()} 之后、
 *       {@code setStrategy()} 之前，这个对象是<b>不可用的</b>。
 *       类型系统完全没有表达这一点——编译器认为它是一个合法的 {@code PriceCalculator}。</li>
 *   <li><b>错误在运行时才暴露</b>：忘记调 {@code setStrategy} 的代码能编译通过、能启动，
 *       直到某个用户下单时才抛 {@code IllegalStateException}。
 *       而且这个异常发生在<b>离错误根源最远的地方</b>，排查困难。</li>
 *   <li><b>"不打折"这个合法业务场景无法表达</b>：
 *       现实中大量订单就是没有任何折扣的（新用户、非活动期、不参与活动的商品）。
 *       用 null 表示"不打折"，就意味着每次都要写
 *       {@code if (strategy == null) return price; else return strategy.calculate(price);}——
 *       <b>null 分支里藏着业务逻辑，这是 bug 的温床。</b></li>
 * </ol>
 *
 * <p><b>空对象模式的解法</b>：把"什么都不做"也实现成一个<b>合法的对象</b>。
 * <pre>
 *   DiscountStrategy none = NoDiscountStrategy.INSTANCE;
 *   none.apply(price);   // 返回 price，行为完全正常，不需要任何 null 判断
 * </pre>
 *
 * <p>好处：
 * <ul>
 *   <li>调用方代码变成<b>无条件的一行</b>：{@code strategy.apply(price)}，
 *       没有 if-else，没有 NPE 风险。</li>
 *   <li>"不打折"从"缺失状态"变成了"一种明确的业务选择"，
 *       它可以被打印（{@code name()} 返回"无折扣"）、被记录到订单日志里、被统计。</li>
 *   <li>{@link PriceCalculator} 的字段可以声明为 {@code final}，
 *       构造时就保证非空，<b>"半初始化状态"在类型层面被消灭了</b>。</li>
 * </ul>
 *
 * <p><b>为什么用单例 {@code INSTANCE} 而不是每次 new？</b>
 * 这个类<b>完全没有状态</b>（没有字段），所以所有实例的行为都一模一样。
 * 提供一个静态常量避免无意义的对象创建；同时它天然线程安全（无状态 = 无竞争）。
 * 注意这里<b>没有用 {@code enum} 实现单例</b>——因为空对象的重点是"提供一个默认行为"，
 * 而不是"保证全局唯一"，用 {@code static final} 常量表达更贴切，也不阻碍将来改成可配置的实现。
 *
 * <p><b>空对象模式的适用边界</b>：只有当"什么都不做"是一个<b>合法且安全</b>的默认行为时才用。
 * 反例：支付策略的空对象绝不能是"什么都不做"——那意味着订单被静默标记为已支付却没收到钱。
 * 那种情况应该继续用 {@code null} + 显式判空，或者用 {@code Optional}，
 * <b>让"缺失"这件事在代码里显眼地存在</b>。
 *
 * @author design-patterns-pro
 * @see PriceCalculator 使用空对象后，字段可以声明为 final
 */
public final class NoDiscountStrategy implements DiscountStrategy {

    /** 全局唯一实例。无状态，线程安全。 */
    public static final NoDiscountStrategy INSTANCE = new NoDiscountStrategy();

    /** 用 {@link #INSTANCE} 即可，一般不需要 new。 */
    public NoDiscountStrategy() {
    }

    @Override
    public String name() {
        return "无折扣（原价）";
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        // 注意这里仍然调用 normalize：
        // 即使不打折，也要保证返回值的 scale 是 2。
        // 否则传入 "99.9" 会原样返回 "99.9"，而其他策略返回 "108.00"，
        // 调用方拼接字符串时会出现 "￥99.9" 和 "￥108.00" 混排，界面不齐。
        return DiscountStrategy.normalize(originalPrice);
    }

    @Override
    public String toString() {
        return "NoDiscountStrategy[无折扣]";
    }
}
