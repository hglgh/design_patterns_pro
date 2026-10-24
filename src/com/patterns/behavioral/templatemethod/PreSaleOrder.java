package com.patterns.behavioral.templatemethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 具体子类 ③：预售订单（新品首发、众筹商品、定制商品……）。
 *
 * <p><b>这个类是本次重构新增的，原项目没有。它承担两个教学任务。</b>
 *
 * <h2>任务 1：验证开闭原则——新增一种订单类型，父类一行都不用改</h2>
 *
 * <p>加这个类的过程中，被修改的文件是：
 *
 * <table border="1">
 *   <caption>新增 PreSaleOrder 的改动范围</caption>
 *   <tr><th>文件</th><th>改动</th></tr>
 *   <tr><td>{@code PreSaleOrder.java}</td><td><b>新建</b></td></tr>
 *   <tr><td>{@code Client.java}</td><td>加一段演示代码（客户端本来就要用新类型）</td></tr>
 *   <tr><td>{@code OrderProcessTemplate.java}</td><td><b>0 改动</b> ← 关键</td></tr>
 *   <tr><td>{@code OrderContext.java}</td><td><b>0 改动</b> ← 关键</td></tr>
 *   <tr><td>{@code PhysicalProductOrder.java}</td><td><b>0 改动</b> ← 关键</td></tr>
 *   <tr><td>{@code VirtualProductOrder.java}</td><td><b>0 改动</b> ← 关键</td></tr>
 * </table>
 *
 * <p><b>父类和已有的两个兄弟类完全没被碰过</b>，这就是「对扩展开放、对修改关闭」。
 *
 * <p><b>对照：如果父类里用了 {@code instanceof} 判断会怎样？</b>
 * 那新增预售订单就必须<b>修改父类</b>的通知逻辑：
 * <pre>
 *   // ❌ 反模式：每加一种订单类型就要改这里
 *   String label = (this instanceof VirtualProductOrder) ? "下载链接"
 *                : (this instanceof PreSaleOrder) ? "预计到货日"     // ← 被迫修改父类
 *                : "物流单号";
 * </pre>
 * 父类会变成一个「知道所有子类」的上帝类，而且改动它会波及全部订单类型的回归测试。
 *
 * <h2>任务 2：完整演示「四种方法角色」的子类侧用法</h2>
 *
 * <p>前两个子类各自只演示了一部分，本类把四种角色全用上了：
 *
 * <table border="1">
 *   <caption>PreSaleOrder 对父类各方法的使用情况</caption>
 *   <tr><th>父类方法</th><th>角色</th><th>本类的处理</th></tr>
 *   <tr><td>{@code processOrder()}</td><td>模板方法</td><td><b>不能改</b>（final，想改也改不了）</td></tr>
 *   <tr><td>{@code pay()}</td><td>抽象方法</td><td><b>必须实现</b> → 只付 30% 定金</td></tr>
 *   <tr><td>{@code deliver()}</td><td>抽象方法</td><td><b>必须实现</b> → 给出预计到货日</td></tr>
 *   <tr><td>{@code notifyCustomer()}</td><td>具体方法</td><td><b>重写</b>，但<b>调用 super 复用父类逻辑</b></td></tr>
 *   <tr><td>{@code checkStock()}</td><td>具体方法</td><td>不重写（钩子已让它不被调用）</td></tr>
 *   <tr><td>{@code needsStockCheck()}</td><td>钩子（返回值型）</td><td><b>重写 → false</b></td></tr>
 *   <tr><td>{@code deliveryProofLabel()}</td><td>钩子（返回值型）</td><td><b>重写 → 「预计到货日」</b></td></tr>
 *   <tr><td>{@code beforePay()}</td><td>钩子（空实现型）</td><td><b>重写</b> → 插入定金比例校验（由模板方法调用）</td></tr>
 * </table>
 *
 * <h2>预售业务规则说明</h2>
 *
 * <p>预售商品的三个特殊性，正好各自对应一种「方法角色」：
 * <ol>
 *   <li><b>不占现货库存</b>（卖的是未来的产能）→ 重写<b>钩子</b> {@code needsStockCheck()}；</li>
 *   <li><b>只付 30% 定金</b>，尾款到货后付 → 实现<b>抽象方法</b> {@code pay()}；</li>
 *   <li><b>通知内容要多一句尾款提醒</b>，但主体和别的订单一样
 *       → 重写<b>具体方法</b> {@code notifyCustomer()} 并调用 {@code super}。</li>
 * </ol>
 *
 * <p><b>第 3 点是本类最值得学的地方</b>：
 * 「大部分逻辑复用父类，只追加一点自己的东西」——这是继承体系里最常见的需求，
 * 而 {@code super.notifyCustomer(ctx)} 就是标准解法。
 *
 * @author design-patterns-pro
 * @see OrderProcessTemplate#beforePay() 空钩子的用途
 */
public class PreSaleOrder extends OrderProcessTemplate {

    /** 定金比例：30%。真实项目里这个值来自商品配置表。 */
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.30");

    /**
     * 定金最低金额：1 元。低于这个值就不走预售流程。
     *
     * <p><b>为什么需要这个下限？</b>
     * 因为支付渠道（微信/支付宝/银联）对单笔交易有最低金额限制，
     * 几分钱的定金实际上收不上来；而且为几分钱走一整套预售流程
     * （定金 → 排产 → 到货 → 尾款）在业务上毫无意义。
     *
     * <p><b>一个真实的调试经历</b>：这个阈值最初写的是 {@code deposit.signum() <= 0}
     * （即「定金为 0 才拦」），结果拿 ￥0.10 的订单测试时，
     * 定金算出来是 {@code 0.03 > 0}，<b>校验直接通过了</b>，根本没拦住。
     * 是运行 Demo 时看到「定金 ￥0.03」这行输出才发现的。
     * <b>“金额为 0”和“金额小得不合理”是两回事，阈值要按业务定，不能按数学定。</b>
     */
    private static final BigDecimal MIN_DEPOSIT = BigDecimal.ONE;

    /** 预计发货天数：下单后 30 天。 */
    private static final int ESTIMATED_DELIVERY_DAYS = 30;

    /** 尾款支付期限（天）。到货后 3 日内必须付清，否则订单取消、定金不退。 */
    private static final int BALANCE_DEADLINE_DAYS = 3;

    /** 到货日格式。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 钩子重写（返回值型）：<b>预售不检查现货库存。</b>
     *
     * <p>原因：预售卖的是「未来的产能」，不是仓库里的现货。
     * 工厂还没生产出来，查现货库存只会得到 0，
     * 于是流程会在步骤 ① 就被<b>错误地中止</b>——这正是原项目那种设计会导致的 bug。
     */
    @Override
    protected boolean needsStockCheck() {
        return false;
    }

    /**
     * 钩子重写（返回值型）：交付凭证的称呼改为「预计到货日」。
     *
     * <p>预售订单在「发货」这一步交付给用户的不是实物，而是<b>一个承诺的日期</b>。
     */
    @Override
    protected String deliveryProofLabel() {
        return "预计到货日";
    }

    /**
     * 钩子重写（空实现型）：在支付之前插入定金比例校验。
     *
     * <p><b>这是 {@code beforePay()} 这个空钩子唯一的使用者</b>，
     * 它演示了空钩子的正确用法：<b>父类预留位置并由模板方法保证调用，需要的子类自己填。</b>
     *
     * <p>{@code PhysicalProductOrder} 和 {@code VirtualProductOrder} 都没有重写它，
     * 所以它们的 {@code beforePay()} 是空操作——<b>不重写也完全正常运行，这就是「钩子」和「抽象方法」的本质区别。</b>
     *
     * <p><b>本方法不需要也不应该被 {@link #pay(OrderContext)} 调用</b>。
     * 它由父类的模板方法在 {@code pay()} 之前调用，并在调用后立即检查 {@code isAborted()}。
     * 详见 {@link OrderProcessTemplate#beforePay(OrderContext)} 的说明。
     */
    @Override
    protected void beforePay(OrderContext ctx) {
        BigDecimal deposit = depositOf(ctx.getAmount());
        // compareTo < 0 而不是 signum() <= 0：拦的是「定金低于 1 元」，不只是「定金为 0」。
        // 原因见 MIN_DEPOSIT 的注释。
        if (deposit.compareTo(MIN_DEPOSIT) < 0) {
            // 订单金额太小，算出来的定金低于支付渠道的最低限额，这种订单不该走预售流程。
            ctx.abort("定金 ￥" + deposit.toPlainString() + " 低于最低限额 ￥"
                    + MIN_DEPOSIT.toPlainString() + "，不适用预售（订单金额 ￥"
                    + ctx.getAmount().toPlainString() + " × " + DEPOSIT_RATE + "）");
            System.out.println("  │  [钩子 beforePay] 定金校验失败：￥" + deposit.toPlainString()
                    + " < 最低限额 ￥" + MIN_DEPOSIT.toPlainString());
        } else {
            ctx.record("【钩子 beforePay】定金校验通过，应付定金 ￥" + deposit.toPlainString());
            System.out.println("  │  [钩子 beforePay] 定金校验通过，应付 ￥"
                    + deposit.toPlainString());
        }
    }

    /**
     * 支付预售订单：<b>只付 30% 定金。</b>
     *
     * <p><b>这一步无法与实物/虚拟商品共用实现</b>——那两种都是全额支付。
     * 这就是它必须是抽象方法（而不是父类的具体方法）的原因。
     */
    @Override
    protected void pay(OrderContext ctx) {
        // 【注意】这里既不用调 beforePay(ctx)，也不用写 if (ctx.isAborted()) return;
        // 钩子由模板方法统一调用，abort 也由模板方法统一检测——
        // 定金校验失败时，本方法【根本不会被执行】。
        // 这正是「把钩子调用放进模板方法」的收益：子类不需要写任何防御代码。
        BigDecimal deposit = depositOf(ctx.getAmount());
        BigDecimal balance = ctx.getAmount().subtract(deposit)
                .setScale(2, RoundingMode.HALF_UP);
        String paymentNo = generateSerialNo("PRE");

        ctx.setPaymentNo(paymentNo);
        ctx.record("② 支付 → 定金 ￥" + deposit.toPlainString()
                + "（尾款 ￥" + balance.toPlainString() + " 到货后付），流水号 " + paymentNo);

        System.out.println("  │  ② 支付：定金 ￥" + deposit.toPlainString()
                + "（" + DEPOSIT_RATE.multiply(BigDecimal.TEN).stripTrailingZeros() + " 成），流水号 " + paymentNo);
        System.out.println("  │        └ 尾款 ￥" + balance.toPlainString() + " 待到货后支付");
    }

    /**
     * 交付预售订单：<b>给出预计到货日。</b>
     *
     * <p>注意这一步同样依赖 {@code ctx.getPaymentNo()}——
     * 到货通知要带上定金流水号，用户才能对上账。
     */
    @Override
    protected void deliver(OrderContext ctx) {
        String eta = LocalDateTime.now().plusDays(ESTIMATED_DELIVERY_DAYS).format(DATE_FMT);

        ctx.setDeliveryProof(eta);
        ctx.record("③ 交付 → 已登记生产排期，预计到货 " + eta
                + "（关联定金流水 " + ctx.getPaymentNo() + "）");

        System.out.println("  │  ③ 交付：已登记生产排期，预计到货日 " + eta);
        System.out.println("  │        └ 关联定金流水 " + ctx.getPaymentNo());
    }

    /**
     * 重写具体方法：<b>调用 {@code super} 复用父类逻辑，再追加预售特有的尾款提醒。</b>
     *
     * <p><b>这是本类最值得学的一段。</b>
     *
     * <p>预售订单的通知内容 = 父类的标准通知 + 一句尾款提醒。
     * 三种写法对比：
     *
     * <pre>
     *   // ❌ 写法 1：完全不调 super，把父类逻辑抄一遍
     *   protected void notifyCustomer(OrderContext ctx) {
     *       String content = "尊敬的客户，您的订单 " + ctx.getOrderNo() + ... ;   // 抄父类的 5 行
     *       content += "；尾款提醒...";
     *   }
     *   // 问题：父类改了通知模板，本类不会跟着改，两份代码逐渐不一致。
     *
     *   // ❌ 写法 2：把父类的 notifyCustomer() 改成抽象方法
     *   // 问题：逼着 PhysicalProductOrder 和 VirtualProductOrder 各写一遍相同的逻辑。
     *
     *   // ✅ 写法 3（本类采用）：调 super 再追加
     *   protected void notifyCustomer(OrderContext ctx) {
     *       super.notifyCustomer(ctx);                          // 复用父类
     *       ctx.setNotificationContent(ctx.getNotificationContent() + "；" + extra);
     *   }
     * </pre>
     *
     * <p><b>写法 3 的关键是：追加的内容依赖父类的产出。</b>
     * {@code super.notifyCustomer(ctx)} 会写入 {@code ctx.notificationContent}，
     * 本方法读出来拼接后再写回去。<b>这个「读-改-写」的顺序不能颠倒</b>——
     * 如果先拼后调 super，父类会把本类的内容整个覆盖掉。
     */
    @Override
    protected void notifyCustomer(OrderContext ctx) {
        // 第一步：让父类完成标准通知（它会写入 ctx.notificationContent）
        super.notifyCustomer(ctx);

        // 第二步：在父类结果的基础上追加尾款提醒
        BigDecimal balance = ctx.getAmount().subtract(depositOf(ctx.getAmount()))
                .setScale(2, RoundingMode.HALF_UP);
        String deadline = LocalDateTime.now()
                .plusDays(ESTIMATED_DELIVERY_DAYS + BALANCE_DEADLINE_DAYS).format(DATE_FMT);

        String extra = "尾款 ￥" + balance.toPlainString() + " 请于 " + deadline
                + " 前支付，逾期订单取消且定金不退";

        ctx.setNotificationContent(ctx.getNotificationContent() + "；" + extra);
        ctx.record("④+ 追加尾款提醒 → " + extra);

        System.out.println("  │     + 追加提醒：" + extra);
        System.out.println("  │     ↑ 这一行由子类追加，上一行由 super.notifyCustomer() 产生");
    }

    /**
     * 计算定金金额：订单金额 × 30%，四舍五入到分。
     *
     * <p>抽成私有方法是因为 {@code pay()} 和 {@code notifyCustomer()} 都要用，
     * 且两处的结果<b>必须完全一致</b>（否则用户看到的尾款和实际要付的对不上）。
     * <b>同一个业务数字只能有一个计算入口</b>，这是消除「数据不一致 bug」的基本手段。
     */
    private BigDecimal depositOf(BigDecimal amount) {
        return amount.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
