package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 链节点：<b>互斥组内只允许一个折扣生效</b>。
 *
 * <p>典型业务：「满减」和「折扣券」不能同享，「新客立减」与「会员折扣」不能叠加。
 * 这类规则的关键特征是：<b>它不只影响自己，还要求后面的节点停下来。</b></p>
 *
 * <p><b>为什么用 {@link DiscountDecision.Stopped} 而不是 {@code Skipped}？</b></p>
 * 这是本类存在的全部意义，值得说清楚。假设链是：
 * <pre>
 *   ① 满减（组：券）
 *   ② 折扣券（组：券）
 *   ③ 积分抵扣
 * </pre>
 * <p>如果 ① 生效后，② 返回 {@code Skipped}，那么链会继续走到 ③，
 * 结果是「满减 + 积分」—— 这没问题。但如果业务规则是
 * 「用了满减，整单不允许再使用任何优惠」，② 就必须返回 {@code Stopped}，
 * 让 ③ 也不执行。</p>
 * <p><b>「跳过」和「终止」的差别，就是「只影响我」和「影响所有人」的差别。</b>
 * 业务上这两条规则都存在，所以决策类型必须能区分它们 ——
 * 这正是 {@link DiscountDecision} 设计成三态而不是布尔值的直接理由。</p>
 *
 * <p><b>为什么「组已被占用」要放在条件判断之前？</b></p>
 * 顺序影响可解释性。假设用户是 Lv.1，而组已经被占用，
 * 两种顺序给出两种原因：
 * <ul>
 *   <li>先判组：{@code "同组「券」已由「满 200 减 30」占用"} —— 这是真实原因；</li>
 *   <li>先判条件：{@code "不满足条件：需 Lv.3"} —— 这是次要原因，
 *       用户照着升到 Lv.3 之后发现还是不能用，会更困惑。</li>
 * </ul>
 * <p><b>报告第一个真实障碍</b>，是错误信息设计的基本要求。</p>
 *
 * @author design-patterns-pro
 */
public final class ExclusiveGroupHandler extends DiscountHandler {

    /** 组名，例如 {@code "券"}、{@code "新人礼"}。 */
    private final String group;

    /** 本节点的策略。 */
    private final DiscountPolicy policy;

    /** 适用条件。 */
    private final Requirement requirement;

    /**
     * 创建一个无条件限制的互斥组成员。
     *
     * @param name   节点名称
     * @param group  组名，非空白
     * @param policy 策略，非 null
     */
    public ExclusiveGroupHandler(String name, String group, DiscountPolicy policy) {
        this(name, group, policy, Requirement.always());
    }

    /**
     * 创建一个完整配置的互斥组成员。
     *
     * @param name        节点名称，非空白
     * @param group       组名，非空白
     * @param policy      策略，非 null
     * @param requirement 适用条件，非 null
     * @throws IllegalArgumentException 任一参数非法
     */
    public ExclusiveGroupHandler(String name, String group, DiscountPolicy policy,
                                 Requirement requirement) {
        super(name);
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("互斥组必须有名字");
        }
        if (policy == null) {
            throw new IllegalArgumentException("节点 [" + name + "] 的策略不能为 null");
        }
        if (requirement == null) {
            throw new IllegalArgumentException("节点 [" + name + "] 的条件不能为 null");
        }
        this.group = group;
        this.policy = policy;
        this.requirement = requirement;
    }

    /**
     * 组是否已被占用 → 条件 → 策略。
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @param state   执行状态，提供「组是否已占用」并接受「占用登记」
     * @return 三态决策
     */
    @Override
    protected DiscountDecision decide(BigDecimal price, OrderContext context, ChainState state) {
        if (state.groupUsed(group)) {
            return new DiscountDecision.Stopped(
                    "同组「" + group + "」已被占用，后续优惠不再生效");
        }
        if (!requirement.test(context)) {
            return new DiscountDecision.Skipped("不满足条件：" + requirement.describe());
        }
        BigDecimal reduction = policy.discount(price, context);
        if (reduction.signum() <= 0) {
            return new DiscountDecision.Skipped(policy.unavailableReason(price, context));
        }
        state.markGroup(group);
        return new DiscountDecision.Applied(reduction);
    }

    /**
     * 策略名称，附带组名，便于审计时看清它属于哪个互斥组。
     *
     * @return 例如 {@code "满 200 减 30（组：券）"}
     */
    @Override
    protected String policyName() {
        return policy.name() + "（组：" + group + "）";
    }

    /**
     * 便于测试与输出的组名。
     *
     * @return 组名
     */
    public String group() {
        return group;
    }

    /**
     * 持有本组的策略，供工厂和测试读取。
     *
     * @return 策略
     */
    public DiscountPolicy policy() {
        return policy;
    }
}
