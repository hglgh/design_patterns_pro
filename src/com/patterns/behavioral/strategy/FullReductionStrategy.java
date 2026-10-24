package com.patterns.behavioral.strategy;

import java.math.BigDecimal;

/**
 * 具体策略：满减。例如"满 100 减 20"、"满 300 减 80"。
 *
 * <p><b>本次重构最重要的改动：把 100 和 20 从硬编码变成构造参数。</b>
 *
 * <p>原项目是这样的：
 * <pre>
 *   public class FullReductionStrategy implements DiscountStrategy {
 *       public double calculate(double originalPrice) {
 *           if (originalPrice &gt;= 100) {   // ← 魔数
 *               return originalPrice - 20;  // ← 魔数
 *           }
 *           return originalPrice;
 *         }
 *   }
 * </pre>
 *
 * <p>硬编码的后果非常直接：<b>运营每改一次活动规则，就要改一次 Java 代码、重新编译、重新发版。</b>
 * 电商的满减档位是<b>每周都在变</b>的东西：
 * <ul>
 *   <li>日常："满 100 减 20"</li>
 *   <li>618："满 300 减 80"</li>
 *   <li>双十一："满 200 减 50，满 1000 减 300"（多档）</li>
 *   <li>清仓："满 50 减 30"</li>
 * </ul>
 * 如果每种档位都要写一个类，一年下来就是几十个几乎一样的类（<b>类爆炸</b>）；
 * 如果只改一个类里的数字，那所有历史订单的计价逻辑就被篡改了（<b>无法复现历史价格</b>，
 * 这在退款、对账、审计时是致命的）。
 *
 * <p>参数化之后，<b>一个类支持无限种满减规则</b>，而且每个订单可以记住自己用的是哪套参数：
 * <pre>
 *   new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20"))   // 日常
 *   new FullReductionStrategy(new BigDecimal("300"), new BigDecimal("80"))   // 618
 * </pre>
 * 这两个对象可以<b>同时存在</b>，互不干扰。这才是策略模式真正的价值：
 * <b>算法族不是"满减 / 打折 / VIP"这么三种，而是"任意参数组合"这么无限种。</b>
 *
 * @author design-patterns-pro
 */
public class FullReductionStrategy implements DiscountStrategy {

    /** 触发满减的门槛金额，例如 100 表示"满 100"。 */
    private final BigDecimal threshold;

    /** 达到门槛后减免的金额，例如 20 表示"减 20"。 */
    private final BigDecimal reduction;

    /**
     * @param threshold 满减门槛，必须为正数
     * @param reduction 减免金额，必须为正数
     * @throws IllegalArgumentException 参数为 null 或非正数时抛出
     */
    public FullReductionStrategy(BigDecimal threshold, BigDecimal reduction) {
        // 快速失败：把配置错误暴露在"创建策略"这一刻，而不是等到用户下单时才算出诡异价格。
        // 特别是 reduction > threshold 的情况（"满 30 减 50"），如果不校验，
        // apply() 会算出负数，虽然有 normalize() 兜底成 0，但那种"静默免费"的 bug 极难排查。
        if (threshold == null || threshold.signum() <= 0) {
            throw new IllegalArgumentException("满减门槛必须为正数，实际传入：" + threshold);
        }
        if (reduction == null || reduction.signum() <= 0) {
            throw new IllegalArgumentException("减免金额必须为正数，实际传入：" + reduction);
        }
        if (reduction.compareTo(threshold) > 0) {
            throw new IllegalArgumentException(
                    "减免金额(" + reduction + ")不应大于门槛(" + threshold + ")，否则订单会变成免费");
        }
        this.threshold = threshold;
        this.reduction = reduction;
    }

    @Override
    public String name() {
        // 名字由参数动态生成，所以"满 300 减 80"和"满 100 减 20"在日志里能被区分开。
        // 原项目做不到这一点——所有满减都叫"满减策略"。
        return "满减（满 " + threshold.stripTrailingZeros().toPlainString()
                + " 减 " + reduction.stripTrailingZeros().toPlainString() + "）";
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);

        // compareTo 而不是 equals！这是 BigDecimal 的一个经典坑：
        //   new BigDecimal("100").equals(new BigDecimal("100.00"))   → false（scale 不同）
        //   new BigDecimal("100").compareTo(new BigDecimal("100.00")) → 0（数值相同）
        // 如果用 equals，传入 100.00 的订单会被判定为"未达门槛"而不打折。
        if (originalPrice.compareTo(threshold) >= 0) {
            return DiscountStrategy.normalize(originalPrice.subtract(reduction));
        }
        // 未达门槛：原价返回，但仍需 normalize，保证返回值的 scale 统一为 2。
        // 否则调用方拿到 scale=1 的 "99.9" 和 scale=2 的 "108.00" 混在一起，
        // 界面显示会不一致（一个显示 ￥99.9，一个显示 ￥108.00）。
        return DiscountStrategy.normalize(originalPrice);
    }

    @Override
    public String toString() {
        return "FullReductionStrategy[" + name() + "]";
    }
}
