package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 链节点：<b>把「一个策略 + 一个适用条件」变成链上的一个环节</b>。
 *
 * <p>这是策略与责任链的接缝处，也是整个组合示例的核心一行：
 * 策略回答「怎么减」，条件回答「要不要减」，本节点回答「减完要不要继续」。
 * 三个问题分别由三个对象回答，谁都不越界。</p>
 *
 * <p><b>为什么不把条件写进策略？</b></p>
 * 因为「满 100 减 20」这条算法本身跟「仅限新客」毫无关系。
 * 如果写进策略，就会产生四个几乎相同的类：
 * {@code FullReductionPolicy}、{@code NewCustomerFullReductionPolicy}、
 * {@code MemberFullReductionPolicy}、{@code MemberDayFullReductionPolicy}，
 * 它们唯一的区别是构造时传入的那个判断。
 * 把条件抽出来之后，<b>一个满减策略可以配上任意条件</b>，
 * 组合数是「策略数 × 条件数」而不是「策略数 × 条件数 个类」。</p>
 *
 * <p><b>为什么节点名要单独传，不直接用策略名？</b></p>
 * 因为同一条策略可能在链上出现两次，且两次的条件不同：
 * <pre>
 *   ① 满减（无条件）      满 100 减 20
 *   ② 满减（会员日专属）  满 100 减 20
 * </pre>
 * <p>如果两行都叫「满 100 减 20」，运营看审计时会以为是重复执行。
 * 节点名是<b>位置标识</b>（第几步、什么角色），策略名是<b>规则标识</b>，
 * 两者回答的问题不同，所以分成两列。</p>
 *
 * @author design-patterns-pro
 */
public final class PolicyHandler extends DiscountHandler {

    /** 本节点要执行的策略。 */
    private final DiscountPolicy policy;

    /** 适用条件。 */
    private final Requirement requirement;

    /**
     * 创建一个无条件的节点。
     *
     * @param name   节点名称
     * @param policy 策略，非 null
     */
    public PolicyHandler(String name, DiscountPolicy policy) {
        this(name, policy, Requirement.always());
    }

    /**
     * 创建一个带条件的节点。
     *
     * @param name        节点名称，非空白
     * @param policy      策略，非 null
     * @param requirement 适用条件，非 null
     * @throws IllegalArgumentException 任一参数非法
     */
    public PolicyHandler(String name, DiscountPolicy policy, Requirement requirement) {
        super(name);
        if (policy == null) {
            throw new IllegalArgumentException("节点 [" + name + "] 的策略不能为 null");
        }
        if (requirement == null) {
            throw new IllegalArgumentException("节点 [" + name + "] 的条件不能为 null");
        }
        this.policy = policy;
        this.requirement = requirement;
    }

    /**
     * 判断条件 → 执行策略 → 给出三态决策。
     *
     * <p>三种分支对应三种真实业务情况：</p>
     * <ol>
     *   <li>条件不满足 → {@code Skipped}，原因来自 {@link Requirement#describe()}。
     *       注意这里<b>不执行策略</b>：条件的作用就是省掉一次无用的计算，
     *       更重要的是省掉策略内部可能产生的副作用（虽然本项目约定策略无副作用，
     *       但别的项目未必）；</li>
     *   <li>条件满足但算出来是 0 → {@code Skipped}，原因由策略自己解释
     *       （{@link DiscountPolicy#unavailableReason}）；</li>
     *   <li>算出来大于 0 → {@code Applied}，携带金额。</li>
     * </ol>
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @param state   执行状态（本节点用不到，但签名由基类固定）
     * @return 三态决策
     */
    @Override
    protected DiscountDecision decide(BigDecimal price, OrderContext context, ChainState state) {
        if (!requirement.test(context)) {
            return new DiscountDecision.Skipped("不满足条件：" + requirement.describe());
        }
        BigDecimal reduction = policy.discount(price, context);
        if (reduction.signum() <= 0) {
            return new DiscountDecision.Skipped(policy.unavailableReason(price, context));
        }
        return new DiscountDecision.Applied(reduction);
    }

    /**
     * 策略名称，用于审计轨迹。
     *
     * @return 策略名称
     */
    @Override
    protected String policyName() {
        return policy.name();
    }
}
