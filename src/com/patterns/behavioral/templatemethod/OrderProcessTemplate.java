package com.patterns.behavioral.templatemethod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 抽象模板：定义订单处理的<b>算法骨架</b>，把可变步骤延迟到子类实现。
 *
 * <p><b>本类是模板方法模式的核心</b>，它同时展示了 GoF 定义的<b>四种方法角色</b>：
 *
 * <table border="1">
 *   <caption>四种方法角色</caption>
 *   <tr>
 *     <th>角色</th><th>修饰符</th><th>子类能否重写</th><th>本类中的例子</th>
 *   </tr>
 *   <tr>
 *     <td><b>模板方法</b><br>Template Method</td>
 *     <td>{@code public final}</td>
 *     <td><b>绝对不能</b>（final 强制）</td>
 *     <td>{@link #processOrder(OrderContext)}</td>
 *   </tr>
 *   <tr>
 *     <td><b>抽象方法</b><br>Abstract Method</td>
 *     <td>{@code protected abstract}</td>
 *     <td><b>必须</b>实现（编译器强制）</td>
 *     <td>{@link #pay(OrderContext)}、{@link #deliver(OrderContext)}</td>
 *   </tr>
 *   <tr>
 *     <td><b>具体方法</b><br>Concrete Method</td>
 *     <td>{@code protected}</td>
 *     <td>可以重写，但通常不需要</td>
 *     <td>{@link #checkStock(OrderContext)}、{@link #notifyCustomer(OrderContext)}</td>
 *   </tr>
 *   <tr>
 *     <td><b>钩子方法</b><br>Hook Method</td>
 *     <td>{@code protected}（有默认实现）</td>
 *     <td><b>可选</b>重写（不重写也能跑）</td>
 *     <td>{@link #needsStockCheck()}、{@link #deliveryProofLabel()}、{@link #beforePay(OrderContext)}</td>
 *   </tr>
 * </table>
 *
 * <p><b>原项目只有前三种，完全没有钩子方法</b>——而钩子恰恰是模板方法模式最有实用价值的部分。
 * 详见 {@link #needsStockCheck()} 的注释。
 *
 * <h2>为什么模板方法必须是 final？</h2>
 *
 * <p>这是本模式<b>最重要的一条设计约束</b>，原项目做对了（{@code public final void processOrder()}），
 * 必须保留并讲清楚原因。
 *
 * <p>如果 {@code processOrder()} 不是 final，子类可以这样写：
 * <pre>
 *   public class PhysicalProductOrder extends OrderProcessTemplate {
 *       &#64;Override
 *       public OrderContext processOrder(OrderContext ctx) {
 *           deliver(ctx);            // ← 先发货
 *           pay(ctx);                // ← 再收款
 *           return ctx;
 *       }
 *   }
 * </pre>
 * <b>后果是灾难性的</b>：货发出去了钱没收，而且发货时 {@code paymentNo} 还是 {@code null}，
 * 财务对账系统会收到一条没有支付流水号的发货记录。
 *
 * <p>模板方法模式的价值<b>完全建立在「骨架不可篡改」这个前提上</b>。
 * 一旦子类能改骨架，「统一的流程控制」就不存在了，模式退化成一堆各自为政的方法。
 *
 * <blockquote>
 * <b>这就是好莱坞原则（Hollywood Principle）</b>：<br>
 * <i>"Don't call us, we'll call you."</i>（别调用我们，我们会调用你）<br>
 * 子类<b>不能</b>主动调用父类的模板方法，也<b>不能</b>决定执行顺序；
 * 只有父类在合适的时机回调子类实现的那些步骤。<br>
 * 依赖倒置原则（DIP）在这里的具体体现就是：<b>高层模块（父类）控制流程，低层模块（子类）填充细节。</b>
 * </blockquote>
 *
 * <h2>为什么步骤方法是 protected 而不是 public？</h2>
 *
 * <p>{@code pay()} / {@code deliver()} 这些步骤<b>是流程的内部环节，不是对外 API</b>。
 * 如果声明成 public，外部代码就能这样写：
 * <pre>
 *   order.deliver(ctx);      // ← 绕过库存检查和支付，直接发货！
 * </pre>
 * 这等于把精心设计的流程约束全部废掉了。
 *
 * <p>{@code protected} 的语义正好：<b>只有子类能看到，外部包和普通调用方都看不到</b>。
 * 外界唯一能调用的入口就是 {@code public final processOrder()}。
 *
 * <h2>模板方法模式 vs 策略模式：这是同一件事的两种解法</h2>
 *
 * <p>两个模式都在解决「有一族相似的操作，某些步骤不同」的问题，但手段相反：
 *
 * <table border="1">
 *   <caption>模板方法 vs 策略</caption>
 *   <tr><th></th><th>模板方法（本类）</th><th>策略模式</th></tr>
 *   <tr><td>复用手段</td><td><b>继承</b>（子类重写步骤）</td><td><b>组合</b>（Context 持有策略对象）</td></tr>
 *   <tr><td>绑定时机</td><td>编译期，对象创建后<b>无法更换</b></td><td>运行期，随时可换</td></tr>
 *   <tr><td>变化粒度</td><td>算法中的<b>某几步</b></td><td><b>整个算法</b></td></tr>
 *   <tr><td>流程控制权</td><td>在<b>父类</b>手里</td><td>在<b>调用方</b>手里</td></tr>
 *   <tr><td>典型场景</td><td>流程固定、只有细节因类型而异</td><td>算法本身可互换、可配置</td></tr>
 * </table>
 *
 * <p><b>本例为什么选模板方法而不是策略？</b>
 * 因为「检查库存 → 支付 → 发货 → 通知」这个<b>顺序是业务强制的、不可配置的</b>。
 * 不存在「某个客户希望先发货再付款」这种需求（如果有，那是另一种订单类型，应该新建子类）。
 * 流程固定 + 细节可变，正是模板方法的最佳适用场景。
 *
 * <p><b>反过来说</b>：如果折扣算法要能被运营在后台自由配置、能运行时切换、能遍历比较，
 * 那就必须用策略模式（见 {@code ../strategy/README.md}）。
 *
 * @author design-patterns-pro
 * @see OrderContext 贯穿流程的数据载体
 */
public abstract class OrderProcessTemplate {

    /** 时间戳格式，用于生成流水号。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 流水号自增序号，保证同一秒内生成的单号不重复。 */
    private static int sequence = 0;

    // ==================================================================
    // 【角色 1】模板方法：public final —— 算法骨架，子类绝对不能改
    // ==================================================================

    /**
     * 订单处理主流程。<b>这就是「模板方法」本身。</b>
     *
     * <p>它规定了四件事：
     * <ol>
     *   <li><b>有哪些步骤</b>：检查库存、支付、发货、通知；</li>
     *   <li><b>步骤的顺序</b>：不能调换，因为存在数据依赖（见 {@link OrderContext} 类注释）；</li>
     *   <li><b>哪些步骤可以跳过</b>：由钩子 {@link #needsStockCheck()} 决定；</li>
     *   <li><b>什么时候必须中止</b>：库存不足、支付失败。</li>
     * </ol>
     *
     * <p><b>为什么声明成 {@code final}？</b>见类注释「为什么模板方法必须是 final」。
     * 一句话：<b>模式的全部价值建立在「骨架不可篡改」上。</b>
     *
     * <p><b>为什么返回 {@link OrderContext} 而不是 void？</b>
     * 原项目是 void，导致调用方拿不到任何执行结果——
     * 不知道流程是否成功、不知道支付流水号、不知道物流单号。
     * 真实系统里调用方<b>必须</b>拿到这些结果（要写数据库、要返回给前端）。
     * 返回上下文对象，调用方就能读到全流程的产出。
     *
     * @param ctx 订单上下文，不能为 null
     * @return 同一个 ctx 对象（已被各步骤填充完毕），便于链式读取结果
     * @throws IllegalArgumentException ctx 为 null 时抛出
     */
    public final OrderContext processOrder(OrderContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("订单上下文不能为 null");
        }

        System.out.println("  ┌─ 开始处理订单 " + ctx.getOrderNo()
                + "（" + ctx.getProductName() + "，￥" + ctx.getAmount().toPlainString() + "）");
        System.out.println("  │  处理器类型：" + getClass().getSimpleName());
        ctx.record("流程启动，处理器 = " + getClass().getSimpleName());

        // -------- 步骤 ①：库存检查（钩子控制是否执行） --------
        // 注意这里的 if 判断的是【钩子方法的返回值】，不是业务数据的 if-else。
        // 区别在于：钩子是"子类在编译期就决定好的策略开关"，
        // 而业务 if-else 是"运行时根据数据分支"——后者才是模式要消灭的东西。
        if (needsStockCheck()) {
            checkStock(ctx);
            // 失败短路：库存不足时【绝不】继续走支付流程。
            // 原项目没有这个能力，即使库存为 0 也会照常"支付、发货、通知"，
            // 那就是真实的超卖事故。
            if (ctx.getAvailableStock() <= 0) {
                ctx.abort("库存不足（可用 " + ctx.getAvailableStock() + " 件），订单不予处理");
                System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
                return ctx;
            }
        } else {
            // 钩子返回 false：跳过库存检查，并在轨迹里留下明确记录。
            // 【为什么要记录"跳过"而不是什么都不做？】
            // 因为审计时必须能区分"检查通过了"和"根本没检查"，
            // 这两者在出问题时的责任归属完全不同。
            ctx.record("跳过库存检查（" + getClass().getSimpleName() + ".needsStockCheck() = false）");
            System.out.println("  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①");
        }

        // -------- 步骤 ②：支付 --------
        // 先调钩子 beforePay()，给子类一个「支付前插入校验」的机会。
        // 【为什么由模板方法调用，而不是由子类的 pay() 自己调用？】
        // 早期版本是写在各个子类的 pay() 第一行的，那是个真实的设计缺陷：
        //   ① 违反好莱坞原则——钩子本该由父类在合适时机回调，不是子类自己想起来才调；
        //   ② 靠「每个子类都记得写一行 beforePay(ctx)」来保证，新增子类忘了写就静默失效，
        //      而且编译器【不会报错】，这种 bug 极难发现。
        // 放进模板方法后，调用点只有一处，子类只管重写、不用管调用。
        beforePay(ctx);
        if (ctx.isAborted()) {
            System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
            return ctx;
        }

        // 支付（抽象方法，子类必须实现）
        pay(ctx);

        // 检查 1：子类是否已经主动中止了流程？
        // 【为什么这个检查必须放在 paymentNo 判空之前？】
        // 因为子类可能因为业务原因 abort（例如充值账号格式非法），
        // 此时 paymentNo 必然也是 null。如果先判 paymentNo，
        // 就会用「疑似子类实现缺陷」把子类给出的【真实业务原因】覆盖掉，
        // 排查事故时看到的是错误信息。这个 bug 在本项目实测时真的出现过。
        if (ctx.isAborted()) {
            System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
            return ctx;
        }

        // 检查 2：流程没中止，但也没写入流水号 → 说明子类的实现有缺陷。
        // 这是【防御性检查】，把「子类忘了调 setPaymentNo」这种编码错误暴露出来，
        // 而不是让 null 流到 deliver() 里去。
        if (ctx.getPaymentNo() == null) {
            ctx.abort("支付步骤未产生流水号，疑似子类实现缺陷（忘了调 ctx.setPaymentNo）");
            System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
            return ctx;
        }

        // -------- 步骤 ③：发货（抽象方法，子类必须实现） --------
        // 走到这里时 paymentNo 一定非 null，子类可以放心使用它。
        // 【这就是"顺序不可调换"的实际含义】：deliver() 依赖 pay() 的产出。
        deliver(ctx);

        // -------- 步骤 ④：通知客户（具体方法，父类已实现，子类可选重写） --------
        notifyCustomer(ctx);

        System.out.println("  └─ 订单处理完成 √");
        ctx.record("流程结束");
        return ctx;
    }

    // ==================================================================
    // 【角色 2】抽象方法：protected abstract —— 子类【必须】实现
    // ==================================================================

    /**
     * 支付步骤。<b>必须由子类实现。</b>
     *
     * <p><b>为什么这一步是抽象的？</b>
     * 因为不同商品类型的支付方式<b>本质上不同</b>，无法在父类里给出一个通用实现：
     * <ul>
     *   <li>实物商品：全额支付；</li>
     *   <li>虚拟商品：全额支付，但要额外校验账号（防止充值到错误账号）；</li>
     *   <li>预售商品：只付定金（例如 30%），尾款到货后再付。</li>
     * </ul>
     *
     * <p><b>实现契约</b>：
     * <ol>
     *   <li>必须调用 {@code ctx.setPaymentNo(...)} 写入支付流水号。
     *       不写的话模板方法会检测到并中止流程（见 {@link #processOrder}）。</li>
     *   <li>应该调用 {@code ctx.record(...)} 记录本步骤做了什么。</li>
     *   <li>如果支付失败，应该调用 {@code ctx.abort(reason)}，
     *       而不是抛异常——「支付失败」是正常业务情况，不是程序错误。</li>
     * </ol>
     *
     * @param ctx 订单上下文
     */
    protected abstract void pay(OrderContext ctx);

    /**
     * 发货步骤。<b>必须由子类实现。</b>
     *
     * <p><b>为什么这一步是抽象的？</b>
     * 因为「交付」这件事在不同商品类型下是完全不同的动作：
     * <ul>
     *   <li>实物商品：交给物流公司，产出<b>物流单号</b>；</li>
     *   <li>虚拟商品：发邮件/短信，产出<b>下载链接或激活码</b>；</li>
     *   <li>预售商品：到货后才发，产出<b>预计到货日</b>。</li>
     * </ul>
     *
     * <p><b>实现契约</b>：
     * <ol>
     *   <li>必须调用 {@code ctx.setDeliveryProof(...)} 写入交付凭证；</li>
     *   <li>可以使用 {@code ctx.getPaymentNo()}——它此时<b>保证非 null</b>，
     *       因为模板方法已经检查过了。<b>这个保证来自「顺序不可调换」</b>，
     *       如果子类能改顺序（即模板方法不是 final），这个保证就不成立了。</li>
     * </ol>
     *
     * @param ctx 订单上下文
     */
    protected abstract void deliver(OrderContext ctx);

    // ==================================================================
    // 【角色 3】具体方法：protected（父类已实现）—— 子类【可以】重写但通常不必
    // ==================================================================

    /**
     * 检查库存。<b>父类已经实现了通用逻辑</b>，所有需要检查库存的订单共用这一份。
     *
     * <p><b>为什么这一步不是抽象的？</b>
     * 因为「查库存」这个动作本身对所有商品类型都是一样的：
     * 拿商品名去库存服务查一个数字。<b>不同的是「要不要查」，而不是「怎么查」</b>
     * ——前者用钩子 {@link #needsStockCheck()} 表达，后者复用父类实现。
     *
     * <p><b>这就是「具体方法」的价值</b>：把真正共用的逻辑收敛到父类，避免每个子类抄一遍。
     * 原项目也做对了这一点（{@code protected void checkStock()} 在父类实现）。
     *
     * <p>本实现用 {@code productName.length()} 模拟库存查询结果，
     * 目的是让「库存不足」这个分支<b>可以被真实触发</b>——
     * 商品名长度 ≤ 3 时库存为 0。真实项目里这里是 {@code inventoryService.query(skuId)}。
     *
     * @param ctx 订单上下文，方法会写入 {@code availableStock}
     */
    protected void checkStock(OrderContext ctx) {
        // 模拟库存查询：商品名越长代表越冷门、库存越少（纯粹为了 Demo 可复现）
        int stock = Math.max(0, ctx.getProductName().length() - 3) * 7;
        ctx.setAvailableStock(stock);
        ctx.record("① 检查库存 → 可用 " + stock + " 件");
        System.out.println("  │  ① 检查库存：可用 " + stock + " 件"
                + (stock <= 0 ? "  ← 不足！" : ""));
    }

    /**
     * 通知客户。<b>父类给出了通用实现</b>，子类可以重写来定制通知渠道。
     *
     * <p><b>为什么这一步是「具体方法」而不是「抽象方法」？</b>
     * 因为大多数订单的通知逻辑是相同的（发一条含订单号和交付凭证的消息），
     * 只有少数类型需要定制。让所有子类都强制实现一遍相同的逻辑是无谓的负担。
     *
     * <p><b>它和钩子方法的区别在哪？</b>
     * 两者都是「父类有默认实现、子类可选重写」，看起来一样。区别在<b>语义</b>：
     * <ul>
     *   <li><b>具体方法</b>是流程中的<b>一个实质步骤</b>（它真的做了事——发通知）；</li>
     *   <li><b>钩子方法</b>是<b>控制流程的开关或参数</b>（它返回一个值，影响模板方法怎么走）。</li>
     * </ul>
     * 简单说：<b>具体方法出现在流程的「执行位」上，钩子出现在流程的「判断位」上。</b>
     *
     * <p>注意父类实现里用了钩子 {@link #deliveryProofLabel()}——
     * <b>具体方法内部调用钩子，是模板方法模式的常见组合拳</b>：
     * 逻辑主体复用父类，只有措辞这种细节由子类定制。
     *
     * @param ctx 订单上下文，方法会写入 {@code notificationContent}
     */
    protected void notifyCustomer(OrderContext ctx) {
        // deliveryProofLabel() 是钩子：实物商品返回"物流单号"，虚拟商品返回"下载链接"。
        // 父类的通知逻辑复用，只有这个【称呼】随子类变化。
        String content = "尊敬的客户，您的订单 " + ctx.getOrderNo()
                + "（" + ctx.getProductName() + "）已处理完成，"
                + deliveryProofLabel() + "：" + ctx.getDeliveryProof();

        ctx.setNotificationContent(content);
        ctx.record("④ 通知客户 → 已发送（凭证类型：" + deliveryProofLabel() + "）");
        System.out.println("  │  ④ 通知客户：" + content);
    }

    // ==================================================================
    // 【角色 4】钩子方法：protected（有默认值）—— 子类【可选】重写
    // ==================================================================

    /**
     * 钩子：本类型订单<b>是否需要检查库存</b>？默认需要。
     *
     * <p><b>这是原项目完全缺失的概念，也是本次重构最重要的补充。</b>
     *
     * <p><b>为什么需要这个钩子？</b>
     * 原项目把 {@code checkStock()} 无条件写在模板方法里，于是<b>虚拟商品也要「检查库存」</b>：
     * <pre>
     *   处理虚拟商品订单：
     *   检查库存              ← 这一行在业务上是【错的】！
     *   支付虚拟商品的订单
     *   通过邮件发送虚拟商品
     *   通知客户订单处理完成
     * </pre>
     *
     * <p>电子书、激活码、在线课程这类虚拟商品<b>库存是无限的</b>，
     * 检查库存这个动作根本没有意义。原项目让它输出「检查库存」，
     * 会给读者传达一个<b>错误的业务认知</b>。
     *
     * <p><b>三种可能的解法，为什么选钩子？</b>
     *
     * <table border="1">
     *   <caption>解法对比</caption>
     *   <tr><th>解法</th><th>写法</th><th>问题</th></tr>
     *   <tr>
     *     <td>① 让子类重写 checkStock() 成空方法</td>
     *     <td>{@code protected void checkStock(ctx) { }}</td>
     *     <td>空方法<b>看不出意图</b>：是「不需要检查」还是「忘了实现」？
     *         而且轨迹里没有记录，审计时无法区分</td>
     *   </tr>
     *   <tr>
     *     <td>② 把 checkStock() 改成抽象方法</td>
     *     <td>{@code protected abstract void checkStock(ctx);}</td>
     *     <td>逼着<b>所有</b>子类实现一遍相同的库存查询逻辑，
     *         或者各自抄一遍——正是模式要消除的重复</td>
     *   </tr>
     *   <tr>
     *     <td>③ <b>钩子方法（本项目采用）</b></td>
     *     <td>{@code protected boolean needsStockCheck() { return true; }}</td>
     *     <td>无。意图明确、默认行为合理、子类只改一个布尔值</td>
     *   </tr>
     * </table>
     *
     * <p><b>钩子方法的定义</b>：父类提供<b>默认实现</b>（通常返回一个默认值或什么都不做），
     * 子类<b>可以重写也可以不重写</b>的方法。
     *
     * <p>它和抽象方法的关键区别：
     * <ul>
     *   <li><b>抽象方法</b>：子类<b>不实现就编译不过</b>（强制）；</li>
     *   <li><b>钩子方法</b>：子类<b>不管它也能正常运行</b>（可选）。</li>
     * </ul>
     *
     * <p><b>钩子的两大用途</b>：
     * <ol>
     *   <li><b>控制流程走向</b>（本例：要不要执行某个步骤）；</li>
     *   <li><b>提供可选的扩展点</b>（例如 {@code beforePay()} / {@code afterPay()} 空方法，
     *       子类需要在支付前后插入逻辑时才重写。Spring 的
     *       {@code AbstractApplicationContext.refresh()} 里就有大量这种钩子）。</li>
     * </ol>
     *
     * <p><b>真实框架里的钩子举例</b>：
     * <ul>
     *   <li>{@code HttpServlet} 的 {@code init()} / {@code destroy()}——有默认实现，按需重写；</li>
     *   <li>Spring {@code JdbcTemplate} 的 {@code initTemplate()}；</li>
     *   <li>JUnit 5 的 {@code @BeforeEach}——虽然是注解形式，本质是钩子；</li>
     *   <li>{@code java.util.AbstractList} 的 {@code removeRange()}。</li>
     * </ul>
     *
     * @return {@code true} 表示需要检查库存（默认）；{@code false} 表示跳过步骤 ①
     * @see VirtualProductOrder 重写为 false，因为虚拟商品库存无限
     */
    protected boolean needsStockCheck() {
        return true;
    }

    /**
     * 钩子：交付凭证在通知消息里的<b>称呼</b>。默认「物流单号」。
     *
     * <p>这个钩子展示了钩子的第二种典型用法：<b>定制细节而不改变流程</b>。
     *
     * <p>{@link #notifyCustomer(OrderContext)} 的通知逻辑对所有订单类型都一样
     * （拼一句「您的订单已处理完成，XXX：YYY」），
     * 但那个 {@code XXX} 在不同类型下措辞不同：
     * <ul>
     *   <li>实物商品：「物流单号」；</li>
     *   <li>虚拟商品：「下载链接」；</li>
     *   <li>预售商品：「预计到货日」。</li>
     * </ul>
     *
     * <p><b>为什么不直接把整个 notifyCustomer() 设为抽象方法？</b>
     * 因为那样每个子类都要重写一遍<b>完全相同的拼接逻辑</b>，只有四个字不同。
     * <b>钩子让子类只需要提供那「四个字」。</b>
     *
     * <p><b>为什么不把 label 做成构造参数？</b>
     * 因为它是「订单类型的固有属性」，不是「每个订单实例的参数」。
     * 同一类型的所有订单，称呼都一样。用继承表达「类型差异」，用参数表达「实例差异」——
     * 这是判断该用哪个的基本准则。
     *
     * @return 交付凭证的称呼，用于拼接通知消息
     */
    protected String deliveryProofLabel() {
        return "物流单号";
    }

    /**
     * 钩子：支付<b>之前</b>的扩展点，默认什么都不做。
     *
     * <p>这是「空钩子」的典型形态——<b>父类提供一个空实现作为扩展点</b>，
     * 绝大多数子类不用管它，只有需要在特定时机插入逻辑的子类才重写。
     * 本项目的 {@link PreSaleOrder} 重写了它来做定金校验。
     *
     * <p><b>它由谁调用？——由模板方法 {@link #processOrder(OrderContext)} 调用，不是由子类调用。</b>
     * 这一点非常重要，原因有两条：
     * <ol>
     *   <li><b>好莱坞原则</b>：钩子的定义就是「父类在合适时机回调子类」。
     *       如果改成子类在自己的 {@code pay()} 里调 {@code beforePay(ctx)}，
     *       那就是「子类调用父类的流程编排」，方向反了；</li>
     *   <li><b>可靠性</b>：靠每个子类「记得写一行」来保证的机制是不成立的。
     *       新增第四种订单类型时如果忘了写，钩子就<b>静默失效</b>——
     *       编译器不会报错、运行时不会异常，只是校验悄悄不做了。
     *       这种 bug 在风控场景下是事故级的。</li>
     * </ol>
     *
     * <p><b>为什么不用「子类重写 pay() 时自己加校验」代替钩子？</b>
     * 因为 {@code pay()} 是<b>抽象方法</b>，父类没有实现可供 {@code super.pay(ctx)} 调用，
     * 所以「在支付这个步骤的前后统一插入逻辑」这件事<b>无法通过重写抽象方法实现</b>。
     * 空钩子提供了明确的、由框架保证调用的插入点。
     *
     * <p><b>钩子可以中止流程</b>：本方法内部调用 {@code ctx.abort(reason)} 之后，
     * 模板方法会检测到并<b>直接返回</b>，{@code pay()} 根本不会执行。
     * 也就是说钩子不只是「开关」，它还能当「守门员」。
     * {@code Client} 的 demo4-C 真实演示了这一点（￥0.10 的订单定金只有 0.03 元，被拦下）。
     *
     * <p><b>真实框架里的同类钩子</b>：
     * <ul>
     *   <li>Spring {@code AbstractApplicationContext.refresh()} 里的
     *       {@code postProcessBeanFactory()}、{@code onRefresh()}——都是空实现，按需重写；</li>
     *   <li>{@code HttpServlet.init()} / {@code destroy()}；</li>
     *   <li>{@code java.util.AbstractList.removeRange()}。</li>
     * </ul>
     *
     * @param ctx 订单上下文
     * @see PreSaleOrder#beforePay(OrderContext) 唯一的重写者，用于定金最低限额校验
     */
    protected void beforePay(OrderContext ctx) {
        // 默认空实现：这是一个可选的扩展点。
        // 参数 ctx 目前没被用到，但签名里必须保留——否则子类重写时拿不到订单数据。
    }

    // ==================================================================
    // 供子类复用的工具方法（不是模式的一部分，是工程便利）
    // ==================================================================

    /**
     * 生成一个带时间戳和序号的唯一流水号。
     *
     * <p><b>为什么放在父类而不是让每个子类自己写？</b>
     * 因为「生成流水号」这件事所有子类都要做，且做法完全相同。
     * 放在父类可以：① 消除重复；② 保证全公司流水号格式统一（对账系统依赖这一点）。
     *
     * <p>注意这不是「抽象方法」也不是「钩子」，而是<b>给子类用的工具方法</b>。
     * 模板方法模式的父类通常都会提供一批这样的辅助方法。
     *
     * @param prefix 前缀，用于区分业务类型（如 PAY / SHIP / PRE）
     * @return 形如 {@code PAY20261022153012-0001} 的流水号
     */
    protected final String generateSerialNo(String prefix) {
        // synchronized 保证并发下序号不重复。
        // 【为什么这里要考虑线程安全？】因为真实系统里订单处理必然是多线程的，
        // 静态的 sequence 字段会被并发访问。Demo 虽然是单线程，
        // 但写出线程不安全的示例代码会给读者错误的示范。
        synchronized (OrderProcessTemplate.class) {
            sequence++;
            return prefix + LocalDateTime.now().format(TS)
                    + "-" + String.format("%04d", sequence);
        }
    }
}
