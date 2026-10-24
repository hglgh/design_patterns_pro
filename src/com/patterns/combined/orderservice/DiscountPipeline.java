package com.patterns.combined.orderservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 折扣链的执行器：<b>把「一条组装好的链」变成「一次计算结果」</b>。
 *
 * <p>它自己不是节点，也不参与计算。它负责三件事：</p>
 * <ol>
 *   <li>为每次请求创建一份新的 {@link ChainState}（价格、轨迹、互斥组都是本次独有的）；</li>
 *   <li>把请求交给链头；</li>
 *   <li>把执行状态打包成不可变的 {@link DiscountResult}。</li>
 * </ol>
 *
 * <p><b>为什么要把「链」和「执行器」分开？</b></p>
 * 因为它们的生命周期完全不同：
 * <table border="1">
 *   <caption>链与执行器的生命周期</caption>
 *   <tr><th></th><th>链（{@link DiscountHandler}）</th><th>执行器（本类）</th></tr>
 *   <tr><td>创建频率</td><td>每次活动配置变更时重建一次</td><td>随链一起创建</td></tr>
 *   <tr><td>执行频率</td><td>不执行</td><td>每个订单执行一次</td></tr>
 *   <tr><td>是否可变</td><td>组装后不可变</td><td>无可变字段，可并发使用</td></tr>
 *   <tr><td>状态保存在哪</td><td>节点内部只有配置</td><td><b>状态全在本次的 {@link ChainState} 里</b></td></tr>
 * </table>
 * <p>把状态放在执行器字段里（很多人的第一版写法）会导致：
 * 两个订单同时计算时，A 的减免被记到 B 的轨迹上。
 * <b>状态必须是「每次请求一份」，而不是「每个对象一份」</b> ——
 * 这就是本类所有可变数据都放在方法内部的理由。</p>
 *
 * <p><b>为什么空链要返回原价，而不是抛异常？</b></p>
 * 「这个用户没有任何可用优惠」是完全正常的业务场景。
 * 如果空链抛异常，每个调用点都要 try-catch 或者判空，
 * 错误处理会淹没主流程。返回一个「减了 0 元」的结果，
 * 让调用方用同一套代码处理所有情况。</p>
 *
 * <p><b>为什么还要保存节点列表？</b></p>
 * 为了在不执行的情况下回答「这条链长什么样」——
 * 运营后台、日志、监控都需要展示链的构成。
 * 只存头节点也能遍历出来，但每次都遍历一遍既浪费又容易写出死循环，
 * 所以组装时一次性展开成列表。</p>
 *
 * @author design-patterns-pro
 */
public final class DiscountPipeline {

    /** 链头；空链时为 null。 */
    private final DiscountHandler head;

    /** 节点列表，按执行顺序。 */
    private final List<DiscountHandler> nodes;

    private DiscountPipeline(DiscountHandler head, List<DiscountHandler> nodes) {
        this.head = head;
        this.nodes = List.copyOf(nodes);
    }

    /**
     * 用一条组装好的链创建执行器。
     *
     * @param head 链头，非 null
     * @return 执行器
     * @throws IllegalArgumentException 链头为 null
     */
    public static DiscountPipeline of(DiscountHandler head) {
        if (head == null) {
            throw new IllegalArgumentException("链头不能为 null；无优惠请用 DiscountPipeline.empty()");
        }
        List<DiscountHandler> nodes = new ArrayList<>();
        for (DiscountHandler cursor = head; cursor != null; cursor = cursor.next()) {
            nodes.add(cursor);
        }
        return new DiscountPipeline(head, nodes);
    }

    /**
     * 空链：不执行任何节点，结果就是原价。
     *
     * @return 空执行器
     */
    public static DiscountPipeline empty() {
        return new DiscountPipeline(null, List.of());
    }

    /**
     * 计算一次。
     *
     * <p>每次调用都会新建 {@link ChainState}，所以同一个执行器
     * 可以被多个线程、多个订单同时使用，互不干扰。</p>
     *
     * @param context 订单上下文，非 null
     * @return 不可变结果
     * @throws IllegalArgumentException context 为 null
     */
    public DiscountResult calculate(OrderContext context) {
        if (context == null) {
            throw new IllegalArgumentException("订单上下文不能为 null");
        }
        ChainState state = new ChainState(context.orderAmount());
        if (head != null) {
            head.process(state, context);
        }
        return new DiscountResult(
                context.orderAmount(),
                state.price(),
                state.trail().entries(),
                state.stopped(),
                state.usedGroups());
    }

    /**
     * 链的构成描述，例如 {@code "① 满减 → ② 会员九折 → ③ 积分"}。
     *
     * @return 单行描述；空链返回 {@code "（空链）"}
     */
    public String describeChain() {
        if (nodes.isEmpty()) {
            return "（空链）";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < nodes.size(); index++) {
            if (index > 0) {
                text.append(" → ");
            }
            text.append(nodes.get(index).name());
        }
        return text.toString();
    }

    /**
     * 节点个数。
     *
     * @return 节点数
     */
    public int size() {
        return nodes.size();
    }

    /**
     * 节点列表，按执行顺序。
     *
     * @return 不可变列表
     */
    public List<DiscountHandler> nodes() {
        return nodes;
    }

    /**
     * 一次性计算并返回结果，便于链式调用。
     *
     * @param head    链头
     * @param context 订单上下文
     * @return 计算结果
     */
    public static DiscountResult run(DiscountHandler head, OrderContext context) {
        return of(head).calculate(context);
    }

    /**
     * 用给定价格快速算一次（无上下文、无链）。
     *
     * @param amount 原价
     * @return 结果（等于原价）
     */
    public static DiscountResult identity(BigDecimal amount) {
        return empty().calculate(OrderContext.of(amount.toPlainString()));
    }
}
