package com.patterns.behavioral.templatemethod;

import com.patterns.util.DisplayWidth;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户端：演示模板方法模式的完整用法。
 *
 * <p>运行方式：
 * <pre>
 *   javac --release 25 -encoding UTF-8 -d out src/com/patterns/behavioral/templatemethod/*.java
 *   java -Dfile.encoding=UTF-8 -cp out com.patterns.behavioral.templatemethod.Client
 * </pre>
 *
 * <p>七段演示分别回答一个问题：
 * <ol>
 *   <li>{@link #demo1Physical} —— 标准流程长什么样？步骤之间怎么传递数据？</li>
 *   <li>{@link #demo2Virtual} —— 钩子方法怎么让某个步骤被跳过？</li>
 *   <li>{@link #demo3PreSale} —— 四种方法角色在同一个子类里怎么配合？</li>
 *   <li>{@link #demo4ShortCircuit} —— 步骤失败时流程怎么中止？（原项目没有这个能力）</li>
 *   <li>{@link #demo5Polymorphism} —— 多态的价值：一批混合订单怎么统一处理？</li>
 *   <li>{@link #demo6WhyFinal} —— <b>为什么模板方法必须是 final？</b>（真实触发顺序错乱）</li>
 *   <li>{@link #demo7Trace} —— 流程轨迹：怎么审计「谁做了什么」？</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1Physical();
        demo2Virtual();
        demo3PreSale();
        demo4ShortCircuit();
        demo5Polymorphism();
        demo6WhyFinal();
        demo7Trace();
    }

    /**
     * 演示 1：实物商品订单 —— 最标准的流程。
     *
     * <p>重点看步骤之间的<b>数据传递</b>：步骤 ③ 用到了步骤 ② 产出的支付流水号。
     */
    private static void demo1Physical() {
        System.out.println("=== 1. 实物商品订单（标准流程）===");

        // 调用方只持有父类引用，完全不知道具体是哪种订单类型。
        // 【注意】这要求 OrderProcessTemplate 是 public——
        // 原项目它是包级私有的，跨包时这行代码根本编译不过。
        OrderProcessTemplate processor = new PhysicalProductOrder();
        OrderContext ctx = new OrderContext(
                "ORD-P-001", "机械键盘", new BigDecimal("399.00"));

        OrderContext result = processor.processOrder(ctx);

        System.out.println();
        System.out.println("  -- 调用方能读到的产出（原项目全是 void，这些一个都拿不到）--");
        System.out.println("      支付流水号：" + result.getPaymentNo());
        System.out.println("      物流单号  ：" + result.getDeliveryProof());
        System.out.println("      可用库存  ：" + result.getAvailableStock());
        System.out.println("      是否中止  ：" + result.isAborted());
        System.out.println();

        // 同一个处理器、不同的订单：触发「超重改走物流专线」这个分支。
        // 15 个字 × 3 = 45.0 kg > 30 kg 阈值
        System.out.println("  -- 同一个处理器处理另一笔订单（触发子类特有的超重分支）--");
        OrderContext heavy = new OrderContext(
                "ORD-P-003", "对开门大容量冰箱洗衣机组合套装", new BigDecimal("8999.00"));
        processor.processOrder(heavy);
        System.out.println();
        System.out.println("      >>> 「超重改走专线」这个判断只存在于 PhysicalProductOrder 里。");
        System.out.println("          虚拟商品没有重量、预售商品发货时才知道重量，");
        System.out.println("          所以这段逻辑【不该】放到父类去污染其他类型。");
        System.out.println("          这就是「子类实现抽象方法」的实际含义：子类装自己的特殊规则。");
        System.out.println();
    }

    /**
     * 演示 2：虚拟商品订单 —— 钩子方法跳过库存检查。
     *
     * <p>对照原项目的输出：原项目会打印「检查库存」，
     * 但电子书的库存是无限的，<b>这一行在业务上是错的</b>。
     */
    private static void demo2Virtual() {
        System.out.println("=== 2. 虚拟商品订单（钩子跳过库存检查）===");

        OrderProcessTemplate processor = new VirtualProductOrder();
        // 商品名里 @ 后面的部分是模拟的充值账号
        OrderContext ctx = new OrderContext(
                "ORD-V-001", "Java编程思想电子书@zhangsan@mail.com", new BigDecimal("89.90"));

        processor.processOrder(ctx);

        System.out.println();
        System.out.println("  >>> 注意步骤 ① 被【跳过】了，而不是「检查通过」。");
        System.out.println("      这两者在审计时的责任归属完全不同，所以轨迹里明确记录了「跳过」。");
        System.out.println();
        System.out.println("  >>> 步骤 ④ 的通知文案自动变成了「下载链接」，");
        System.out.println("      而 notifyCustomer() 的整段拼接逻辑【完全复用父类】。");
        System.out.println("      子类只改了一个钩子的返回值（四个字），没有抄一行父类代码。");
        System.out.println();
        System.out.println("  -- 对照原项目的输出 --");
        System.out.println("      处理虚拟商品订单：");
        System.out.println("      检查库存                    ← 电子书库存无限，这一行是【错的】");
        System.out.println("      支付虚拟商品的订单");
        System.out.println("      通过邮件发送虚拟商品");
        System.out.println("      通知客户订单处理完成");
        System.out.println();
    }

    /**
     * 演示 3：预售订单 —— 四种方法角色在同一个子类里配合。
     */
    private static void demo3PreSale() {
        System.out.println("=== 3. 预售订单（新增的子类，父类 0 改动）===");

        OrderProcessTemplate processor = new PreSaleOrder();
        OrderContext ctx = new OrderContext(
                "ORD-S-001", "新款折叠屏手机首发预售版", new BigDecimal("6999.00"));

        OrderContext result = processor.processOrder(ctx);

        System.out.println();
        System.out.println("  -- 本类用到了全部四种方法角色 --");
        System.out.println("      模板方法 processOrder()      → final，子类【改不了】");
        System.out.println("      抽象方法 pay()/deliver()     → 子类【必须】实现（定金 30% / 预计到货日）");
        System.out.println("      具体方法 notifyCustomer()    → 子类【重写并调用 super】，追加尾款提醒");
        System.out.println("      钩子 needsStockCheck()       → 子类【重写为 false】，跳过库存检查");
        System.out.println("      钩子 deliveryProofLabel()    → 子类【重写】，称呼改为「预计到货日」");
        System.out.println("      钩子 beforePay()             → 子类【重写空钩子】，插入定金校验（由模板方法调用）");
        System.out.println();
        System.out.println("  -- 开闭原则的验证 --");
        System.out.println("      新增这个类时，OrderProcessTemplate.java 改动了 0 行；");
        System.out.println("      PhysicalProductOrder / VirtualProductOrder 也改动了 0 行。");
        System.out.println("      → 对扩展开放（新建一个类），对修改关闭（不碰任何已有类）。");
        System.out.println();
        System.out.println("      实际产出：定金流水 " + result.getPaymentNo());
        System.out.println("                预计到货 " + result.getDeliveryProof());
        System.out.println();
    }

    /**
     * 演示 4：失败短路 —— 步骤失败时流程立即中止。
     *
     * <p><b>这是原项目完全缺失的能力。</b>原项目即使库存为 0，
     * 也会照常走完「支付 → 发货 → 通知」，那就是真实的超卖事故。
     */
    private static void demo4ShortCircuit() {
        System.out.println("=== 4. 失败短路：库存不足 / 账号非法 ===");

        // 情况 A：库存不足（商品名 ≤ 3 个字时，模拟库存为 0）
        System.out.println("  -- A. 实物商品库存不足 --");
        OrderContext soldOut = new OrderContext(
                "ORD-P-002", "杯子", new BigDecimal("29.90"));
        OrderContext r1 = new PhysicalProductOrder().processOrder(soldOut);
        System.out.println("      支付流水号 = " + r1.getPaymentNo()
                + "   ← 【null】说明根本没走到支付步骤");
        System.out.println("      isAborted  = " + r1.isAborted());
        System.out.println("      中止原因   = " + r1.getAbortReason());
        System.out.println();
        System.out.println("      >>> 对比原项目：库存不足时它依然会打印「支付实物商品的订单」「发货实物商品」");
        System.out.println("          ——用户付了钱但没货，只能退款，这就是超卖事故。");
        System.out.println();

        // 情况 B：虚拟商品账号校验失败
        System.out.println("  -- B. 虚拟商品充值账号非法 --");
        OrderContext badAccount = new OrderContext(
                "ORD-V-002", "话费充值@12", new BigDecimal("100.00"));
        OrderContext r2 = new VirtualProductOrder().processOrder(badAccount);
        System.out.println("      isAborted = " + r2.isAborted());
        System.out.println("      中止原因  = " + r2.getAbortReason());
        System.out.println("      下载链接  = " + r2.getDeliveryProof()
                + "   ← 【null】没走到交付步骤");
        System.out.println();
        System.out.println("      >>> 注意：这里用 ctx.abort() 而不是抛异常。");
        System.out.println("          「账号填错了」是正常业务情况，不是程序错误。");
        System.out.println("          抛异常会逼调用方写 try-catch，用 if 判断更符合语义。");
        System.out.println();

        // 情况 C：预售定金低于最低限额，被 beforePay 钩子拦下
        System.out.println("  -- C. 预售订单定金低于最低限额，被钩子拦下 --");
        OrderContext tooCheap = new OrderContext(
                "ORD-S-002", "定制马克杯", new BigDecimal("0.10"));
        OrderContext r3 = new PreSaleOrder().processOrder(tooCheap);
        System.out.println("      isAborted = " + r3.isAborted());
        System.out.println("      中止原因  = " + r3.getAbortReason());
        System.out.println("      定金流水  = " + r3.getPaymentNo() + "   ← 【null】没真的扣钱");
        System.out.println();
        System.out.println("      >>> 这一单是被 beforePay() 钩子拦下的：0.10 × 0.30 = 0.03 < 最低限额 1 元。");
        System.out.println("          钩子不只是「开关」，它也能主动中止流程。");
        System.out.println();
        System.out.println("      >>> 【注意输出里没有「② 支付」这一行】");
        System.out.println("          因为 beforePay() 是由模板方法调用的，它一返回就检查 isAborted()，");
        System.out.println("          直接 return 了——PreSaleOrder.pay() 【根本没被执行】。");
        System.out.println("          对比：如果钩子是由子类自己在 pay() 里调的，");
        System.out.println("          子类就得额外写 if (ctx.isAborted()) return; 这种防御代码，");
        System.out.println("          而且【新增子类忘了写就会静默失效】。这就是调用位置的区别。");
        System.out.println();
        System.out.println("      >>> 【一个真实的调试经历】这个阈值最初写的是 deposit.signum() <= 0，");
        System.out.println("          即「定金为 0 才拦」。结果 0.03 > 0，校验直接通过了，");
        System.out.println("          这一单被当成正常预售处理了——是跑 Demo 看到「定金 ￥0.03」才发现的。");
        System.out.println("          教训：“金额为 0”和“金额小得不合理”是两回事，阈值要按业务定。");
        System.out.println();
    }

    /**
     * 演示 5：多态的价值 —— 一批混合订单统一处理。
     *
     * <p><b>这段代码完全不知道有几种订单类型</b>，新增第四种订单时一行都不用改。
     */
    private static void demo5Polymorphism() {
        System.out.println("=== 5. 多态：一批混合订单统一处理 ===");

        // 三种完全不同的订单类型，放在同一个 List 里
        List<OrderProcessTemplate> processors = List.of(
                new PhysicalProductOrder(),
                new VirtualProductOrder(),
                new PreSaleOrder()
        );
        List<OrderContext> orders = List.of(
                new OrderContext("ORD-B-001", "无线鼠标", new BigDecimal("129.00")),
                new OrderContext("ORD-B-002", "在线课程@lisi@example.com", new BigDecimal("299.00")),
                new OrderContext("ORD-B-003", "限量版手办预售", new BigDecimal("899.00"))
        );

        System.out.println("  待处理订单 " + orders.size() + " 笔，处理器 " + processors.size() + " 种");
        System.out.println("  下面这个循环体【不认识任何具体订单类型】：");
        System.out.println();
        System.out.println("      for (int i = 0; i < orders.size(); i++) {");
        System.out.println("          processors.get(i).processOrder(orders.get(i));");
        System.out.println("      }");
        System.out.println();

        int success = 0;
        for (int i = 0; i < orders.size(); i++) {
            OrderContext result = processors.get(i).processOrder(orders.get(i));
            if (!result.isAborted()) {
                success++;
            }
            System.out.println();
        }

        // 汇总统计也完全通用：只依赖 OrderContext 的公共字段
        System.out.println("  " + DisplayWidth.line('-', 52));
        System.out.println("      处理完成：" + success + " / " + orders.size() + " 笔成功");
        System.out.println();
        System.out.println("  >>> 上面这段统计代码只读了 isAborted()，");
        System.out.println("      它对三种订单类型【一视同仁】，没有任何 instanceof 判断。");
        System.out.println("      新增第四种订单类型时，这里一个字都不用改。");
        System.out.println();
    }

    /**
     * 演示 6：<b>为什么模板方法必须是 final？</b>
     *
     * <p>这段代码<b>故意绕过模板方法</b>，直接按错误的顺序调用步骤，
     * 用真实的运行结果证明「顺序不可调换」不是一句空话。
     *
     * <p><b>为什么 Client 能调用 protected 方法？</b>
     * 因为 {@code protected} 的可见范围是「<b>同包 + 子类</b>」，
     * 而本类和这些订单类在同一个包里。
     * 这恰好给了我们做反例演示的机会——
     * 但也暴露了一个真实的语言限制，见下面的说明。
     */
    private static void demo6WhyFinal() {
        System.out.println("=== 6. 为什么模板方法必须是 final（反例演示）===");

        PhysicalProductOrder processor = new PhysicalProductOrder();
        OrderContext ctx = new OrderContext(
                "ORD-X-001", "机械键盘", new BigDecimal("399.00"));

        System.out.println("  -- 正确的顺序（由 final 的模板方法保证）--");
        System.out.println("      ① 检查库存 → ② 支付 → ③ 发货 → ④ 通知");
        System.out.println();

        System.out.println("  -- 现在【故意】把顺序写反：先发货，再支付 --");
        System.out.println("      （这段代码能编译通过，因为 Client 和订单类在同一个包内，");
        System.out.println("        protected 方法对同包可见）");
        System.out.println();

        // ❌ 错误顺序：先发货
        processor.deliver(ctx);
        System.out.println();
        System.out.println("      >>> 发货步骤里读取的「关联支付流水」是什么？");
        System.out.println("          ctx.getPaymentNo() = " + ctx.getPaymentNo());
        System.out.println("          ↑ 【null】！因为 pay() 还没执行。");
        System.out.println();
        System.out.println("      >>> 后果：物流公司收到一条「货已发出、支付流水号为 null」的记录，");
        System.out.println("          财务对账时这笔货不知道是谁付的钱。这是真实的资金风险。");
        System.out.println();

        // 补上支付，说明数据一旦补齐就正常了
        processor.pay(ctx);
        System.out.println("      现在再调 pay()：" + ctx.getPaymentNo());
        System.out.println("      → 但发货记录已经写进物流系统了，改不回来了。");
        System.out.println();

        System.out.println("  -- final 是怎么防止这件事的？--");
        System.out.println("      因为 processOrder() 是 public final，子类【无法重写】它，");
        System.out.println("      所以子类没有任何办法改变步骤顺序。下面这段代码编译不过：");
        System.out.println();
        System.out.println("          public class BadOrder extends OrderProcessTemplate {");
        System.out.println("              @Override");
        System.out.println("              public OrderContext processOrder(OrderContext ctx) {  // ← 编译错误");
        System.out.println("                  deliver(ctx);      // 先发货");
        System.out.println("                  pay(ctx);          // 再收款");
        System.out.println("                  return ctx;");
        System.out.println("              }");
        System.out.println("          }");
        System.out.println("      报错信息：cannot override the final method from OrderProcessTemplate");
        System.out.println();

        System.out.println("  -- 一个诚实的说明：protected 在同包内是可以被绕过的 --");
        System.out.println("      上面这段反例能跑起来，正是因为 Client 和订单类在同一个包里。");
        System.out.println("      Java 的 protected = 【同包可见 + 子类可见】，没有更严格的选项。");
        System.out.println("      生产项目里的应对方式：");
        System.out.println("        ① 把 Client（调用方）放在【不同的包】，protected 就真的访问不到了；");
        System.out.println("        ② 用模块系统（JPMS）只 exports 需要暴露的包；");
        System.out.println("        ③ 用 ArchUnit 写架构测试，禁止跨层调用步骤方法。");
        System.out.println("      本项目为了能演示反例，刻意把它们放在同一个包里。");
        System.out.println();
    }

    /**
     * 演示 7：流程轨迹 —— 审计「谁做了什么」。
     *
     * <p>{@code ctx.getTrace()} 记录了流程中每一步的实际执行情况，
     * 包括<b>被钩子跳过的步骤</b>。真实系统里这对应操作日志表或链路追踪。
     */
    private static void demo7Trace() {
        System.out.println("=== 7. 流程轨迹（可审计的执行记录）===");

        OrderContext ctx = new OrderContext(
                "ORD-T-001", "智能手表", new BigDecimal("1299.00"));
        new PhysicalProductOrder().processOrder(ctx);

        System.out.println();
        System.out.println("  -- 这笔订单的完整轨迹 --");
        List<String> trace = ctx.getTrace();
        for (int i = 0; i < trace.size(); i++) {
            System.out.println("      " + (i + 1) + ". " + trace.get(i));
        }
        System.out.println();

        System.out.println("  -- 对照：虚拟商品的轨迹（注意第 2 条）--");
        OrderContext vctx = new OrderContext(
                "ORD-T-002", "音乐会员年卡@music@example.com", new BigDecimal("168.00"));
        new VirtualProductOrder().processOrder(vctx);
        System.out.println();
        List<String> vtrace = vctx.getTrace();
        for (int i = 0; i < vtrace.size(); i++) {
            System.out.println("      " + (i + 1) + ". " + vtrace.get(i));
        }
        System.out.println();
        System.out.println("  >>> 第 2 条明确写着「跳过库存检查（needsStockCheck = false）」。");
        System.out.println("      审计时能一眼看出【这一步没做】，而不是【做了但没记录】。");
        System.out.println();

        System.out.println("  -- 轨迹是只读的 --");
        try {
            trace.add("伪造一条记录");
            System.out.println("      [FAIL] 居然添加成功了？这说明封装有问题");
        } catch (UnsupportedOperationException e) {
            System.out.println("      [OK] trace.add() 抛 UnsupportedOperationException");
            System.out.println("           → getTrace() 返回的是 unmodifiableList 视图，");
            System.out.println("             外部无法篡改审计日志。");
        }
        System.out.println();
    }
}
