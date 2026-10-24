package com.patterns.combined.orderservice;

import java.math.BigDecimal;
import java.util.List;

/**
 * 责任链的骨架：<b>一条线 + 一个模板方法 + 一个三态答案</b>。
 *
 * <p>这个类只做四件事：</p>
 * <ol>
 *   <li>持有唯一的后继引用 {@code next}；</li>
 *   <li>保证组装出来的是一条<b>有向无环</b>路径（{@link #linkTo}）；</li>
 *   <li>把「问自己 → 记录 → 决定要不要往下传」写死成 {@code final} 的 {@link #process}；</li>
 *   <li>子类只回答一个问题：<b>你对当前价格做什么决定</b>（{@link #decide}）。</li>
 * </ol>
 *
 * <p><b>为什么 {@code process} 必须是 {@code final}？</b></p>
 * 「往下传」是责任链的定义，不是某个节点的实现细节。
 * 一旦允许子类重写 {@code process}，就会有人写出
 * 「我处理完忘了往下传」，链在最难排查的地方断掉：
 * 上游以为请求已送达，下游根本没收到。
 * <b>模板方法的意义是把流程锁在基类，把判断留给子类。</b></p>
 *
 * <p><b>这个类与解释器模式里的 {@code Handler} 有什么不同？</b></p>
 * 那个 {@code Handler} 是<b>泛型</b>的（{@code Handler<C, R>}），
 * 请求上下文和决定类型都由外部指定，适合"审批"这种一件事只由一个节点拍板的场景。
 * 本类不泛型，因为折扣链有两条额外约束：
 * <ul>
 *   <li>每个节点都可能<b>改变</b>礼物（价格），而不是"一个人说了算"；</li>
 *   <li>节点之间要通过 {@link ChainState} 共享"已占用的互斥组"。</li>
 * </ul>
 * <p>把这两条抽成泛型参数，会让类型签名长到没人愿意读。
 * <b>模式是骨架，不是模板 —— 同一个模式在不同场景下应该长出不同的类。</b></p>
 *
 * <p><b>为什么链上每个节点都要记审计，即使它被跳过了？</b></p>
 * 「跳过」本身就是重要信息。用户问「为什么我的会员折扣没生效」，
 * 如果轨迹里只有生效的节点，答案永远是「不知道」；
 * 有了一行「跳过：需 Lv.3 及以上」，客服可以直接回答。
 * <b>可观测性不是记录成功，而是记录「发生了什么」。</b></p>
 *
 * @author design-patterns-pro
 */
public abstract class DiscountHandler {

    /** 节点名称，审计轨迹和执行日志靠它定位。 */
    private final String name;

    /**
     * 后继节点。
     *
     * <p>只在组装阶段写入一次，运行期只读。没有写成 {@code final}
     * 是因为「先 new 再串」这个顺序无法避免，但 {@link #linkTo}
     * 会拒绝二次写入，所以它<b>在语义上不可变</b>。</p>
     */
    private DiscountHandler next;

    /**
     * @param name 节点名称，例如 {@code "① 满减"}
     * @throws IllegalArgumentException 名称为 null 或空白
     */
    protected DiscountHandler(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("处理器必须有名字：审计轨迹全靠它区分是哪个节点");
        }
        this.name = name;
    }

    /** 节点名称。 */
    public final String name() {
        return name;
    }

    /**
     * 后继节点，没有时返回 null。
     *
     * @return 后继节点或 null
     */
    public final DiscountHandler next() {
        return next;
    }

    /**
     * 把 {@code next} 接到本节点后面，并返回 {@code next} 本身。
     *
     * <p>四个校验全部在<b>组装期</b>完成，而不是等运行期递归到
     * {@code StackOverflowError} 才发现：</p>
     * <ul>
     *   <li>重复链接：{@code a.linkTo(b); a.linkTo(c);} 会静默丢掉 b；</li>
     *   <li>自环：{@code a.linkTo(a)} 必然无限递归；</li>
     *   <li>环路：{@code a.linkTo(b); b.linkTo(a)} 从任一节点看都「合法」，只有走到头才暴露。</li>
     * </ul>
     *
     * @param next 后继节点，不能为 null
     * @return 传入的 {@code next}，方便继续往后链
     * @throws IllegalArgumentException next 为 null、等于自己，或会形成环
     * @throws IllegalStateException    本节点已经链接过后继
     */
    public final DiscountHandler linkTo(DiscountHandler next) {
        if (next == null) {
            throw new IllegalArgumentException(
                    "[" + name + "] 不能链接 null；链的末尾本身就是 null");
        }
        if (this.next != null) {
            throw new IllegalStateException(
                    "[" + name + "] 已经有后继 [" + this.next.name + "]，不能重复链接 ["
                            + next.name + "]：重复链接会把前一条子链整段丢掉");
        }
        if (next == this) {
            throw new IllegalArgumentException("[" + name + "] 不能链接到自己：会造成无限递归");
        }
        for (DiscountHandler cursor = next; cursor != null; cursor = cursor.next) {
            if (cursor == this) {
                throw new IllegalArgumentException(
                        "链接 [" + name + "] → [" + next.name + "] 会形成环，责任链必须是一条有向无环路径");
            }
        }
        this.next = next;
        return next;
    }

    /**
     * 处理一个请求：<b>问自己 → 记账 → 决定是否继续</b>。
     *
     * <p>这四步的顺序是有讲究的：</p>
     * <ol>
     *   <li><b>先看是否已终止</b>：终止后不再问任何节点，连轨迹也不记 ——
     *       那些节点确实没被执行，记一行「未执行」只会让轨迹变长、信息变少；</li>
     *   <li><b>先问自己再记账</b>：记录的是「决策之后」的价格，
     *       所以必须先让子类算完、先把价格扣掉，再写轨迹；</li>
     *   <li><b>决策决定去留</b>：{@code Stopped} 直接把整条链标记终止，
     *       这与 {@code Skipped} 的区别是「后面的节点还能不能被执行」；</li>
     *   <li><b>把价格传给下一个</b>：传的是 {@link ChainState}，
     *       里面已经有扣减后的价格。</li>
     * </ol>
     *
     * @param state   执行状态，承载当前价格、互斥组和轨迹
     * @param context 订单上下文
     */
    public final void process(ChainState state, OrderContext context) {
        if (state.stopped()) {
            return;
        }
        BigDecimal before = state.price();
        DiscountDecision decision = decide(before, context, state);
        if (decision instanceof DiscountDecision.Applied(BigDecimal amount)) {
            state.reduce(amount);
        }
        state.trail().record(name, policyName(), decision, before, state.price());
        if (!decision.continues()) {
            state.stop();
            return;
        }
        if (next != null) {
            next.process(state, context);
        }
    }

    /**
     * 子类实现：本节点对当前价格做什么决定。
     *
     * <p><b>允许读取 {@code state} 吗？</b>
     * 允许，但<b>只允许读</b>（当前价、已占用组），
     * 唯一的写操作由基类根据返回值完成。
     * 这样「谁改了价格」永远能追溯到一条 {@code Applied} 记录，
     * 而不会出现「某个节点偷偷把价格改了但轨迹里没有」的情况。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @param state   执行状态（只读）
     * @return 三态决策
     */
    protected abstract DiscountDecision decide(BigDecimal price, OrderContext context, ChainState state);

    /**
     * 本节点关联的策略名称，用于审计轨迹。
     *
     * @return 默认为 {@code "——"}，表示该节点不绑定策略
     */
    protected String policyName() {
        return "——";
    }

    /**
     * 把若干节点按给定顺序串成一条链。
     *
     * <p>用静态工厂而不是让调用方手写 {@code a.linkTo(b); b.linkTo(c);}，
     * 是因为 {@code linkTo} 返回的是<b>下一个</b>，很容易写成
     * {@code head = a.linkTo(b).linkTo(c)} 而拿到错误的头节点。
     * 这个方法让调用方永远不需要关心返回值。</p>
     *
     * @param nodes 按执行顺序排列的节点，至少一个
     * @return 头节点
     * @throws IllegalArgumentException 列表为空
     */
    public static DiscountHandler chain(List<DiscountHandler> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("链至少需要一个节点");
        }
        for (int index = 0; index < nodes.size() - 1; index++) {
            nodes.get(index).linkTo(nodes.get(index + 1));
        }
        return nodes.get(0);
    }
}
