package com.patterns.behavioral.templatemethod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 具体子类 ②：虚拟商品订单（电子书、激活码、在线课程、话费充值……）。
 *
 * <p><b>这个类的存在价值，就是演示「钩子方法」的用途。</b>
 *
 * <p>它重写了<b>两个钩子</b>，没有重写任何具体方法：
 *
 * <table border="1">
 *   <caption>VirtualProductOrder 对父类各方法的使用情况</caption>
 *   <tr><th>父类方法</th><th>角色</th><th>本类的处理</th></tr>
 *   <tr><td>{@code processOrder()}</td><td>模板方法</td><td><b>不能改</b>（final）</td></tr>
 *   <tr><td>{@code pay()}</td><td>抽象方法</td><td><b>必须实现</b> → 全额支付 + 校验充值账号</td></tr>
 *   <tr><td>{@code deliver()}</td><td>抽象方法</td><td><b>必须实现</b> → 邮件发送下载链接</td></tr>
 *   <tr><td>{@code checkStock()}</td><td>具体方法</td><td>不重写（<b>而且根本不会被调用</b>）</td></tr>
 *   <tr><td>{@code notifyCustomer()}</td><td>具体方法</td><td>不重写，复用父类的通知逻辑</td></tr>
 *   <tr><td>{@code needsStockCheck()}</td><td><b>钩子</b></td><td><b>重写 → false</b>，跳过库存检查</td></tr>
 *   <tr><td>{@code deliveryProofLabel()}</td><td><b>钩子</b></td><td><b>重写 → 「下载链接」</b></td></tr>
 * </table>
 *
 * <h2>关键点 1：修正了原项目的一个业务错误</h2>
 *
 * <p>原项目把 {@code checkStock()} 无条件写在模板方法里，于是<b>虚拟商品也会「检查库存」</b>：
 * <pre>
 *   处理虚拟商品订单：
 *   检查库存                    ← 这一行在业务上是【错的】
 *   支付虚拟商品的订单
 *   通过邮件发送虚拟商品
 *   通知客户订单处理完成
 * </pre>
 *
 * <p>电子书、激活码、在线课程这类虚拟商品<b>库存是无限的</b>——
 * 一份 PDF 可以卖给一百万个人，不存在「卖完了」的情况。
 * 让它输出「检查库存」，会给读者传达一个<b>错误的业务认知</b>。
 *
 * <p>本类通过重写钩子 {@link #needsStockCheck()} 返回 {@code false}，
 * 让模板方法<b>跳过</b>这一步，并在流程轨迹里明确记录「跳过」而不是「通过」
 * （这两者在审计时的责任归属完全不同）。
 *
 * <p>实测输出对比：
 * <pre>
 *   实物商品：│  ① 检查库存：可用 49 件
 *   虚拟商品：│  [钩子] 本类型商品无需检查库存，跳过步骤 ①
 * </pre>
 *
 * <h2>关键点 2：只改一个称呼，复用了整个通知逻辑</h2>
 *
 * <p>{@link #deliveryProofLabel()} 返回「下载链接」而不是父类默认的「物流单号」。
 * 于是父类 {@code notifyCustomer()} 的<b>整段拼接逻辑原样复用</b>，
 * 输出的消息自动变成：
 * <pre>
 *   ④ 通知客户：尊敬的客户，您的订单 ORD-V-001（Java 编程思想电子书）已处理完成，
 *               下载链接：https://download.example.com/ebook/xxx
 * </pre>
 *
 * <p><b>如果不用钩子，两种替代方案都更差</b>：
 * <ul>
 *   <li>把 {@code notifyCustomer()} 改成抽象方法 → 本类要重写<b>整段拼接逻辑</b>，
 *       只为改四个字，且和 {@code PhysicalProductOrder} 产生大段重复代码；</li>
 *   <li>在父类里写 {@code if (this instanceof VirtualProductOrder)} →
 *       <b>这是模板方法模式最严重的反模式</b>：父类反过来依赖子类，
 *       每加一种订单类型都要改父类，彻底违反开闭原则。</li>
 * </ul>
 *
 * <blockquote>
 * <b>钩子的威力就在这里：它让子类只表达「差异点」，而不用重复「共同部分」。</b>
 * </blockquote>
 *
 * <h2>关键点 3：{@code instanceof} 判断为什么是反模式</h2>
 *
 * <p>初学者很容易在父类里这样写：
 * <pre>
 *   // ❌ 绝对不要这样
 *   protected void notifyCustomer(OrderContext ctx) {
 *       String label = (this instanceof VirtualProductOrder) ? "下载链接" : "物流单号";
 *       ...
 *   }
 * </pre>
 *
 * <p>三个致命问题：
 * <ol>
 *   <li><b>父类依赖子类</b>——违反依赖倒置原则。子类本来是「后加的」，
 *       现在却要求父类预先知道它们的存在；</li>
 *   <li><b>违反开闭原则</b>——新增 {@code PreSaleOrder} 时必须<b>修改</b>父类的这个 if-else；</li>
 *   <li><b>继承关系被破坏</b>——如果有人写了 {@code class EBookOrder extends VirtualProductOrder}，
 *       {@code instanceof} 判断会给出错误结果。</li>
 * </ol>
 *
 * <p><b>钩子方法是这个问题的正解</b>：父类调用 {@code deliveryProofLabel()}（它不认识任何子类），
 * 子类重写它来提供自己的答案。<b>控制流方向是「父类 → 子类」，依赖方向也是「子类 → 父类」，完全正常。</b>
 *
 * @author design-patterns-pro
 * @see OrderProcessTemplate#needsStockCheck() 钩子的详细设计说明
 * @see PreSaleOrder 另一个重写钩子的例子
 */
public class VirtualProductOrder extends OrderProcessTemplate {

    /**
     * 钩子重写：<b>虚拟商品不需要检查库存。</b>
     *
     * <p>原因见类注释「关键点 1」。
     *
     * <p><b>注意：重写钩子不需要调用 {@code super.needsStockCheck()}</b>——
     * 钩子的语义是「替换默认答案」，不是「在默认答案基础上追加」。
     * 这和 {@code notifyCustomer()} 那种「具体方法」不同（那种往往需要调 super 复用主体逻辑）。
     *
     * @return 恒为 {@code false}
     */
    @Override
    protected boolean needsStockCheck() {
        return false;
    }

    /**
     * 钩子重写：交付凭证的称呼改为「下载链接」。
     *
     * <p>只改这一个返回值，父类的整个 {@code notifyCustomer()} 逻辑原样复用。
     */
    @Override
    protected String deliveryProofLabel() {
        return "下载链接";
    }

    /**
     * 支付虚拟商品订单：全额支付，但<b>额外校验充值账号</b>。
     *
     * <p><b>这一步为什么不能和实物商品共用实现？</b>
     * 因为话费充值、游戏点卡这类虚拟商品，<b>充错账号是无法撤回的</b>——
     * 货（余额）已经进入别人的账户，退不回来。
     * 所以支付前必须校验账号格式，这是虚拟商品特有的风控要求。
     */
    @Override
    protected void pay(OrderContext ctx) {
        // 虚拟商品特有的前置校验：账号格式。
        // 【为什么放在子类而不是父类？】因为实物商品根本没有"充值账号"这个概念，
        // 放进父类会让 PhysicalProductOrder 也被迫处理一个它不关心的字段。
        // 【为什么不用 beforePay() 钩子？】因为这段校验是虚拟商品【自己】的支付逻辑的一部分，
        // 不是「所有订单都可能需要、只是默认不做」的扩展点。钩子用于后者。
        String account = extractAccount(ctx.getProductName());
        if (!isValidAccount(account)) {
            // 注意：这里用 abort() 而不是抛异常。
            // 「账号格式不对」是正常业务情况（用户填错了），不是程序错误。
            // 抛异常会让调用方被迫写 try-catch，而 abort() 让调用方用 if 判断，语义更准确。
            ctx.abort("充值账号格式非法：" + account);
            System.out.println("  │  ② 支付：账号校验失败 → " + account);
            return;
        }

        BigDecimal paid = ctx.getAmount().setScale(2, RoundingMode.HALF_UP);
        String paymentNo = generateSerialNo("VPAY");

        ctx.setPaymentNo(paymentNo);
        ctx.record("② 支付 → 全额 ￥" + paid.toPlainString()
                + "，账号 " + account + " 校验通过，流水号 " + paymentNo);

        System.out.println("  │  ② 支付：全额 ￥" + paid.toPlainString()
                + "，充值账号 " + account + " 已校验，流水号 " + paymentNo);
    }

    /**
     * 交付虚拟商品：<b>生成下载链接并写入上下文</b>。
     *
     * <p>这里同样用到了 {@code ctx.getPaymentNo()}——
     * 下载链接需要绑定支付流水号，防止链接被转发给未付款的人。
     * <b>这又是一处「顺序不可调换」的实际约束。</b>
     */
    @Override
    protected void deliver(OrderContext ctx) {
        // 用支付流水号做链接的签名，防止盗链（真实项目会用 HMAC 签名）
        String link = "https://dl.example.com/" + ctx.getOrderNo().toLowerCase()
                + "?token=" + ctx.getPaymentNo();

        ctx.setDeliveryProof(link);
        ctx.record("③ 交付 → 已生成下载链接（token 来自支付流水 " + ctx.getPaymentNo() + "）");

        System.out.println("  │  ③ 交付：已生成下载链接");
        System.out.println("  │        └ " + link);
        System.out.println("  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用");
    }

    /**
     * 从商品名里提取充值账号。纯 Demo 用的模拟逻辑。
     *
     * <p>真实项目里账号是订单的独立字段。这里用「商品名里 {@code @} 后面的部分」模拟，
     * 好处是<b>可以构造出校验失败的用例</b>来演示 abort 分支。
     */
    private String extractAccount(String productName) {
        int at = productName.indexOf('@');
        return (at >= 0 && at < productName.length() - 1)
                ? productName.substring(at + 1)
                : productName;
    }

    /**
     * 校验账号格式：至少 5 位，且不含空格。
     *
     * <p>故意写得很宽松——目的不是做真实的账号校验，
     * 而是让「校验失败 → abort」这条分支<b>可以被触发</b>。
     */
    private boolean isValidAccount(String account) {
        return account != null && account.length() >= 5 && !account.contains(" ");
    }
}
