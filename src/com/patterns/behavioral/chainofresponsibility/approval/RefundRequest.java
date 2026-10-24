package com.patterns.behavioral.chainofresponsibility.approval;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一张退款单：责任链要传递的<b>请求</b>。
 *
 * <p>用 record 而不是普通类，是因为它<b>天生就该是不可变的</b>：
 * 一张退款单在链上被传了四个节点，如果每个节点都能改它的金额，
 * 那"最终批了多少钱"就永远说不清是谁改的。不可变让这件事从根上不可能发生——
 * <b>处理器只能做决定，不能篡改请求。</b>
 *
 * <p><b>为什么不在这个 record 里放审计日志？</b>
 * 这是本次重构刻意避开的一个坑。record 里放一个可变的 {@code List} 完全编译得过，
 * 但会带来两个后患：
 * <ul>
 *   <li>{@code record} 自动生成的 {@code equals}/{@code hashCode} 会把那个 List 算进去，
 *       而 List 的内容在链上一直在变——同一个请求在不同时刻 {@code equals} 出不同结果，
 *       放进 {@code HashSet} 里就再也取不出来了；</li>
 *   <li>"请求是什么"和"这次请求是怎么被处理的"是<b>两件事</b>。
 *       把后者塞进前者，等于让一次性的处理过程污染了业务实体。</li>
 * </ul>
 * 所以审计日志放在 {@link ApprovalContext} 里：它的生命周期只覆盖"一次链上旅行"。
 *
 * <p><b>为什么 {@code amount} 在构造时就收敛到 2 位小数？</b>
 * 因为后续所有判断都是 {@code compareTo}。{@code BigDecimal} 的 {@code compareTo}
 * 虽然只比数值不比 scale（{@code 200} 和 {@code 200.00} 相等），但显示和日志会不一致。
 * 在<b>入口处统一收敛一次</b>，比在每个节点里各收敛一次可靠得多。
 *
 * @param orderNo            订单号，审计时用于串起整条链路
 * @param amount             申请退款金额，必须为正数
 * @param reason             退款原因，人工复核时必看
 * @param daysAfterPurchase  距下单天数，售后政策节点的判断依据
 * @author design-patterns-pro
 */
public record RefundRequest(String orderNo, BigDecimal amount, String reason, int daysAfterPurchase) {

    /**
     * 紧凑构造器：所有校验都在对象<b>被创建的那一刻</b>完成。
     *
     * <p>把校验放在这里而不是放在链上某个节点，是因为"金额是负数"这种错误
     * 属于<b>调用方写错了</b>，不是"业务上不允许"。前者应该立刻炸在构造处，
     * 让栈顶直接指向出错的那一行；后者才是链要处理的事（比如"超过 7 天不给退"）。
     * <b>失败的位置越靠前，排查成本越低。</b>
     */
    public RefundRequest {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (amount == null) {
            throw new IllegalArgumentException("退款金额不能为 null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("退款金额必须为正数，实际传入：" + amount);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("退款原因不能为空：人工复核时要靠它判断");
        }
        if (daysAfterPurchase < 0) {
            throw new IllegalArgumentException("距下单天数不能为负数，实际传入：" + daysAfterPurchase);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
