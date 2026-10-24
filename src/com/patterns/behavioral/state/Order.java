package com.patterns.behavioral.state;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订单：状态模式的 Context（上下文）。
 *
 * <p><b>它在本模式里只做三件事，每件都必须做对：</b>
 * <ol>
 *   <li><b>持有当前状态，并且是唯一的写入口。</b></li>
 *   <li><b>暴露业务动作</b>（{@link #pay()} 等方法），而不是暴露"设置状态"。</li>
 *   <li><b>记录每一步发生了什么</b>，成功的和失败的都记。</li>
 * </ol>
 *
 * <h2>一、为什么入口是 {@code pay()} 而不是 {@code setState()}？</h2>
 *
 * <p>原项目把 {@code setState(OrderState)} 设成了 {@code public}：
 * <pre>
 *   class Order {
 *       public void setState(OrderState state) { this.state = state; }
 *   }
 * </pre>
 * 任何一段代码都能写 {@code order.setState(new CompletedState())}，
 * 跳过支付、跳过发货，直接把订单标记成完成。<b>状态机的合法性完全没人管。</b>
 *
 * <p>本类的做法是把入口收窄成四个<b>业务语义</b>的方法：
 * <pre>
 *   order.pay()      order.ship()      order.confirm()      order.cancel()
 * </pre>
 * 这些方法内部没有 {@code if (state == ...)}，全部转发给当前状态。
 * 于是：
 * <ul>
 *   <li>调用方<b>不需要知道</b>当前是什么状态，也不需要知道有哪些状态；</li>
 *   <li>调用方<b>没有办法</b>直接指定目标状态——它只能说"我要做支付这件事"，
 *       至于这件事能不能做、做完变成什么，由状态决定。</li>
 * </ul>
 *
 * <h2>二、为什么 {@code state} 没有任何 setter？</h2>
 *
 * <p>因为状态的变化必须<b>和它引发的结果绑定</b>：
 * 只有 {@link TransitionResult#success()} 为真时才切换。
 * 如果状态可以由外部随意赋值，那"失败不转移"这条不变量就守不住——
 * 有人可能在转移失败后仍然把状态改掉，而调用方看到的是一句"已发货订单不能取消"。
 *
 * <p>所以 {@link #transition} 是 {@code private}，并且是<b>唯一写 {@code state} 的方法</b>。
 * 想给所有转移加日志、埋点、加锁，只需要改这一个方法。
 *
 * <h2>三、为什么失败的尝试也要进轨迹？</h2>
 *
 * <p>见 {@link HistoryEntry} 的说明。这里补一条本类的实现细节：
 * 记录发生在状态切换<b>之后</b>，所以轨迹里存的是"那一步的结果对象"，
 * 而不是对订单当前状态的引用——即使订单后来又往前走了，
 * 历史里那条记录仍然准确地描述当时发生了什么。
 *
 * @author design-patterns-pro
 */
public final class Order {

    /** 订单号，创建后不变。 */
    private final String orderNo;

    /** 订单金额，创建后不变。 */
    private final BigDecimal amount;

    /**
     * 审计轨迹：每一次尝试（无论成功失败）都追加一条。
     *
     * <p>用 {@link ArrayList} 而不是 {@code LinkedList}：只做尾部追加和整体遍历，
     * 这正是 ArrayList 的强项；LinkedList 在这个场景下每个节点多一次指针跳转，
     * 还多占内存。
     */
    private final List<HistoryEntry> history = new ArrayList<>();

    /**
     * 当前状态。
     *
     * <p><b>{@code private} 且没有 setter</b>——只有 {@link #transition} 能改它。
     * 初始化在 {@link PendingState#INSTANCE}：新订单一律从"待支付"开始，
     * 这本身也是一条业务规则，写死在构造器里比写在文档里可靠。
     */
    private OrderState state = PendingState.INSTANCE;

    /** 是否已付款。 */
    private boolean paid;

    /** 物流单号，发货后才有。 */
    private String trackingNo;

    /** 是否已签收。 */
    private boolean delivered;

    /** 取消原因，取消后才有。 */
    private String cancelReason;

    /**
     * 创建一张订单。
     *
     * <p>金额用 {@code String} 而不是 {@code BigDecimal} 作参数，是为了让调用方
     * <b>没法传进来一个已经带误差的 double</b>。{@code new Order("2001", "0.1")}
     * 这样的调用是安全的；如果参数是 {@code double}，调用方写
     * {@code new Order("2001", 0.1)} 的那一刻误差就已经进去了。
     *
     * @param orderNo 订单号，非空；数字部分会用来生成物流单号
     * @param amount  订单金额的字面值，例如 {@code "299.00"}
     * @throws IllegalArgumentException 订单号为空或金额非法
     */
    public Order(String orderNo, String amount) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        this.orderNo = orderNo;
        this.amount = Money.of(amount);
    }

    // ==================================================================
    // 对外：四个业务动作
    // 注意这四行里没有任何状态判断——判断全在状态对象里
    // ==================================================================

    /**
     * 买家支付。
     *
     * @return 转移结果；重复支付时返回带原因的拒绝
     */
    public TransitionResult pay() {
        return transition(OrderAction.PAY);
    }

    /**
     * 卖家发货。
     *
     * @return 转移结果
     */
    public TransitionResult ship() {
        return transition(OrderAction.SHIP);
    }

    /**
     * 买家确认收货。
     *
     * @return 转移结果
     */
    public TransitionResult confirm() {
        return transition(OrderAction.CONFIRM);
    }

    /**
     * 取消订单。
     *
     * <p>这是本示例里最值得看的一个方法：同样一行 {@code cancel()}，
     * 在待支付下只释放库存，在已支付下要原路退款，在已发货下被拒绝。
     * <b>调用方不需要写任何分支，三种后果全部由状态吸收。</b>
     *
     * @return 转移结果
     */
    public TransitionResult cancel() {
        return transition(OrderAction.CANCEL);
    }

    // ==================================================================
    // 核心：唯一的状态写入口
    // ==================================================================

    /**
     * 执行一次动作：先问状态，状态同意才切换，然后把这一步记进轨迹。
     *
     * <p>这四步的顺序不能换：
     * <ol>
     *   <li><b>先校验</b>——{@code allowedActions()} 里没有这个动作，
     *       直接返回带原因的拒绝，<b>不进入</b> {@code accept()}。
     *       这一步是必需的：终态的动作集合是空的，如果不先拦截，
     *       {@code accept()} 就会撞进 {@code unreachable} 抛异常，
     *       把一次正常的业务拒绝变成程序崩溃。</li>
     *   <li><b>再问</b>——{@code state.accept()}。此时状态可以写入业务数据
     *       （付款标记、物流单号），也可以自己返回拒绝。</li>
     *   <li><b>然后切</b>——只有 {@code success} 为真才改 {@code state}。
     *       这一行是整个类最重要的不变量：<b>失败绝不转移</b>。</li>
     *   <li><b>最后记并返回</b>——把同一个不可变结果对象既存进轨迹、又交给调用方，
     *       保证调用方看到的和轨迹里存的<b>必然一致</b>。</li>
     * </ol>
     *
     * <p><b>为什么不用异常来处理"状态不允许"？</b>
     * 详见 {@link TransitionResult} 的类注释。一句话：用户点了灰掉的按钮，
     * 是正常业务分支，不是程序故障。
     *
     * @param action 动作
     * @return 转移结果
     */
    private TransitionResult transition(OrderAction action) {
        Objects.requireNonNull(action, "动作不能为 null");

        TransitionResult result;
        if (state.allowedActions().contains(action)) {
            result = state.accept(action, this);
        } else {
            result = TransitionResult.rejected(action, state,
                    "「" + state.displayName() + "」下不能「" + action.label()
                            + "」；当前允许：" + describeAllowed());
        }

        if (result.success()) {
            state = result.to();
        }
        history.add(HistoryEntry.next(history.size(), result));
        return result;
    }

    /**
     * 把当前允许的动作拼成一句提示，顺序按 {@link OrderAction} 的声明顺序固定。
     *
     * <p><b>为什么不直接 {@code allowedActions().toString()}？</b>
     * 因为 {@code Set.of(...)} 的迭代顺序是<b>不保证</b>的。
     * 同一台机器上可能碰巧稳定，换一个 JDK 版本就可能变——
     * 那种"昨天跑出来是‘支付、取消’，今天变成‘取消、支付’"的变化
     * 会让文档里的真实输出对不上。
     *
     * <p>按枚举声明顺序遍历，顺序就<b>由语言规范保证</b>。
     *
     * <p>它之所以是 {@code public}，是因为<b>界面层也需要同一句话</b>：
     * 订单详情页要显示"当前可做：发货、取消"，前端不该再把这个逻辑写一遍。
     * 业务规则只能有一个出处，否则两份规则迟早不一致。
     *
     * @return 形如 {@code 支付、取消} 或 {@code （无，终态）} 的提示
     */
    public String describeAllowed() {
        StringBuilder sb = new StringBuilder();
        for (OrderAction candidate : OrderAction.values()) {
            if (state.allowedActions().contains(candidate)) {
                if (!sb.isEmpty()) {
                    sb.append("、");
                }
                sb.append(candidate.label());
            }
        }
        return sb.isEmpty() ? "（无，终态）" : sb.toString();
    }

    // ==================================================================
    // 供状态对象回写业务数据
    // 这些方法故意不加 public：它们不是业务 API，是状态机的内部协议
    // ==================================================================

    /**
     * 标记已付款。只允许 {@link PendingState} 在成功支付后调用。
     *
     * <p><b>为什么不加 {@code public}？</b> 因为外部代码不该说
     * "把这张单标记成已付款"——它应该说"支付"（{@link #pay()}）。
     * 前者跳过了一切校验，后者会完整走一遍状态机。
     *
     * <p>包内可见（无修饰符）不是绝对安全：同一个包里的 {@code Client} 也能调。
     * 这是 Java 包级封装的已知限制，本项目的处理方式是
     * <b>用命名和文档把"内部协议"标出来</b>，而不是假装它不存在。
     * 如果确实需要强隔离，把状态类放进 {@code Order} 的嵌套类即可——
     * 但那样会牺牲包结构的可读性，得不偿失。
     */
    void markPaid() {
        this.paid = true;
    }

    /**
     * 生成并记住物流单号。
     *
     * <p>单号格式 {@code SF + 订单号里的数字}，例如 {@code O-2001 → SF2001}。
     * 用订单号派生而不是用时间戳或随机数，是为了让 Demo 的输出
     * <b>每次运行完全一致</b>——否则文档里的"真实输出"第二天就失效了。
     *
     * <p>重复调用会抛异常而不是静默返回旧单号：说明有人绕过了状态机
     * 连续发两次货，这是结构性错误，应该立刻暴露。
     *
     * @return 新生成的物流单号
     * @throws IllegalStateException 单号已经生成过
     */
    String issueTrackingNo() {
        if (trackingNo != null) {
            throw new IllegalStateException(
                    "订单 " + orderNo + " 的物流单号已经生成过：" + trackingNo
                            + "；重复发货说明有代码绕过了状态机");
        }
        this.trackingNo = "SF" + digitsOf(orderNo);
        return trackingNo;
    }

    /** 标记已签收。只允许 {@link ShippedState} 在确认收货后调用。 */
    void markDelivered() {
        this.delivered = true;
    }

    /**
     * 记录取消原因。
     *
     * <p>原因由<b>发起取消的那个状态</b>给出，因为只有它知道上下文：
     * 待支付时是"未付款，释放库存"，已支付时是"需原路退款"。
     * 订单本身不该去猜——它只负责存。
     *
     * @param reason 取消原因
     */
    void markCancelled(String reason) {
        Objects.requireNonNull(reason, "取消原因不能为 null");
        this.cancelReason = reason;
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** @return 订单号 */
    public String orderNo() {
        return orderNo;
    }

    /** @return 订单金额 */
    public BigDecimal amount() {
        return amount;
    }

    /** @return 当前状态 */
    public OrderState state() {
        return state;
    }

    /**
     * 当前状态下允许的动作，直接转发给状态。
     *
     * <p>界面层用它来决定按钮的禁用状态，<b>不需要自己再判断一遍状态</b>：
     * <pre>
     *   for (OrderAction action : OrderAction.values()) {
     *       button(action).setEnabled(order.allowedActions().contains(action));
     *   }
     * </pre>
     * 这段渲染代码永远不会和业务规则不一致，因为它读的就是业务规则本身。
     *
     * @return 不可变的动作集合
     */
    public Set<OrderAction> allowedActions() {
        return state.allowedActions();
    }

    /**
     * 完整的审计轨迹。
     *
     * <p>返回 {@link List#copyOf} 而不是直接返回 {@code history}：
     * 后者会把内部的可变列表交到调用方手里，调用方一个 {@code clear()}
     * 就把审计记录抹掉了。{@code List.copyOf} 做的是<b>真拷贝</b>，
     * 之后内部再怎么追加都不影响已经拿出去的那份快照。
     *
     * <p>注意它不是 {@code Collections.unmodifiableList(history)}——
     * 那个只是<b>视图</b>，内部一改，外面看到的也跟着变，
     * 拿来做"某一时刻的快照"是错的。
     *
     * @return 不可变的轨迹快照
     */
    public List<HistoryEntry> history() {
        return List.copyOf(history);
    }

    /** @return 是否已付款 */
    public boolean isPaid() {
        return paid;
    }

    /** @return 物流单号；未发货时为空 */
    public String trackingNo() {
        return trackingNo;
    }

    /** @return 是否已签收 */
    public boolean isDelivered() {
        return delivered;
    }

    /** @return 取消原因；未取消时为空 */
    public String cancelReason() {
        return cancelReason;
    }

    /**
     * 业务数据的一行摘要，供演示输出使用。
     *
     * <p>只有当数据存在时才出现在摘要里——这样"未发货"和"发货但单号为空"
     * 一眼就能区分开，而不是都打印成一个空字符串。
     *
     * @return 形如 {@code 已付款 ￥299.00 | 单号 SF2001 | 已签收} 的摘要
     */
    public String describeData() {
        List<String> parts = new ArrayList<>();
        parts.add(paid ? "已付款 " + Money.yuan(amount) : "未付款");
        if (trackingNo != null) {
            parts.add("单号 " + trackingNo);
        }
        if (delivered) {
            parts.add("已签收");
        }
        if (cancelReason != null) {
            parts.add("取消原因：" + cancelReason);
        }
        return String.join(" | ", parts);
    }

    /**
     * 从订单号里提取数字，用于生成物流单号。
     *
     * @param orderNo 订单号
     * @return 数字部分；一个数字都没有时退回原订单号
     */
    private static String digitsOf(String orderNo) {
        String digits = orderNo.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? orderNo : digits;
    }
}
