package com.patterns.behavioral.chainofresponsibility.discount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 折扣管道：<b>按顺序把每条规则都问一遍，谁来都别想跳过</b>。
 *
 * <p><b>请先看清楚一件事：这个类里没有"链"。</b>
 * 它只是持有一个 {@code List<DiscountRule>}，然后 {@code for} 循环遍历。
 * 没有 {@code next}、没有 {@code Handler}、没有 {@code setNext()}、没有链表节点。
 * 这不是偷懒——<b>而是因为"传递"这件事根本不存在</b>：
 * 满减减完，优惠券还得接着减；券减完，会员折扣还得接着算。
 * 没有任何一步需要"找到能处理的就停下"。
 *
 * <p><b>这正是原项目最大的设计偏差。</b>
 * 原项目为了让三个折扣处理器按优先级执行，写了：
 * <ul>
 *   <li>{@code HandlerNode}：一个纯粹为了串链表的节点类，29 行；</li>
 *   <li>{@code DiscountChain}：插入排序 + 遍历执行 + 清空，62 行；</li>
 *   <li>{@code DiscountHandler.getPriority()}：把顺序信息塞进每个处理器内部；</li>
 *   <li>三个处理器类各 26 行，其中 4 行是 {@code getPriority()} 的样板。</li>
 * </ul>
 * 一共 257 行，只为了做一件 {@code List.of(a, b, c).forEach(...)} 就能做的事。
 * 而它换来的<b>不是</b>责任链的能力（因为链条从不提前终止），
 * 而是"看上去像责任链"。<b>为模式而模式的代价，在这里是 200 行代码。</b>
 *
 * <p><b>管道和链的判据只有一条：</b>
 * <pre>
 *   这条规则命中之后，后面的规则还该不该执行？
 *     该   → 管道（List），本类
 *     不该 → 责任链（next），Handler
 * </pre>
 * 判据简单到可以在 Code Review 时一眼用出来。难的不是判据，是<b>愿不愿意先问这一句</b>。
 *
 * <p><b>为什么规则列表要 {@code List.copyOf}？</b>
 * 因为管道是<b>构建一次、反复使用</b>的对象（通常作为单例或 Spring Bean 存在）。
 * 如果构造器直接持有调用方传进来的可变 List，那调用方事后 {@code add} 一条规则，
 * 管道的行为就悄悄变了——而且没有任何编译错误、没有任何日志。
 * {@code copyOf} 让"组装好之后不会变"成为<b>结构上的保证</b>，而不是口头约定。
 *
 * @author design-patterns-pro
 */
public final class DiscountPipeline {

    /** 规则列表，顺序即执行顺序。组装后不可变。 */
    private final List<DiscountRule> rules;

    /**
     * @param rules 至少一条规则，顺序即执行顺序
     * @throws IllegalArgumentException 列表为 null 或为空
     */
    public DiscountPipeline(List<DiscountRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("折扣管道至少需要一条规则");
        }
        this.rules = List.copyOf(rules);
    }

    /**
     * 业务默认的折扣管道：<b>满减 → 优惠券 → 会员</b>。
     *
     * <p>这个顺序是有理由的，而且理由必须写下来，否则接手的人总会想调换：
     * <ol>
     *   <li><b>满减先算</b>：满减看的是"还差多少钱能到门槛"，应该按接近原价的金额判断；</li>
     *   <li><b>优惠券其次</b>：券有明确门槛，也应按"扣掉满减之后的价格"判断是否可用，
     *       否则会出现"用满减把金额降到券门槛以下，还想用券"的套利；</li>
     *   <li><b>会员折扣最后</b>：它按百分比算，基数越小减得越少，对商家最有利，
     *       也符合"先立减、后打折"的行业惯例。</li>
     * </ol>
     *
     * @return 默认管道
     */
    public static DiscountPipeline standard() {
        return new DiscountPipeline(List.of(
                new FullReductionRule("200", "50"),
                new CouponRule("SAVE30", "30", "100"),
                new MemberRule("0.90")));
    }

    /**
     * 依次执行每条规则，返回最终订单<b>和每一步的轨迹</b>。
     *
     * <p>循环体只有五行，但每一行都有明确的理由：
     * <ol>
     *   <li>{@code evaluate(current)}：用<b>当前</b>订单问规则，而不是原始订单——
     *       这样后面的规则看到的是前面的结果，叠加才有意义；</li>
     *   <li>{@code steps.add(...)}：<b>无论命中与否都记一条</b>。
     *       只记命中的话，"为什么没生效"就永远查不到，而这恰恰是客诉的主因；</li>
     *   <li>{@code if (outcome.applied())}：命中才扣减。
     *       把"判断"和"修改"分开之后，未命中的规则<b>连订单都不会碰</b>，
     *       也就没有"改了一半"这种中间态。</li>
     * </ol>
     *
     * @param order 待计算的订单，不能为 null
     * @return 最终订单 + 每一步轨迹
     */
    public Result apply(DiscountOrder order) {
        Objects.requireNonNull(order, "订单不能为 null");
        DiscountOrder current = order;
        List<Step> steps = new ArrayList<>();
        for (DiscountRule rule : rules) {
            DiscountRule.Outcome outcome = rule.evaluate(current);
            steps.add(new Step(rule.name(), outcome.applied(), outcome.discount(), outcome.reason()));
            if (outcome.applied()) {
                current = current.withDiscount(rule.name(), outcome.discount());
            }
        }
        return new Result(current, steps);
    }

    /** 管道里有哪些规则，按执行顺序。 */
    public List<DiscountRule> rules() {
        return rules;
    }

    /**
     * 一次规则执行的记录。
     *
     * @param rule     规则名称
     * @param applied  是否命中
     * @param discount 命中时减掉的钱
     * @param reason   命中/未命中的原因
     */
    public record Step(String rule, boolean applied, BigDecimal discount, String reason) {
    }

    /**
     * 管道跑完的结果：<b>最终订单 + 每一步的轨迹</b>。
     *
     * <p>把轨迹和结果绑在一起返回，而不是让管道自己打印日志：
     * 打印是调用方的自由，库代码只负责把事实原样交出去。
     * 这样同一份轨迹既可以打控制台（Demo）、也可以发消息队列（生产）、
     * 还可以在单测里断言"第 2 步为什么没命中"。
     *
     * @param order 最终订单
     * @param steps 每一步的轨迹
     */
    public record Result(DiscountOrder order, List<Step> steps) {

        public Result {
            steps = List.copyOf(steps);
        }
    }
}
