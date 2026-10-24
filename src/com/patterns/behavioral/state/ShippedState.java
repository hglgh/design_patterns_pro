package com.patterns.behavioral.state;

import java.util.Set;

/**
 * 已发货：货在路上，等买家确认收货。
 *
 * <p><b>这个状态只有一个出口</b>：确认收货。不能取消、不能再发货、更不能再付款。
 *
 * <p><b>为什么"已发货不能取消"值得单独强调？</b>
 * 因为这是真实业务里最容易写散的一条规则。没有状态模式时，它通常长这样：
 * <pre>
 *   // 在 OrderService 里
 *   if (order.getStatus() == 3) throw new BizException("已发货订单不能取消");
 *   // 在 RefundService 里
 *   if (order.getStatus() == 3) return RefundResult.rejected("已发货，请先拒收");
 *   // 在 AdminController 里
 *   if (order.getStatus() == 3) { ... }   // 忘了写，于是后台能取消在路上订单
 * </pre>
 * 同一条业务规则写了三遍，第三遍漏了。而漏掉的那一处，
 * 恰恰是权限最高的后台入口。
 *
 * <p>在状态模式里，这条规则<b>只有一个位置</b>：
 * {@link #ALLOWED} 里没有 {@code CANCEL}。所有调用方共用同一个判断，
 * 不存在"某个入口忘了写"的可能。
 *
 * <blockquote>
 * <b>规则的重复不是靠自律消灭的，是靠结构消灭的。</b>
 * 当一条规则只能写在一个地方时，它就不可能被漏写。
 * </blockquote>
 *
 * <p><b>为什么这个状态不提供"修改收货地址"？</b>
 * 因为本示例的动作集合是刻意保持最小的。真实系统里当然会有
 * {@code UPDATE_ADDRESS} 这类动作，但每加一个动作，就要在五个状态里
 * 各回答一次"允许吗、做了什么"，这正是状态模式的<b>成本</b>——
 * 也是 §8 里要算清楚的账。
 *
 * @author design-patterns-pro
 */
public enum ShippedState implements OrderState {

    /** 唯一实例。 */
    INSTANCE;

    /**
     * 允许的动作：只有确认收货。
     *
     * <p>注意这里<b>没有 {@code CANCEL}</b>，而没有的理由是业务上的
     * （货已经出库，取消要走退货流程而不是取消流程），不是技术上的。
     * 状态模式的职责就是把这个业务事实变成<b>代码里的一个空位</b>——
     * 空位本身就是最清晰的文档。
     */
    private static final Set<OrderAction> ALLOWED =
            Set.of(OrderAction.CONFIRM);

    @Override
    public String displayName() {
        return "已发货";
    }

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;
    }

    /**
     * 处理确认收货。
     *
     * @param action 动作
     * @param order  订单
     * @return 转移结果
     */
    @Override
    public TransitionResult accept(OrderAction action, Order order) {
        return switch (action) {
            case CONFIRM -> {
                order.markDelivered();
                yield TransitionResult.ok(action, this, CompletedState.INSTANCE,
                        "买家已签收，交易完成");
            }
            case PAY, SHIP, CANCEL -> throw OrderState.unreachable(this, action);
        };
    }
}
