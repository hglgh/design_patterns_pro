package com.patterns.behavioral.chainofresponsibility.approval;

import com.patterns.behavioral.chainofresponsibility.Handler;
import com.patterns.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 一个"有额度上限"的审批人：<b>权限够就批，不够就往后传</b>。
 *
 * <p><b>本次重构最重要的一处简化：一个类代替四个类。</b>
 *
 * <p>原项目写了 {@code FullReductionDiscountHandler}、{@code CouponDiscountHandler}、
 * {@code MemberDiscountHandler} 三个类，每个 26 行，除了"判断条件"和"减多少钱"之外
 * 几乎逐行相同。如果换成审批场景，那就是"客服 / 主管 / 经理 / 总监"四个类，
 * 而它们之间的唯一差别只有两样东西：<b>叫什么名字</b>、<b>上限是多少</b>。
 *
 * <p>把这两样变成构造参数之后：
 * <pre>
 *   new LimitApprover("客服专员", "200")
 *   new LimitApprover("客服主管", "2000")
 *   new LimitApprover("售后经理", "20000")
 *   new LimitApprover("售后总监", "200000")
 * </pre>
 * <b>四个对象，一个类，零重复。</b> 更关键的是——新增一个审批层级只需要在组装处加一行，
 * 不需要新建文件、不需要复制粘贴、更不会因为漏改某一行而产生"看起来一样但行为不同"的分支。
 * 这和策略模式把"满 100 减 20"参数化是同一个道理：
 * <b>模式的价值不在于"有几个类"，而在于"变化被收敛到了哪里"。</b>
 *
 * <p><b>什么时候反而应该拆成多个类？</b> 当各审批人的判断逻辑<b>真的不一样</b>时。
 * 比如"主管"除了看金额还要看客户等级、"总监"除了看金额还要看是否已退过一次。
 * 那种差异是<b>行为差异</b>，参数化表达不了，硬塞会变成一堆 {@code if (title.equals("主管"))}——
 * 那才是应该拆类的时候。<b>先看差异是什么，再决定用什么手段。</b>
 *
 * <p><b>边界怎么定？</b> 本实现是"金额 ≤ 上限就批"。所以上限写 {@code 200} 的意思是
 * "200 元（含）以下我就能批"。判断写成 {@code compareTo(limit) > 0} 才符合这个语义，
 * 如果写成 {@code >= 0} 就会出现"恰好 200 元的单子被推给主管"。
 * 这类差一错误（off-by-one）在金额分档里极其常见，
 * 本项目在折扣 Demo 里抓到了原项目真实存在的同一类 bug，详见 README §2。
 *
 * @author design-patterns-pro
 * @see ApprovalChain 谁来把这些审批人串起来
 */
public final class LimitApprover extends Handler<ApprovalContext, ApprovalDecision> {

    /** 本节点能单独批准的金额上限（含）。 */
    private final BigDecimal limit;

    /**
     * @param title 节点名称，同时会出现在审计轨迹里
     * @param limit 审批上限，必须是正数
     */
    public LimitApprover(String title, String limit) {
        this(title, Money.of(limit));
    }

    /**
     * @param title 节点名称
     * @param limit 审批上限，必须为正数
     * @throws IllegalArgumentException 上限为 null 或非正数
     */
    public LimitApprover(String title, BigDecimal limit) {
        super(title);
        if (limit == null || limit.signum() <= 0) {
            throw new IllegalArgumentException("审批上限必须为正数，实际传入：" + limit);
        }
        this.limit = limit.setScale(2, RoundingMode.HALF_UP);
    }

    /** 本节点的审批上限。 */
    public BigDecimal limit() {
        return limit;
    }

    /**
     * 金额在额度内就批准，超出就返回空——<b>注意是"返回空"，不是"返回驳回"</b>。
     *
     * <p>这两者天差地别：
     * <ul>
     *   <li>返回空（{@code Optional.empty()}）= 我没权限，<b>请下一个节点接着看</b>；</li>
     *   <li>返回驳回 = 我认为这笔不该退，<b>链到此为止</b>。</li>
     * </ul>
     * 如果把"超权限"写成"驳回"，那 800 元的单子会被客服专员直接拒掉——
     * 这是责任链最典型的一种实现错误，而且它<b>在 Demo 里跑起来"看起来也很正常"</b>，
     * 只是所有大额申请都莫名其妙被拒了。
     */
    @Override
    protected Optional<ApprovalDecision> tryHandle(ApprovalContext context) {
        RefundRequest request = context.request();

        if (request.amount().compareTo(limit) > 0) {
            context.record(name(), "无权",
                    Money.yuan(request.amount()) + " 超出本节点上限 " + Money.yuan(limit) + "，转下一节点");
            return Optional.empty();
        }

        context.record(name(), "批准",
                Money.yuan(request.amount()) + " 在本节点上限 " + Money.yuan(limit) + " 之内");
        return Optional.of(new ApprovalDecision.Granted(
                request.orderNo(),
                request.amount(),
                name(),
                "在" + name() + "的直接审批权限（" + Money.yuan(limit) + "）内",
                context.auditTrail()));
    }

    @Override
    public String describe() {
        return name() + "(≤" + Money.yuan(limit) + ")";
    }
}
