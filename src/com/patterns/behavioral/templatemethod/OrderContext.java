package com.patterns.behavioral.templatemethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单流程上下文：贯穿整个订单处理流程的<b>数据载体</b>。
 *
 * <p><b>为什么需要这个类？这是本次重构最关键的一处改动。</b>
 *
 * <p>原项目的四个步骤全是 {@code protected void xxx()} + {@code System.out.println}：
 * <pre>
 *   protected void checkStock() { System.out.println("检查库存"); }
 *   protected abstract void pay();
 *   protected abstract void deliver();
 *   protected void notifyCustomer() { System.out.println("通知客户订单处理完成"); }
 * </pre>
 *
 * <p>四个方法<b>全是 void，互相之间没有任何数据传递</b>。这带来一个致命的教学问题：
 *
 * <blockquote>
 * <b>既然步骤之间没有数据依赖，那把顺序调换一下会怎样？</b><br>
 * 答案是：什么都不会发生，输出只是换了个顺序。<br>
 * 于是 {@code public final void processOrder()} 里那个 <b>{@code final} 就失去了意义</b>
 * ——读者无法理解「为什么这个顺序不允许子类修改」。
 * </blockquote>
 *
 * <p>真实的订单流程是<b>有严格数据依赖</b>的：
 * <pre>
 *   ① 检查库存  ──产出──▶ 可用库存数
 *                              │
 *                              ▼ （库存 ≤ 0 就必须中止，否则超卖）
 *   ② 支付      ──产出──▶ 支付流水号
 *                              │
 *                              ▼ （财务对账要求发货记录必须带支付流水号）
 *   ③ 发货      ──产出──▶ 物流单号 / 下载链接
 *                              │
 *                              ▼ （通知内容必须包含交付凭证，否则用户不知道去哪收货）
 *   ④ 通知客户  ──读取──▶ 上面所有产出
 * </pre>
 *
 * <p><b>把 ② 和 ③ 调换会怎样？</b>发货时拿不到支付流水号，
 * 财务对账时会出现「货已发出但不知道收了谁的钱」，这是真实的资金风险。
 *
 * <p><b>把 ① 挪到最后会怎样？</b>库存不足时用户已经付了钱，
 * 只能退款——这就是「超卖」事故，双十一最常见的故障之一。
 *
 * <p>有了 {@code OrderContext}，这些依赖关系就变成了<b>可以被运行验证的事实</b>：
 * 调换顺序会立刻在输出里看到 {@code null} 或者错误的结果。
 *
 * <p><b>为什么不用一堆散参数传递？</b>
 * <pre>
 *   // ❌ 参数会越来越多，签名失控
 *   protected abstract void pay(String orderNo, BigDecimal amount, int stock);
 *   protected abstract void deliver(String orderNo, BigDecimal amount, int stock, String paymentNo);
 * </pre>
 * 每加一步就要给后面所有步骤的方法签名加参数，
 * 而且<b>所有子类都要跟着改</b>——这正是模板方法模式最忌讳的（父类改动波及全部子类）。
 *
 * <p>用一个上下文对象承载，<b>新增字段不需要改任何方法签名</b>。
 * 这个思路在真实框架里非常常见：
 * <ul>
 *   <li>Spring 的 {@code RequestContextHolder} / {@code ApplicationContext}；</li>
 *   <li>Servlet 的 {@code HttpServletRequest}（贯穿整个过滤器链）；</li>
 *   <li>工作流引擎（Activiti/Flowable）的流程变量。</li>
 * </ul>
 *
 * <p><b>这个类是「设计出来的可变容器」，不是「不可变值对象」。</b>
 * 这一点和策略模式里的 {@code PriceCalculator}（刻意不可变）形成对照：
 * <ul>
 *   <li>策略模式的 Context 只是<b>持有</b>一个策略，本身不需要变，所以做成不可变；</li>
 *   <li>模板方法模式的 Context 是<b>流程的产物累积器</b>，每走一步就多一个字段，必须可变。</li>
 * </ul>
 * <b>可变性应该由职责决定，不是由个人偏好决定。</b>
 *
 * <p>为了控制可变带来的风险，本类做了两件事：
 * <ol>
 *   <li>{@code orderNo}/{@code productName}/{@code amount} 三个<b>输入</b>字段是 {@code final}，
 *       只能通过构造器设置，流程中不可篡改；</li>
 *   <li>{@code trace}（流程轨迹）对外只暴露 {@link Collections#unmodifiableList} 视图，
 *       外部不能往里塞东西。</li>
 * </ol>
 *
 * @author design-patterns-pro
 * @see OrderProcessTemplate 使用本类在各步骤之间传递数据
 */
public class OrderContext {

    // ==================================================================
    // 输入字段（final，流程中不可变）
    // ==================================================================

    /** 订单号，全流程的唯一标识。 */
    private final String orderNo;

    /** 商品名称。 */
    private final String productName;

    /** 订单金额。用 BigDecimal 而不是 double，原因见策略模式的 README 第 6.2 节。 */
    private final BigDecimal amount;

    // ==================================================================
    // 产出字段（由各步骤依次写入，体现步骤间的数据依赖）
    // ==================================================================

    /** ① 检查库存的产出：可用库存数量。{@code -1} 表示「该商品不需要检查库存」。 */
    private int availableStock = -1;

    /** ② 支付的产出：支付流水号。为 {@code null} 表示尚未支付。 */
    private String paymentNo;

    /** ③ 发货的产出：交付凭证（实物商品是物流单号，虚拟商品是下载链接）。 */
    private String deliveryProof;

    /** ④ 通知的产出：实际发送给客户的通知内容。 */
    private String notificationContent;

    /** 流程是否被中止（例如库存不足）。 */
    private boolean aborted;

    /** 中止原因。 */
    private String abortReason;

    /**
     * 流程轨迹：记录每一步实际执行了什么。
     *
     * <p><b>为什么要有这个字段？</b>
     * 因为它让「父类控制流程、子类填充细节」这件事变得<b>可以验证</b>。
     * 运行结束后打印出来，能清楚看到：
     * <ul>
     *   <li>哪些步骤被执行了、哪些被钩子跳过了；</li>
     *   <li>执行顺序是否符合模板方法定义的顺序；</li>
     *   <li>每一步是父类实现的还是子类重写的。</li>
     * </ul>
     * 真实系统里这个字段对应的是<b>操作日志表</b>或<b>链路追踪 span</b>。
     */
    private final List<String> trace = new ArrayList<>();

    /**
     * @param orderNo     订单号，不能为空
     * @param productName 商品名称，不能为空
     * @param amount      订单金额，不能为 null 或负数
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public OrderContext(String orderNo, String productName, BigDecimal amount) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("订单金额不能为 null 或负数，实际传入：" + amount);
        }
        this.orderNo = orderNo;
        this.productName = productName;
        this.amount = amount;
    }

    /**
     * 记录一条流程轨迹。
     *
     * <p><b>为什么这个方法由父类模板方法调用，而不是每个步骤自己调？</b>
     * 因为「记录轨迹」是<b>所有步骤都要做的公共动作</b>，
     * 如果让每个子类自己调，新增子类时很容易忘记——
     * 这正是模板方法模式要解决的问题：<b>把公共动作固定在父类里</b>。
     *
     * @param step 步骤描述
     */
    public void record(String step) {
        trace.add(step);
    }

    /**
     * 返回不可修改的流程轨迹视图。
     *
     * <p>直接返回 {@code trace} 字段的话，外部可以 {@code clear()} 掉整条轨迹，
     * 审计日志就没了。<b>返回内部集合时一定要包一层 unmodifiable 视图。</b>
     */
    public List<String> getTrace() {
        return Collections.unmodifiableList(trace);
    }

    /**
     * 标记流程中止。
     *
     * <p>中止后模板方法会<b>立即返回</b>，不再执行后续步骤。
     * 这是原项目完全没有的能力——原项目即使库存为 0 也会走完支付、发货、通知全流程。
     *
     * @param reason 中止原因
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
        record("【中止】" + reason);
    }

    // ==================================================================
    // getter / setter
    // ==================================================================

    public String getOrderNo() {
        return orderNo;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getDeliveryProof() {
        return deliveryProof;
    }

    public void setDeliveryProof(String deliveryProof) {
        this.deliveryProof = deliveryProof;
    }

    public String getNotificationContent() {
        return notificationContent;
    }

    public void setNotificationContent(String notificationContent) {
        this.notificationContent = notificationContent;
    }

    public boolean isAborted() {
        return aborted;
    }

    public String getAbortReason() {
        return abortReason;
    }

    @Override
    public String toString() {
        return "OrderContext[orderNo=" + orderNo
                + ", product=" + productName
                + ", amount=" + amount
                + ", stock=" + availableStock
                + ", paymentNo=" + paymentNo
                + ", proof=" + deliveryProof
                + (aborted ? ", ABORTED=" + abortReason : "")
                + "]";
    }
}
