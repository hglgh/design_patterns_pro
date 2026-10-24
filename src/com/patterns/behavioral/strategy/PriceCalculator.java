package com.patterns.behavioral.strategy;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 上下文（Context）：持有一个策略，把"算价"这件事委托给它。
 *
 * <p><b>Context 的职责边界——这是最容易搞错的地方。</b>
 * Context <b>只负责三件事</b>：
 * <ol>
 *   <li>持有策略引用；</li>
 *   <li>把调用转发给策略；</li>
 *   <li>（可选）提供策略需要的额外数据，或包装策略的结果。</li>
 * </ol>
 * Context <b>绝不负责</b>：判断"该用哪个策略"、实现任何折扣算法。
 * 一旦 Context 里出现 {@code if (isVip) ... else if (amount > 100) ...}，
 * 策略模式就白用了——那些 if-else 正是策略模式要消灭的东西。
 * 策略的选择应该由<b>调用方</b>或<b>专门的工厂</b>决定（见 {@link DiscountStrategyFactory}）。
 *
 * <p><b>本次重构对 Context 的三处改动</b>：
 *
 * <p><b>① setter 注入 → 构造器注入。</b>原项目：
 * <pre>
 *   private DiscountStrategy strategy;                    // 可变、可能为 null
 *   public void setStrategy(DiscountStrategy s) {...}     // 分两步初始化
 *   public double calculatePrice(double p) {
 *       if (strategy == null) throw new IllegalStateException("未设置折扣策略");
 *       ...
 *   }
 * </pre>
 * 问题：对象存在"已 new 但不可用"的中间状态，错误要到运行时才暴露（详见 {@link NoDiscountStrategy} 的说明）。
 *
 * <p>改成构造器注入后：
 * <pre>
 *   private final DiscountStrategy strategy;              // 不可变、保证非 null
 *   public PriceCalculator(DiscountStrategy s) {
 *       this.strategy = Objects.requireNonNull(s, "...");  // 构造时就失败
 *   }
 * </pre>
 * <b>「一个对象要么被正确创建，要么根本创建不出来」</b>——这叫构造器不变式（constructor invariant）。
 * 忘记传策略的代码<b>编译不过</b>，而不是运行到一半才崩。
 *
 * <p><b>② 新增 {@link #savedAmount(BigDecimal)}。</b>
 * 真实的电商界面上，"折后价"旁边一定有一行"已优惠 ￥20.00"。
 * 这个计算不属于任何具体策略（它是"原价 - 折后价"，与策略无关），
 * 放在 Context 里正合适——<b>这就是上面说的"包装策略的结果"。</b>
 *
 * <p><b>③ 新增 {@link #withStrategy(DiscountStrategy)} 替代 setter。</b>
 * 需要切换策略时（比如用户在结算页选了另一张券），
 * 返回一个<b>新的</b> Context 而不是修改现有的。好处：
 * <ul>
 *   <li>线程安全：不可变对象可以自由地在多线程间共享，不需要任何同步。</li>
 *   <li>可追溯：旧对象还在，能同时算出"用券 A 多少钱、用券 B 多少钱"给用户对比。
 *       如果用 setter，切换后旧策略就丢了，做不出对比界面。</li>
 * </ul>
 *
 * <p><b>Context 是不是多余的？直接用策略不行吗？</b>
 * 确实，{@code strategy.apply(price)} 一行就能算价，Context 看起来只是转发。
 * 它存在的价值在于：
 * <ul>
 *   <li>给策略提供一个<b>稳定的挂载点</b>。真实系统里 Context 还会持有订单号、用户 ID、
 *       商品列表等信息，策略需要时可以从 Context 拿（把 Context 传进 {@code apply}）。</li>
 *   <li>集中放置<b>与具体算法无关的公共逻辑</b>，比如 {@link #savedAmount}、日志、埋点。</li>
 *   <li>让调用方面对一个<b>业务语义的名字</b>（{@code PriceCalculator}）而不是一个抽象接口。</li>
 * </ul>
 * 本项目的 Context 刻意保持精简，因为示例的重点是"策略可替换"，
 * <b>不是</b>"Context 有多少功能"。如果 Context 膨胀到几百行，说明职责划分出了问题。
 *
 * @author design-patterns-pro
 * @see DiscountStrategy 被委托的抽象策略
 * @see DiscountStrategyFactory 负责"选哪个策略"，与 Context 职责分离
 */
public class PriceCalculator {

    /**
     * 当前使用的折扣策略。
     *
     * <p>{@code final} + 构造器 {@code requireNonNull} = <b>这个字段永远不可能为 null</b>。
     * 于是 {@link #calculatePrice} 里不再需要任何判空代码，
     * 原项目那个 {@code if (strategy == null) throw ...} 被彻底消除了。
     */
    private final DiscountStrategy strategy;

    /**
     * @param strategy 要使用的折扣策略，不能为 null。
     *                 如果业务上确实"没有折扣"，请传 {@link NoDiscountStrategy#INSTANCE}
     *                 而不是 null——这样"无折扣"是一个明确的策略，而不是一种缺失状态。
     * @throws NullPointerException 如果 strategy 为 null
     */
    public PriceCalculator(DiscountStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy,
                "折扣策略不能为 null；若表示'不打折'请传 NoDiscountStrategy.INSTANCE");
    }

    /**
     * 计算折后价格。
     *
     * <p>就一行——因为所有校验和规范化都在策略内部完成了。
     * Context 不重复做这些事，否则就是职责泄漏。
     *
     * @param originalPrice 原价
     * @return 折后价，保留 2 位小数，非负
     */
    public BigDecimal calculatePrice(BigDecimal originalPrice) {
        return strategy.apply(originalPrice);
    }

    /**
     * 计算这一单省了多少钱。
     *
     * <p><b>为什么这个方法放在 Context 而不是策略里？</b>
     * 因为它的实现<b>与具体策略无关</b>：不管是满减、打折还是 VIP，
     * 省下的钱永远是"原价 - 折后价"。
     * 如果放进 {@link DiscountStrategy}，每个策略都要重复实现一遍，
     * 而且新增策略时很容易忘记实现（或者实现错了）。
     *
     * <p>这是判断"逻辑该放哪"的通用准则：
     * <b>随策略变化而变化的 → 放策略；所有策略都一样的 → 放 Context。</b>
     *
     * @param originalPrice 原价
     * @return 优惠金额，非负
     */
    public BigDecimal savedAmount(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        BigDecimal saved = originalPrice.subtract(calculatePrice(originalPrice));
        // 理论上不会为负（策略保证折后价 ≤ 原价），但仍做兜底：
        // 万一某个自定义策略写错了算出"折后价高于原价"，
        // 界面会显示"已优惠 ￥-5.00"，这比抛异常更容易被用户看到并投诉。
        return saved.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 返回一个使用新策略的 <b>新</b> Context，当前对象保持不变。
     *
     * <p>这是 {@code setStrategy()} 的不可变替代品。对比：
     * <pre>
     *   // 原项目（可变）：切换后旧策略丢失，且非线程安全
     *   calculator.setStrategy(newVip);
     *
     *   // 重构后（不可变）：可以同时持有两个，做价格对比
     *   PriceCalculator withCouponA = calculator.withStrategy(couponA);
     *   PriceCalculator withCouponB = calculator.withStrategy(couponB);
     *   // 给用户展示："用 A 券 108.00 元，用 B 券 100.00 元，B 更划算"
     * </pre>
     *
     * @param newStrategy 新策略
     * @return 新的 Context 实例
     */
    public PriceCalculator withStrategy(DiscountStrategy newStrategy) {
        return new PriceCalculator(newStrategy);
    }

    /** 当前使用的策略，用于日志和界面展示。 */
    public DiscountStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return "PriceCalculator[strategy=" + strategy.name() + "]";
    }
}
