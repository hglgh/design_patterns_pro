package com.patterns.behavioral.chainofresponsibility.approval;

import com.patterns.behavioral.chainofresponsibility.Handler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 审批链：<b>组装 + 兜底</b>，这两件事都不属于任何一个节点。
 *
 * <p>这个类是客户端唯一需要认识的类。它只做三件事：
 * <ol>
 *   <li><b>组装</b>：决定链上有哪些节点、按什么顺序（{@link #standard()}）；</li>
 *   <li><b>兜底</b>：链走完了还没人做决定时，产出一个合法的业务结论（{@link #submit}）；</li>
 *   <li><b>自述</b>：把链的形状打出来给运维看（{@link #describe()}）。</li>
 * </ol>
 * 三件事都不是"某个审批人的职责"，所以既不能放进 {@link LimitApprover}，
 * 也不能放进 {@link PolicyGuard}。<b>找一个现成的类硬塞进去，是设计里最常见的偷懒。</b>
 *
 * <p><b>为什么兜底放在这里，而不是在链尾挂一个"兜底节点"？</b>
 * 两种做法都有人用，差别在"兜底是不是业务的一部分"：
 * <ul>
 *   <li>如果兜底有<b>业务语义</b>（比如"转人工"要走工单系统、要发通知），
 *       它就应该是一个节点，因为它做了一件真实的事；</li>
 *   <li>如果兜底只是<b>把"没人处理"翻译成一个返回值</b>，那它是链的语义补全，
 *       属于框架职责，放在这里更合适。</li>
 * </ul>
 * 本项目选后者：{@link ApprovalDecision.NeedsHumanReview} 只是一个数据，
 * 没有副作用，没必要为它建一个只有三行的类。
 *
 * <p><b>为什么 {@code submit} 不 catch 异常？</b>
 * 节点里可能抛出的是"代码写错了"（空指针、状态不合法），
 * 这类异常必须一路炸到最上层，带着完整堆栈。
 * 如果在这里 {@code catch (Exception e)} 然后翻译成"转人工"，
 * 结果是：所有 bug 都变成了"这笔单子需要人工复核"，
 * 线上安静得可怕，而真正的问题一次也没被修过。
 * <b>业务上的"办不成"应该用返回值表达；代码上的"跑错了"必须用异常表达。</b>
 *
 * @author design-patterns-pro
 */
public final class ApprovalChain {

    /** 链头。整条链只通过它被访问。 */
    private final Handler<ApprovalContext, ApprovalDecision> head;

    private ApprovalChain(Handler<ApprovalContext, ApprovalDecision> head) {
        this.head = head;
    }

    /**
     * 业务默认的审批链：<b>政策校验 → 客服专员 → 主管 → 经理 → 总监</b>。
     *
     * <p>订单金额越大，越往后走：
     * <pre>
     *   ≤ 200       客服专员直接批
     *   ≤ 2,000     客服专员无权 → 主管批
     *   ≤ 20,000    再往后 → 经理批
     *   ≤ 200,000   再往后 → 总监批
     *   更大        全链无人可批 → 转人工
     * </pre>
     *
     * <p>这个方法是"配置"，不是"逻辑"。真实项目里这些数字应当来自配置文件或数据库，
     * 但<b>哪怕写死在代码里，也要集中写在一个方法里</b>——
     * 因为它是唯一能一眼看清"整条链长什么样"的地方。
     * 反过来说，如果每个节点的上限散落在各自的类里（原项目就是这样：
     * 优先级 1/2/3 分别写在三个 Handler 内部），那么想知道"整条链什么形状"
     * 就得打开三个文件拼起来——<b>而且永远无法确认自己有没有漏看。</b>
     *
     * @return 默认审批链
     */
    public static ApprovalChain standard() {
        return new ApprovalChain(Handler.chain(List.of(
                new PolicyGuard(7),
                new LimitApprover("客服专员", "200"),
                new LimitApprover("客服主管", "2000"),
                new LimitApprover("售后经理", "20000"),
                new LimitApprover("售后总监", "200000"))));
    }

    /**
     * 用给定的节点按顺序组装一条链，<b>列表的第一个元素是链头</b>。
     *
     * <p><b>开放这个方法是为了让 Demo 能演示"换顺序会怎样"</b>，
     * 也是为了单测能塞进假节点。生产代码如果只需要标准链，
     * 就别调它——<b>能改的东西越多，能坏的地方就越多。</b>
     *
     * @param nodes 至少一个节点，按从前往后的顺序
     * @return 组装好的链
     * @throws IllegalArgumentException 列表为 null 或为空
     */
    public static ApprovalChain of(List<Handler<ApprovalContext, ApprovalDecision>> nodes) {
        // 复用 Handler.chain：它返回头节点，正好避开 linkTo 返回"下一个"的陷阱
        return new ApprovalChain(Handler.chain(nodes));
    }

    /**
     * 提交一张退款单，拿到<b>一定非 null</b> 的结论。
     *
     * <p>这里浓缩了整个模式运行期的全部流程，只有四行：
     * <pre>
     *   1. 建上下文（装请求 + 空轨迹）
     *   2. head.handle(ctx) —— 递归沿链往下问
     *   3. 有人给了决定 → 原样返回
     *   4. 一路返回空   → 兜底成"转人工"
     * </pre>
     * 第 4 行就是"为什么调用方永远不用判空"的原因：
     * <b>把可空性挡在框架里，不要漏给业务代码。</b>
     *
     * @param request 退款单，不能为 null
     * @return 三种结论之一，永不为 null
     */
    public ApprovalDecision submit(RefundRequest request) {
        Objects.requireNonNull(request, "退款单不能为 null");
        ApprovalContext context = new ApprovalContext(request);
        Optional<ApprovalDecision> decision = head.handle(context);
        return decision.orElseGet(() -> ApprovalDecision.needsHumanReview(request, context.auditTrail()));
    }

    /**
     * 链的可读形状，例如 {@code 售后政策校验(≤7 天) → 客服专员(≤￥200.00) → ...}。
     *
     * <p>建议在应用启动时打一行日志。责任链最典型的线上事故是"某个节点没被挂上"，
     * 而这种事故<b>打一行链的组成就能立刻看出来</b>；
     * 不打的话，就得等哪天大额退款莫名全转人工了才发现。
     *
     * @return 形如 {@code A → B → C} 的描述
     */
    public String describe() {
        return head.describeChain();
    }
}
