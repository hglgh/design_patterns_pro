package com.patterns.behavioral.state;

import com.patterns.util.Money;

import java.util.Set;

/**
 * 已支付：钱到了，等卖家发货。
 *
 * <p><b>这个状态能做什么？</b> 发货，或者取消（但取消的后果和待支付时完全不同）。
 *
 * <p><b>它是整个示例里最关键的一个状态</b>，因为它同时回答了两件事：
 * <ol>
 *   <li><b>同一个动作，在不同状态下含义不同。</b>
 *       {@code CANCEL} 在待支付下只是"释放库存"，在这里却要"原路退款"。
 *       调用方写的都是 {@code order.cancel()} 一行，行为差异全部由状态吸收。</li>
 *   <li><b>同一个动作，在同一个状态下也可能被拒绝。</b>
 *       {@code PAY} 在这里会被拒绝——重复支付是真实世界里最常见的支付事故之一。
 *       如果状态模式只演示"状态会往前走"，不演示"拒绝"，那它跟流水线没有区别。</li>
 * </ol>
 *
 * <p><b>为什么 {@code SHIP} 要生成物流单号，而不是让调用方传进来？</b>
 * 因为"发货"这个动作的<b>本质</b>就是"产生一个可追踪的运单"。
 * 让调用方自己生成单号再传进来，等于把这条业务规则外包给了每个调用点——
 * 三个调用点就会写出三种单号格式。
 *
 * <blockquote>
 * <b>判断一段逻辑该放在状态里还是调用方</b>，只需要问一句：
 * 这个动作换一个调用方来做，它的内容会变吗？
 * 会变 → 放调用方；不会变 → 放状态里。
 * </blockquote>
 *
 * <p>物流单号必须<b>可复现</b>：本示例从订单号里取数字部分拼成 {@code SF2001}，
 * 不用时间戳或随机数。否则同一个程序跑两次输出不同，文档里的"真实输出"
 * 就变成了假的。
 *
 * @author design-patterns-pro
 */
public enum PaidState implements OrderState {

    /** 唯一实例。 */
    INSTANCE;

    /**
     * 允许的动作：发货、取消。
     *
     * <p><b>没有 {@code PAY}</b>——这是本状态最重要的信息。
     * 重复支付不是"应该成功但要注意"，而是<b>根本不允许</b>。
     * 把这条规则写在集合里，界面层拿到的直接就是
     * "已支付订单不显示支付按钮"，不需要前端再判断一次。
     */
    private static final Set<OrderAction> ALLOWED =
            Set.of(OrderAction.SHIP, OrderAction.CANCEL);

    @Override
    public String displayName() {
        return "已支付";
    }

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;
    }

    /**
     * 处理发货或取消。
     *
     * @param action 动作
     * @param order  订单
     * @return 转移结果
     */
    @Override
    public TransitionResult accept(OrderAction action, Order order) {
        return switch (action) {
            case SHIP -> {
                // 单号由订单自己发，不由调用方传——保证全站格式一致
                String trackingNo = order.issueTrackingNo();
                yield TransitionResult.ok(action, this, ShippedState.INSTANCE,
                        "已出库，物流单号 " + trackingNo);
            }
            case CANCEL -> {
                // 同样的 cancel()，这里要退款；待支付时只需释放库存
                order.markCancelled("已支付后取消，需原路退款");
                yield TransitionResult.ok(action, this, CancelledState.INSTANCE,
                        "订单已取消，将原路退回 " + Money.yuan(order.amount()));
            }
            case PAY -> throw OrderState.unreachable(this, action);
            case CONFIRM -> throw OrderState.unreachable(this, action);
        };
    }
}
