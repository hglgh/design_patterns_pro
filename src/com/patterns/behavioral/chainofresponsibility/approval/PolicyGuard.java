package com.patterns.behavioral.chainofresponsibility.approval;

import com.patterns.behavioral.chainofresponsibility.Handler;

import java.util.Optional;

/**
 * 链首的"政策闸门"：<b>不满足售后政策就直接驳回，一个审批人都不惊动</b>。
 *
 * <p><b>为什么它必须放在链首？</b>
 * 因为它的职责不是"批多少钱"，而是"这事压根就不该进入审批流程"。
 * 放在后面会发生什么：800 元的单子先惊动客服专员和主管，最后才被政策驳掉——
 * 两个人白干，审计日志里还留下两条"无权"的噪声。
 * <b>把"过滤"放在"分派"之前，是链设计里最基本的一条顺序原则。</b>
 *
 * <p><b>它和 {@link LimitApprover} 的关键差别：两种终止。</b>
 * <pre>
 *   LimitApprover  能做决定 → 批准（终态）
 *                  不能决定 → 返回空，继续传
 *   PolicyGuard    违反政策 → 驳回（终态，且是否定的）
 *                  不违反   → 返回空，继续传
 * </pre>
 * 两者都只用了 {@code Optional} 的两个分支，但组合出了四种业务含义。
 * 这就是为什么 {@code tryHandle} 的返回值必须能表达"我不决定"——
 * 一旦用了 {@code boolean}，"不否决"和"同意"就再也分不开了。
 *
 * <p><b>为什么这个类的名字里没有"Approver"？</b>
 * 因为它确实不是审批人，它是一个<b>规则校验节点</b>。
 * 责任链上的节点不必是同一类业务角色，只要它们都遵守"要么决定、要么继续传"这一条契约。
 * 命名如实反映职责，比强行统一叫 {@code XxxHandler} 更有信息量。
 *
 * @author design-patterns-pro
 */
public final class PolicyGuard extends Handler<ApprovalContext, ApprovalDecision> {

    /** 无理由退款期（天）。超过这个天数，申请直接驳回。 */
    private final int maxDaysAfterPurchase;

    /**
     * @param maxDaysAfterPurchase 无理由退款期，必须为正数
     */
    public PolicyGuard(int maxDaysAfterPurchase) {
        super("售后政策校验");
        if (maxDaysAfterPurchase <= 0) {
            throw new IllegalArgumentException("无理由退款期必须为正数，实际传入：" + maxDaysAfterPurchase);
        }
        this.maxDaysAfterPurchase = maxDaysAfterPurchase;
    }

    /**
     * 违反政策则驳回；否则返回空——<b>"不否决"绝不等于"同意"</b>。
     *
     * <p>审计轨迹里这两种情况写的是完全不同的字：
     * 违反政策写"驳回"，不违反写"通过"。如果两者都写"ok"，
     * 事后排查时你会看到一串"ok"，然后完全不知道是谁真的批准了。
     */
    @Override
    protected Optional<ApprovalDecision> tryHandle(ApprovalContext context) {
        RefundRequest request = context.request();

        if (request.daysAfterPurchase() > maxDaysAfterPurchase) {
            String reason = "距下单 " + request.daysAfterPurchase() + " 天，已超过 "
                    + maxDaysAfterPurchase + " 天无理由退款期";
            context.record(name(), "驳回", reason);
            return Optional.of(new ApprovalDecision.Denied(
                    request.orderNo(), reason, name(), context.auditTrail()));
        }

        context.record(name(), "通过",
                "距下单 " + request.daysAfterPurchase() + " 天，在 " + maxDaysAfterPurchase + " 天无理由期内");
        return Optional.empty();
    }

    @Override
    public String describe() {
        return name() + "(≤" + maxDaysAfterPurchase + " 天)";
    }
}
