package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 积分抵扣策略：<b>把用户手里的积分换成钱</b>。
 *
 * <p>三个参数：每分抵多少钱、单笔最多用多少分、以及从上下文里读到的可用积分。
 * 实际抵扣分两道上限：<b>笔数上限</b>（{@code maxPoints}）和
 * <b>余额上限</b>（{@code context.availablePoints()}），取较小者。</p>
 *
 * <p><b>为什么这个策略只读积分、不扣积分？</b></p>
 * 这是整个组合示例里最重要的一条边界。折扣计算是「试算」：
 * 用户可能在支付页反复切换优惠券、反复点「重新计算」，
 * 每次都会把链跑一遍。如果策略在 {@code discount} 里扣积分，那么：</p>
 * <ol>
 *   <li>用户刷新三次页面，积分被扣三次；</li>
 *   <li>链在第四个节点抛异常，前三个节点的副作用已经发生，事务无法回滚内存中的变量；</li>
 *   <li>「预览最终价」这个纯查询动作变成了写操作，接口再也不能做缓存。</li>
 * </ol>
 * <p>所以扣积分必须由订单服务在<b>确认下单</b>时统一执行一次。
 * 策略返回的只是「建议抵扣多少」，是一个纯粹的计算结果。</p>
 *
 * <p><b>为什么「每分抵多少」要作为参数，而不是写死一个常数？</b></p>
 * 原项目的 {@code PointsDeductionStrategy} 直接 {@code return originalPrice - 10;}
 * —— 它连「用多少积分」都不问，固定减 10 元。这意味着无论用户有 0 分还是 10000 分，
 * 都减 10 元；而用户实际有 3 分时，平台倒贴了 7 元。
 * 参数化之后，至少能表达「100 分抵 1 元」「单笔最多 500 分」这类真实规则。</p>
 *
 * <p><b>为什么用 {@link BigDecimal} 表示「每分抵多少」而不是 int 分？</b></p>
 * 「100 分抵 1 元」写成 {@code 0.01} 元/分，语义清楚。
 * 用「分」为单位的整数也能表达（1 分/分），但那样每个语义都要多一次心算。
 * 本项目在金额一律用 {@code BigDecimal} 的前提下，参数也用同一类型，
 * 避免在构造器里做单位转换 —— 单位转换是 bug 的高发区。</p>
 *
 * @param name         策略名称，例如 {@code "积分抵扣"}
 * @param yuanPerPoint 每 1 积分抵多少钱，必须为正且不超过 1 元
 * @param maxPoints    单笔最多可用积分，必须为非负
 * @author design-patterns-pro
 */
public record PointsDeductionPolicy(String name, BigDecimal yuanPerPoint, int maxPoints)
        implements DiscountPolicy {

    /**
     * 校验参数。
     *
     * @throws IllegalArgumentException 名称为空、每分价值越界、笔数上限为负
     */
    public PointsDeductionPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("积分策略必须有名称");
        }
        if (yuanPerPoint == null || yuanPerPoint.signum() <= 0) {
            throw new IllegalArgumentException("每分抵扣金额必须为正：" + yuanPerPoint);
        }
        if (yuanPerPoint.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "每分抵 1 元以上不合常理：" + yuanPerPoint.toPlainString());
        }
        if (maxPoints < 0) {
            throw new IllegalArgumentException("单笔可用积分上限不能为负：" + maxPoints);
        }
    }

    /**
     * 计算积分能抵扣多少钱。
     *
     * <p>三步：先取「余额」与「笔数上限」的较小值，
     * 再乘以每分价值，最后由 {@link DiscountPolicy#legal} 保证不超过当前价。
     * 第三步不可省略 —— 用户可能只有 100 积分（抵 10 元），
     * 但当前价已经被前面的折扣减到 5 元，这时最多只能抵 5 元。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文，提供可用积分
     * @return 抵扣金额，无可用积分时为 0
     */
    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        DiscountPolicy.requireValidPrice(price);
        if (context == null) {
            throw new IllegalArgumentException("积分抵扣必须提供订单上下文：否则不知道用户有多少积分");
        }
        int usable = Math.min(context.availablePoints(), maxPoints);
        if (usable <= 0) {
            return ZERO;
        }
        BigDecimal reduction = yuanPerPoint.multiply(BigDecimal.valueOf(usable));
        return DiscountPolicy.legal(reduction, price);
    }

    /**
     * 没用上的两种原因要分开说：
     * 「没积分」和「有积分但被价格卡住」对用户是完全不同的两件事。
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @return 跳过原因
     */
    @Override
    public String unavailableReason(BigDecimal price, OrderContext context) {
        int held = context == null ? 0 : context.availablePoints();
        int usable = Math.min(held, maxPoints);
        if (usable <= 0) {
            return "无可用积分（持有 " + held + "，单笔上限 " + maxPoints + "）";
        }
        return "当前价格仅剩 " + com.patterns.util.Money.yuan(price) + "，积分无法再抵";
    }
}
