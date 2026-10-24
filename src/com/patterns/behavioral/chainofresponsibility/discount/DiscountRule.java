package com.patterns.behavioral.chainofresponsibility.discount;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 一条折扣规则：<b>看当前订单，回答"我能不能减，减多少，为什么"</b>。
 *
 * <p><b>先说清楚一件事：这个接口不是责任链的 Handler。</b>
 * 它长得像（都是"不同实现、按顺序执行"），但语义正好相反：
 * <pre>
 *   Handler（责任链）  → 命中一个就停，后面的根本不会被执行
 *   DiscountRule（管道）→ 每一条都会被执行，命中之后继续往下问
 * </pre>
 * 之所以<b>不</b>复用 {@code Handler}，是因为"传递"这件事在折扣场景里根本不存在：
 * 满减减完，优惠券当然还要接着减，会员折扣也还要接着算。
 * 硬套责任链会得到一个所有节点都"命中还继续传"的畸形链——
 * 那就等于把 {@code for} 循环包装了一套类，纯亏。
 *
 * <p><b>原项目正是在这里走偏的。</b> 它为了这三个"都要执行"的处理器，
 * 手写了一个单向链表（{@code HandlerNode} + {@code DiscountChain}）来维护顺序，
 * 还引入了 {@code getPriority()} 排序。而这段代码用 {@code List} 依次遍历就能完全等价——
 * 因为<b>没有"传递"的地方，就不需要"链"这个结构</b>。详见 README §4.10。
 *
 * <p><b>为什么返回值不是 {@code Optional<BigDecimal>}，而是一个三字段的 {@code Outcome}？</b>
 * 因为 {@code Optional.empty()} 只说"不适用"，<b>说不出"为什么不适用"</b>。
 * 而运营最常问的两个问题是"这个活动为什么没生效"和"券为什么没抵扣"。
 * 把 {@code reason} 带上，排查就是读一行日志的事；不带，就得靠猜或者加断点。
 * 这和多传一个字段的成本相比，完全不值一提。
 *
 * @author design-patterns-pro
 */
public interface DiscountRule {

    /** 规则名称，会出现在账单和轨迹里。 */
    String name();

    /**
     * 评估本规则。
     *
     * <p><b>契约</b>：实现必须无副作用——不能改订单、不能写库。
     * "减多少"由返回的 {@link Outcome} 描述，真正扣减由 {@link DiscountOrder#withDiscount} 做。
     * 这样"判断"和"修改"被彻底分开，单测一条规则时不需要造一个订单再断言它的可变字段。
     *
     * @param order 当前订单（只读）
     * @return 命中与否、减多少、为什么
     */
    Outcome evaluate(DiscountOrder order);

    /**
     * 一条规则的评估结果。
     *
     * @param applied  是否命中
     * @param discount 命中时减掉的钱；未命中时为 {@link BigDecimal#ZERO}
     * @param reason   命中/未命中的具体原因，必须带数字
     */
    record Outcome(boolean applied, BigDecimal discount, String reason) {

        public Outcome {
            Objects.requireNonNull(discount, "discount 不能为 null，未命中请用 ZERO");
            Objects.requireNonNull(reason, "reason 不能为 null：说不出原因的规则没法排查");
        }

        /**
         * 命中。
         *
         * @param discount 减掉的钱，必须为正数
         * @param reason   命中原因
         * @return 命中结果
         */
        public static Outcome hit(BigDecimal discount, String reason) {
            return new Outcome(true, discount, reason);
        }

        /**
         * 未命中。
         *
         * @param reason 未命中的原因
         * @return 未命中结果
         */
        public static Outcome skip(String reason) {
            return new Outcome(false, BigDecimal.ZERO, reason);
        }
    }
}
