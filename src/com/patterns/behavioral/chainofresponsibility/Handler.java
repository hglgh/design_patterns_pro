package com.patterns.behavioral.chainofresponsibility;

import java.util.List;
import java.util.Optional;

/**
 * 责任链的骨架：<b>一条线 + 一个模板方法</b>。
 *
 * <p>这个类只做四件事，其余一概不做：
 * <ol>
 *   <li>持有唯一的后继引用 {@code next}；</li>
 *   <li>保证组装出来的是一条<b>有向无环</b>路径（{@link #linkTo}）；</li>
 *   <li>把"先问自己、自己不要就问下一个"写死成 {@code final} 的 {@link #handle}；</li>
 *   <li>子类只能回答一个问题：<b>这个请求你做不做决定</b>（{@link #tryHandle}）。</li>
 * </ol>
 *
 * <p><b>为什么 {@code handle} 必须是 {@code final}？</b>
 * 因为"往下传"这件事是责任链的<b>定义</b>，不是某个节点的实现细节。
 * 一旦允许子类重写 {@code handle}，就有人会写出"我处理完不往下传"
 * 或者"我不处理也不往下传"——链当场断掉，而且断在最难排查的地方：
 * 上游以为请求已经送达，下游根本没收到。
 * <b>模板方法的意义正是把"流程"锁在基类，把"判断"留给子类。</b>
 *
 * <p><b>为什么 {@code tryHandle} 返回 {@code Optional<R>} 而不是 {@code boolean}？</b>
 * 三种写法都有人用，差别在"谁能表达什么"：
 * <pre>
 *   boolean tryHandle(C ctx)        → 只能表达"要不要"；结果得写进 ctx（隐式副作用）
 *   R tryHandle(C ctx)              → 得用 null 表示"我不处理"，null 和"处理结果为空"分不清
 *   Optional&lt;R&gt; tryHandle(C ctx)   → 空 = 我不做决定；有值 = 这是我的决定。语义唯一
 * </pre>
 * 本项目选第三种。它让"<b>没人处理</b>"成为一个必须被调用方显式面对的返回值
 * （{@code handle} 到底时也返回 {@code Optional.empty()}），
 * 而不是一个躺在地板上的 {@code null}。
 *
 * <p><b>为什么把 {@code next} 放在处理器内部，而不是像原项目那样外挂一个链表？</b>
 * 原项目的 {@code DiscountChain} 用 {@code HandlerNode} 自己维护链表，
 * 处理器对"我还有没有下一个"一无所知。后果是<b>处理器永远无法决定"到此为止"</b>：
 * 它连自己在链上的位置都不知道，只能被动地被 {@code for} 循环一个个调用一遍。
 * 那已经是"管道"，不是"责任链"了。详见 README §4.2。
 *
 * @param <C> 上下文类型：请求 + 审计轨迹的载体
 * @param <R> 决定类型：链最终要产出的结果
 * @author design-patterns-pro
 */
public abstract class Handler<C, R> {

    /** 节点名称。日志、审计轨迹、异常信息都靠它定位问题。 */
    private final String name;

    /**
     * 后继节点。
     *
     * <p>只在组装阶段被写入一次，运行期只读。没有写成 {@code final}
     * 是因为"先 new 出来、再串起来"这个顺序无法避免——但 {@link #linkTo} 会拒绝二次写入，
     * 所以它<b>在语义上仍然是不可变的</b>。
     */
    private Handler<C, R> next;

    /**
     * @param name 节点名称，例如 {@code "客服主管"}、{@code "售后政策校验"}
     * @throws IllegalArgumentException 名称为 null 或空白
     */
    protected Handler(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("处理器必须有名字：审计轨迹全靠它区分是哪个节点做的决定");
        }
        this.name = name;
    }

    /** 节点名称。 */
    public final String name() {
        return name;
    }

    /**
     * 把 {@code next} 接到本节点后面，并<b>返回 {@code next} 本身</b>。
     *
     * <p><b>返回下一个而不是返回自己，是为了让链式写法指向正确的位置</b>：
     * <pre>
     *   Handler&lt;C, R&gt; head = a.linkTo(b).linkTo(c);   // 错！head 拿到的是 c
     *   a.linkTo(b);                                     // 对：先固定 head
     *   Handler.chain(a, b, c);                          // 更好：用静态工厂，不会错
     * </pre>
     * 这个坑很常见：{@code linkTo} 的返回值看起来像"调用者"，实际上是"被链上的那个"。
     * 所以本类额外提供了 {@link #chain}，<b>让调用方永远不必手写返回值</b>。
     *
     * <p>三个校验都在"组装期"完成，而不是等运行期递归到 {@code StackOverflowError} 才发现：
     * <ul>
     *   <li>重复链接：{@code a.linkTo(b); a.linkTo(c);} → 静默把 b 丢掉，是最阴的一类 bug；</li>
     *   <li>自环：{@code a.linkTo(a)} 必然无限递归；</li>
     *   <li>环路：{@code a.linkTo(b); b.linkTo(a)} 同样无限递归，
     *       而且从任一节点看都"合法"，只有走到头才会暴露。</li>
     * </ul>
     *
     * @param next 后继节点，不能为 null
     * @return 传入的 {@code next}，方便继续往后链
     * @throws IllegalArgumentException next 为 null、等于自己，或会形成环
     * @throws IllegalStateException    本节点已经链接过后继
     */
    public final Handler<C, R> linkTo(Handler<C, R> next) {
        if (next == null) {
            // 链的末尾天然就是 null，不需要（也不应该）显式链接一个 null 节点。
            // 如果你在"清空链"时想传 null，请新建一条链——链一旦组装就视为不可变。
            throw new IllegalArgumentException(
                    "[" + name + "] 不能链接 null；链的末尾本身就是 null，无需显式链接");
        }
        if (this.next != null) {
            throw new IllegalStateException(
                    "[" + name + "] 已经有后继节点 [" + this.next.name + "]，不能重复链接 ["
                            + next.name + "]：重复链接会把前一条子链整段丢掉");
        }
        if (next == this) {
            throw new IllegalArgumentException("[" + name + "] 不能链接到自己：会造成无限递归");
        }
        // 环检测：从 next 往后走，如果走回了 this，说明这次链接会让整条链闭合成一个环。
        // 代价是组装期的一次 O(n) 遍历，换来的是运行期永远不会 StackOverflowError。
        // 这笔交易很划算：组装只发生一次，递归发生在每次请求。
        for (Handler<C, R> cursor = next; cursor != null; cursor = cursor.next) {
            if (cursor == this) {
                throw new IllegalArgumentException(
                        "链接 [" + name + "] → [" + next.name + "] 会形成环，责任链必须是一条有向无环路径");
            }
        }
        this.next = next;
        return next;
    }

    /**
     * 处理一个请求：<b>先问自己，自己不做决定就问下一个</b>。
     *
     * <p>这是整个模式唯一的流程定义，四行代码，不含任何业务判断：
     * <pre>
     *   1. 问自己（tryHandle）
     *   2. 自己给了决定 → 到此为止，原样返回
     *   3. 自己没给决定，但没有下一个 → 返回 Optional.empty()（没人做决定）
     *   4. 自己没给决定，还有下一个 → 递归交给下一个
     * </pre>
     *
     * <p><b>"到此为止"是责任链和管道的分水岭。</b>
     * 本项目同时提供了两个 Demo：审批流用责任链（批准即停），
     * 折扣叠加用管道（每条规则都要跑一遍）。同一个业务里，
     * 前者必须"停"，后者必须"都不停"——这决定了它们不该共用一套抽象。
     *
     * @param context 请求上下文，不能为 null（子类自行校验）
     * @return 链上第一个做出决定的节点给出的结果；无人做决定时为空
     */
    public final Optional<R> handle(C context) {
        Optional<R> mine = tryHandle(context);
        if (mine.isPresent()) {
            return mine;
        }
        Handler<C, R> downstream = next;
        if (downstream == null) {
            return Optional.empty();
        }
        return downstream.handle(context);
    }

    /**
     * 判断这个请求要不要在<b>本节点</b>做决定。
     *
     * <p>实现约定：
     * <ul>
     *   <li>返回 {@code Optional.of(result)} → 我做了决定（批准、驳回……），链在此终止；</li>
     *   <li>返回 {@code Optional.empty()} → 我不做决定，请继续传给下一个；
     *       <b>注意：这绝不等于"我同意了"</b>，它的准确含义是"这事不归我定"。
     *       两者在审计轨迹里必须能区分开，否则排查问题时全是噪声。</li>
     * </ul>
     *
     * <p>实现<b>不应该</b>自己去调用 {@code next}——那会把"传递"这件事从基类抢走，
     * 于是每个子类都要重复一遍"我处理不了怎么办"，而只要有一个子类漏写，链就断了。
     *
     * @param context 请求上下文
     * @return 本节点的决定，或空表示"不归我定"
     */
    protected abstract Optional<R> tryHandle(C context);

    /**
     * 把整条链画成一行，形如 {@code A → B → C}。
     *
     * <p>用途是启动时打一行日志。责任链最典型的线上事故是"某个节点没被挂上"，
     * 导致请求莫名走到人工兜底——而这种事故<b>打一行链的组成就能立刻看出来</b>。
     * 与其等出问题后翻代码，不如启动时把链的形状写进日志。
     *
     * @return 从本节点开始的可读描述
     */
    public final String describeChain() {
        StringBuilder text = new StringBuilder();
        for (Handler<C, R> cursor = this; cursor != null; cursor = cursor.next) {
            if (!text.isEmpty()) {
                text.append(" → ");
            }
            text.append(cursor.describe());
        }
        return text.toString();
    }

    /**
     * 单个节点的描述，子类可覆盖以带上自己的参数（例如审批上限）。
     *
     * @return 默认就是节点名称
     */
    public String describe() {
        return name;
    }

    /**
     * 按顺序把多个节点串成一条链，<b>返回头节点</b>。
     *
     * <p>存在的理由只有一个：{@link #linkTo} 返回的是"下一个"，
     * 手写 {@code head = a.linkTo(b).linkTo(c)} 会拿到尾巴而不是头。
     * 有了这个方法，调用方永远不需要关心 {@code linkTo} 返回什么：
     * <pre>
     *   Handler&lt;ApprovalContext, ApprovalDecision&gt; head = Handler.chain(List.of(
     *           new PolicyGuard(7),
     *           new LimitApprover("客服专员", "200"),
     *           new LimitApprover("客服主管", "2000")));
     * </pre>
     *
     * <p><b>为什么参数是 {@code List} 而不是可变参数 {@code Handler...}？</b>
     * 因为泛型可变参数在 Java 里天生不干净：调用方每传一次，编译期就会生成一个
     * 泛型数组，而它的运行期类型是 {@code Handler[]}——谁都可以往里塞一个
     * {@code Handler<String, Integer>}，然后在取出时炸 {@code ClassCastException}。
     * 编译器管这叫"堆污染"（heap pollution），开了 {@code -Xlint:all} 就会一直报。
     * 可以用 {@code @SafeVarargs} 把告警压下去，但那是"让编译器闭嘴"，
     * 不是"让问题消失"。{@code List.of(...)} 没有这个问题，写法一样短。
     *
     * @param nodes 至少一个节点，按从前往后的顺序
     * @param <C>   上下文类型
     * @param <R>   决定类型
     * @return 头节点（即 {@code nodes.get(0)}）
     * @throws IllegalArgumentException 列表为 null 或为空
     */
    public static <C, R> Handler<C, R> chain(List<Handler<C, R>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("链至少需要一个头节点");
        }
        Handler<C, R> head = nodes.get(0);
        Handler<C, R> cursor = head;
        for (int i = 1; i < nodes.size(); i++) {
            cursor = cursor.linkTo(nodes.get(i));
        }
        return head;
    }
}
