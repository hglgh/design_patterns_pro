package com.patterns.behavioral.templatemethod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 具体子类 ①：实物商品订单（衣服、家电、生鲜……）。
 *
 * <p><b>这个类是「最省事」的子类</b>——它只实现了两个抽象方法，
 * 其余全部沿用父类的默认行为：
 *
 * <table border="1">
 *   <caption>PhysicalProductOrder 对父类各方法的使用情况</caption>
 *   <tr><th>父类方法</th><th>角色</th><th>本类的处理</th></tr>
 *   <tr><td>{@code processOrder()}</td><td>模板方法</td><td><b>不能改</b>（final）</td></tr>
 *   <tr><td>{@code pay()}</td><td>抽象方法</td><td><b>必须实现</b> → 全额支付</td></tr>
 *   <tr><td>{@code deliver()}</td><td>抽象方法</td><td><b>必须实现</b> → 交给物流公司</td></tr>
 *   <tr><td>{@code checkStock()}</td><td>具体方法</td><td>不重写，用父类的库存查询</td></tr>
 *   <tr><td>{@code notifyCustomer()}</td><td>具体方法</td><td>不重写，用父类的通知逻辑</td></tr>
 *   <tr><td>{@code needsStockCheck()}</td><td>钩子</td><td>不重写 → 默认 {@code true}，需要检查库存</td></tr>
 *   <tr><td>{@code deliveryProofLabel()}</td><td>钩子</td><td>不重写 → 默认「物流单号」，正好合适</td></tr>
 *   <tr><td>{@code beforePay()}</td><td>钩子</td><td>不重写 → 默认空实现（由模板方法调用，本类无需关心）</td></tr>
 * </table>
 *
 * <p><b>这正体现了模板方法模式的核心收益</b>：
 * 实物商品是最「标准」的订单类型，它需要写的代码最少（只有两个方法共约 20 行），
 * 因为标准行为都已经在父类里实现好了。
 *
 * <p><b>对比原项目</b>：
 * <pre>
 *   // 原项目 PhysicalProductOrder
 *   public class PhysicalProductOrder extends OrderProcessTemplate {
 *       &#64;Override
 *       protected void pay() { System.out.println("支付实物商品的订单"); }
 *       &#64;Override
 *       protected void deliver() { System.out.println("发货实物商品"); }
 *   }
 * </pre>
 * 原项目的结构是对的，本类保留了同样的骨架，但增加了：
 * <ol>
 *   <li><b>数据产出</b>：写入 {@code paymentNo} 和 {@code deliveryProof}，
 *       让后续步骤和调用方能读到结果（原项目四步全是 void，产出全部丢失）；</li>
 *   <li><b>业务细节</b>：发货时校验包裹重量、超重改走物流专线（实物商品特有的逻辑），
 *       让「为什么这一步必须由子类实现」有真实的说服力；</li>
 *   <li><b>流程轨迹</b>：调用 {@code ctx.record(...)}，让执行过程可审计。</li>
 * </ol>
 *
 * <p><b>访问修饰符修正</b>：原项目这个类是 {@code public}，
 * 但它继承的 {@code OrderProcessTemplate} 却是<b>包级私有</b>（没有 public）。
 * 这会造成一个真实的可见性问题：其他包能 {@code new PhysicalProductOrder()}，
 * 却<b>无法声明</b> {@code OrderProcessTemplate o = ...}（父类型不可见），
 * 只能写成 {@code PhysicalProductOrder o = ...}，
 * 于是<b>多态失效</b>——调用方被迫依赖具体子类。
 * 本项目把所有类统一为 {@code public}。
 *
 * @author design-patterns-pro
 * @see VirtualProductOrder 重写了钩子，跳过库存检查
 * @see PreSaleOrder 重写了具体方法，定制通知内容
 */
public class PhysicalProductOrder extends OrderProcessTemplate {

    /** 单件商品的最大允许重量（公斤），超过则不能走普通快递。 */
    private static final BigDecimal MAX_PARCEL_WEIGHT = new BigDecimal("30");

    /**
     * 支付实物商品订单：<b>全额支付</b>。
     *
     * <p>为什么这一步不能由父类统一实现？
     * 因为预售商品只付定金、虚拟商品要额外校验账号，
     * <b>三种支付方式没有共同的实现</b>，父类写不出一个通用版本。
     */
    @Override
    protected void pay(OrderContext ctx) {
        // 【注意】这里不需要调 beforePay(ctx)——钩子由模板方法统一调用。
        // 本类没重写 beforePay()，所以那一步对实物商品而言是空操作，
        // 但流程依然会走到它——【这就是"扩展点"的意义：位置预留好了，用时才填】。

        // 全额支付：实物商品没有定金/尾款的概念
        BigDecimal paid = ctx.getAmount().setScale(2, RoundingMode.HALF_UP);
        String paymentNo = generateSerialNo("PAY");

        ctx.setPaymentNo(paymentNo);
        ctx.record("② 支付 → 全额 ￥" + paid.toPlainString() + "，流水号 " + paymentNo);

        System.out.println("  │  ② 支付：全额 ￥" + paid.toPlainString()
                + "，流水号 " + paymentNo);
    }

    /**
     * 发货实物商品：<b>交给物流公司，产出物流单号</b>。
     *
     * <p>注意这里用到了 {@code ctx.getPaymentNo()}——
     * <b>它是上一步 pay() 写入的</b>。物流公司的发货接口要求带上支付流水号用于财务对账。
     *
     * <p><b>这就是「顺序不可调换」的实际含义</b>：
     * 如果 {@code processOrder()} 不是 final，子类把 deliver() 放到 pay() 前面，
     * 这里拿到的就是 {@code null}，对账系统会收到一条「货已发出但不知道收了谁的钱」的记录。
     */
    @Override
    protected void deliver(OrderContext ctx) {
        // 实物商品特有的校验：超重不能走普通快递。
        // 【为什么这个校验放在子类而不是父类？】
        // 因为虚拟商品没有重量、预售商品发货时才知道重量，
        // 这是"实物商品"这一类型独有的规则，放进父类会污染其他类型。
        BigDecimal weight = estimateWeight(ctx.getProductName());
        boolean overweight = weight.compareTo(MAX_PARCEL_WEIGHT) > 0;
        String carrier = overweight ? "重货物流专线" : "顺丰";

        // 使用上一步产出的支付流水号（体现步骤间的数据依赖）
        String trackingNo = generateSerialNo("SF");
        ctx.setDeliveryProof(trackingNo);

        ctx.record("③ 发货 → 已交付" + carrier + "，运单号 " + trackingNo
                + "（重量 " + weight + "kg，关联支付流水 " + ctx.getPaymentNo() + "）");

        System.out.println("  │  ③ 发货：已交付" + carrier + "，运单号 " + trackingNo);
        if (overweight) {
            System.out.println("  │        └ 重量 " + weight + "kg 超过 "
                    + MAX_PARCEL_WEIGHT + "kg 阈值，普通快递不收，改走专线");
        }
        System.out.println("  │        └ 关联支付流水 " + ctx.getPaymentNo()
                + "  ← 这个值来自步骤 ②，所以顺序不能调换");
    }

    /**
     * 根据商品名估算包裹重量。纯 Demo 用的模拟逻辑。
     *
     * <p>真实项目里重量是商品表的字段，从数据库读。
     * 这里用「商品名字数 × 3 公斤」模拟，目的是让「超重走专线」这个分支
     * <b>可以被真实触发</b>：
     * <ul>
     *   <li>「机械键盘」4 个字 → 12.0 kg，不超过 30 kg，走普通快递（承运方显示「顺丰」）；</li>
     *   <li>「对开门大容量冰箱洗衣机组合套装」15 个字 → 45.0 kg，超过 30 kg，走专线
     *       （承运方显示「重货物流专线」）。</li>
     * </ul>
     * 两个用例在 {@code Client} 的 demo1 里都有演示，运行输出可以逐行对照。
     */
    private BigDecimal estimateWeight(String productName) {
        return BigDecimal.valueOf(productName.length())
                .multiply(new BigDecimal("3"))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
