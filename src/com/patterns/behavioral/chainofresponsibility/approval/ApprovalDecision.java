package com.patterns.behavioral.chainofresponsibility.approval;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审批链最终给出的<b>三种结论</b>，用 {@code sealed} 把"可能性"锁死。
 *
 * <p><b>为什么用 sealed interface + record，而不是一个带 {@code status} 字段的类？</b>
 * 因为"三种结论各自携带的字段根本不同"：
 * <pre>
 *   Granted          批准：需要"批了多少钱""谁批的"
 *   Denied           驳回：需要"为什么驳回""谁驳的"
 *   NeedsHumanReview 无人可批：需要"为什么走到人工""申请的是多少钱"
 * </pre>
 * 塞进一个类，就会得到四个永远为 null 的字段和一堆"这个字段在这种状态下有没有意义"的注释。
 * sealed 之后，编译器会保证 {@code switch} 覆盖所有分支——<b>新增一种结论时，
 * 所有没处理它的 switch 会直接编译失败</b>，而不是在运行期悄悄走进 default 分支。
 *
 * <p>这一点在本模式下格外重要：责任链的"没走到底"是一个<b>真实且必须处理的结局</b>。
 * 原项目的 {@code DiscountChain.process()} 返回 {@code void}，
 * 于是"一个处理器都没命中"和"所有处理器都命中了"在调用方看来完全一样——
 * 这正是不给它返回值的代价。
 *
 * <p><b>为什么每种结论都带一份 {@code auditTrail}？</b>
 * 因为"结论"和"怎么得出这个结论的"必须一起交给调用方。
 * 只返回 {@code Granted(800)}，运营问"为什么是主管批的不是经理批的"就答不上来；
 * 带上轨迹，答案就在数据里。
 *
 * <p>还有一个更深的原因：<b>轨迹只会在"做决定的那一刻"是完整的</b>。
 * 三种结论都由"链的最后一个节点"创建（批准/驳回由节点创建，无人可批由兜底创建），
 * 那一刻之后再没有任何节点会往 {@link ApprovalContext} 里写东西。
 * 所以在这里做快照，一定是完整的一份。
 *
 * @author design-patterns-pro
 */
public sealed interface ApprovalDecision {

    /** 订单号，三种结论都有。 */
    String orderNo();

    /** 得出这个结论时走过的完整轨迹。 */
    List<ApprovalContext.Step> auditTrail();

    /**
     * 批准：退款通过。
     *
     * @param orderNo        订单号
     * @param approvedAmount 批准的金额
     * @param approver       批准人（节点名称）
     * @param note           批准依据
     * @param auditTrail     完整轨迹
     */
    record Granted(String orderNo, BigDecimal approvedAmount, String approver,
                   String note, List<ApprovalContext.Step> auditTrail) implements ApprovalDecision {

        public Granted {
            auditTrail = List.copyOf(auditTrail);
        }
    }

    /**
     * 驳回：退款不通过，且<b>链在此终止</b>。
     *
     * <p>驳回是一种"否定的批准"，同样是终态。很多初学者以为责任链只有"处理成功"才终止，
     * 于是把"金额超过 7 天不给退"写成"继续往下传，让后面的人拒"——
     * 结果是：一条明明不可能通过的申请，惊动了整条审批链上的所有人。
     */
    record Denied(String orderNo, String reason, String rejectedBy,
                  List<ApprovalContext.Step> auditTrail) implements ApprovalDecision {

        public Denied {
            auditTrail = List.copyOf(auditTrail);
        }
    }

    /**
     * 无人可批：链走完了，没有任何节点愿意做出决定，转人工。
     *
     * <p><b>这个分支绝不能省。</b> 它对应真实业务里的"超权限工单"。
     * 如果链走到底返回 {@code null} 或者抛异常，调用方只能猜；
     * 而"转人工"本身就是一个合法、应该被记录、应该被统计的业务结果。
     */
    record NeedsHumanReview(String orderNo, BigDecimal amount, String reason,
                            List<ApprovalContext.Step> auditTrail) implements ApprovalDecision {

        public NeedsHumanReview {
            auditTrail = List.copyOf(auditTrail);
        }
    }

    /**
     * 构造"无人可批"结论。只有 {@code ApprovalChain} 会调用它——
     * 因为只有链的拥有者才知道"链走完了"意味着什么。
     *
     * @param request 原请求
     * @param trail   走完链之后的完整轨迹
     * @return 转人工的结论
     */
    static ApprovalDecision needsHumanReview(RefundRequest request, List<ApprovalContext.Step> trail) {
        return new NeedsHumanReview(
                request.orderNo(),
                request.amount(),
                "链上所有审批节点的权限上限都低于 " + Money.yuan(request.amount()) + "，需人工复核",
                trail);
    }
}
