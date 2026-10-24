package com.patterns.combined.orderservice;

import java.math.BigDecimal;

/**
 * 装饰器：<b>给任意策略加一条「单步最多减多少钱」的上限</b>，不改它的算法。
 *
 * <p>它和被装饰者是<b>同一个类型</b>（{@link DiscountPolicy}），
 * 所以链节点、工厂、其他装饰器都分不清手里拿的是裸策略还是包装过的策略。
 * 这正是装饰器模式的定义：<b>在不改变接口的前提下，动态地增加职责</b>。</p>
 *
 * <p><b>为什么不在每个策略里加一个 {@code cap} 参数？</b></p>
 * 三种做法各有代价，对比一下：
 * <table border="1">
 *   <caption>三种添加「上限」的方式</caption>
 *   <tr><th>做法</th><th>新增一个策略时</th><th>想临时关闭上限时</th><th>想叠两层上限时</th></tr>
 *   <tr><td>写进每个策略</td><td>要记得抄一遍 min 逻辑，漏抄就少了一道风控</td><td>要改构造函数</td><td>做不到</td></tr>
 *   <tr><td>继承一个 AbstractCappedPolicy</td><td>必须继承，且只能有一层上限</td><td>要新建子类</td><td>类爆炸（每种组合一个类）</td></tr>
 *   <tr><td>装饰器（本类）</td><td>不需要改，自动获得封顶能力</td><td>不包它就行</td><td>{@code new CappedPolicy(new CappedPolicy(p, a), b)}</td></tr>
 * </table>
 * <p>更关键的是<b>职责归属</b>：单个折扣「最多减多少」通常不是折扣算法的一部分，
 * 而是<b>活动风控</b>的要求（「这次大促单个折扣最多减 50，防止配置错误导致资损」）。
 * 风控和算法来自不同部门、变化节奏也不同，把它们揉进同一个类，
 * 就会出现「风控要改上限，算法同学被迫改代码」的协作摩擦。</p>
 *
 * <p><b>为什么 {@code cap} 只约束「减免额」，不约束「最终价」？</b></p>
 * 最终价的底线（不能为负、不能低于 0.01 元）是<b>全局不变量</b>，
 * 由 {@link DiscountPolicy#legal} 和链的执行器统一保证，不该由某一层装饰器负责。
 * 如果让 CappedPolicy 同时管「单步上限」和「最终价下限」，
 * 它就有了两个变化原因，将来「上限」的规则变了会牵连「下限」的判断。</p>
 *
 * <p><b>为什么装饰后的名称要变？</b></p>
 * 名称是审计轨迹里给人看的东西。如果一个被封顶的策略仍叫「满 100 减 20」，
 * 用户看到「减了 50」会以为规则被改了；写成
 * {@code "封顶 50（满 100 减 20）"} 才能解释「为什么这次只减了 50」。
 * <b>装饰器改变了行为，就应该在名字上体现出来</b> —— 这是可观测性的一部分。</p>
 *
 * @param delegate 被装饰的策略，非 null
 * @param cap      单步最大减免额，必须为正
 * @author design-patterns-pro
 */
public record CappedPolicy(DiscountPolicy delegate, BigDecimal cap) implements DiscountPolicy {

    /**
     * 校验参数。
     *
     * @throws IllegalArgumentException 被装饰者为空、上限非正
     */
    public CappedPolicy {
        if (delegate == null) {
            throw new IllegalArgumentException("被装饰的策略不能为 null");
        }
        if (cap == null || cap.signum() <= 0) {
            throw new IllegalArgumentException("单步减免上限必须为正：" + cap);
        }
        cap = DiscountPolicy.money(cap);
    }

    /**
     * 名称体现包装关系，例如 {@code "封顶 50（满 100 减 20）"}。
     *
     * @return 组合名称
     */
    @Override
    public String name() {
        return "封顶 " + com.patterns.util.Money.plain(cap) + "（" + delegate.name() + "）";
    }

    /**
     * 先让被装饰者算，再把结果截到上限。
     *
     * <p>注意顺序：必须<b>先算再截</b>。如果先截价格再交给策略，
     * 打折策略就会基于被截过的价格计算，结果完全不同 ——
     * 「封顶」约束的是减免额，不是参与计算的基数。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @return 不超过上限的减免额
     */
    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        BigDecimal inner = delegate.discount(price, context);
        if (inner.compareTo(cap) <= 0) {
            return inner;
        }
        return DiscountPolicy.legal(cap, price);
    }
}
