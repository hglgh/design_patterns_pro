package com.patterns.behavioral.state;

import com.patterns.util.DisplayWidth;
import com.patterns.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 状态模式的五段演示。
 *
 * <p>运行方式（在 {@code design_patterns_pro} 目录下）：
 * <pre>
 *   javac --release 25 -encoding UTF-8 -g -d out -sourcepath src `
 *         src/com/patterns/behavioral/state/Client.java
 *
 *   java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out `
 *        com.patterns.behavioral.state.Client
 * </pre>
 *
 * <p><b>本类的输出是完全可复现的</b>：全程没有时间、随机数、耗时统计，
 * 物流单号由订单号派生，集合的展示顺序按枚举声明顺序固定。
 * 同样的代码在任何机器上跑，得到的字节完全一致。
 *
 * <p>五段演示的设计意图：
 * <table border="1">
 *   <caption>五段演示各证明什么</caption>
 *   <tr><th>#</th><th>演示</th><th>要证明的事</th></tr>
 *   <tr><td>1</td><td>走完一生</td><td>正常路径长什么样</td></tr>
 *   <tr><td>2</td><td>合法与非法交错</td><td>状态模式的价值在于<b>拒绝</b>，不只是"往前走"</td></tr>
 *   <tr><td>3</td><td>两种取消</td><td>同一个方法，不同状态，后果完全不同</td></tr>
 *   <tr><td>4</td><td>转移表对照</td><td>什么时候<b>不该</b>用状态模式</td></tr>
 *   <tr><td>5</td><td>自检 + 对比</td><td>两份独立规则互为校验；与策略/责任链的区别</td></tr>
 * </table>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1HappyPath();
        demo2MixedRefusals();
        demo3TwoCancelBranches();
        demo4TableCompare();
        demo5SelfCheckAndCompare();
    }

    /**
     * Demo 1：一张订单从待支付走到已完成。
     *
     * <p>这是最"平淡"的一段，但它是基准：后面所有关于拒绝、分支、
     * 对照的讨论，都以这条正常路径为参照。没有它，
     * "哪里不同"就无从谈起。
     */
    private static void demo1HappyPath() {
        section("1. 一张订单走完它的一生");

        Order order = new Order("O-2001", "299.00");
        System.out.println("  订单 " + order.orderNo()
                + "，金额 " + Money.yuan(order.amount())
                + "，初始状态：" + order.state().displayName());
        System.out.println("  开局可做：" + order.describeAllowed());
        System.out.println();

        int seq = 0;
        printStep(++seq, order, order.pay());
        printStep(++seq, order, order.ship());
        printStep(++seq, order, order.confirm());

        printHistory(order);
    }

    /**
     * Demo 2：同一张单上，合法与非法交错出现。
     *
     * <p><b>这一段是整个示例的重点。</b> 很多状态模式的示例只演示
     * "状态一路往前走"，那看完之后读者会以为状态模式就是"把 if 拆成类"。
     * 真正让它有价值的是：<b>它同样擅长拒绝</b>。
     *
     * <p>七次尝试里有四次被挡下来，而且每一次挡下来的理由都不一样：
     * 待支付不能发货、已支付不能重复支付、已发货不能取消、已完成不能再确认。
     * 这四条规则分别住在四个状态类里，<b>互相不知道对方的存在</b>。
     */
    private static void demo2MixedRefusals() {
        section("2. 同一张单上，合法与非法交错出现");

        Order order = new Order("O-2002", "88.00");
        System.out.println("  订单 " + order.orderNo()
                + "，金额 " + Money.yuan(order.amount())
                + "，初始状态：" + order.state().displayName());
        System.out.println();

        int seq = 0;
        // 待支付：发货被拒
        printStep(++seq, order, order.ship());
        // 待支付：支付成功
        printStep(++seq, order, order.pay());
        // 已支付：重复支付被拒
        printStep(++seq, order, order.pay());
        // 已支付：发货成功
        printStep(++seq, order, order.ship());
        // 已发货：取消被拒
        printStep(++seq, order, order.cancel());
        // 已发货：确认收货成功
        printStep(++seq, order, order.confirm());
        // 已完成：再确认被拒
        printStep(++seq, order, order.confirm());

        printHistory(order);
    }

    /**
     * Demo 3：同一个 {@code cancel()}，两种完全不同的后果。
     *
     * <p>这是"状态模式到底解决了什么"最直观的一次对照。
     * 两张订单，不同的只是"取消之前有没有付过钱"，
     * 而调用方写的代码<b>一模一样</b>。
     *
     * <p>如果用 {@code if-else} 写，这个判断会长成这样：
     * <pre>
     *   public void cancel(Order order) {
     *       if (order.getStatus() == PAID) {
     *           refund(order);          // 已支付：要退款
     *       } else if (order.getStatus() == PENDING) {
     *           releaseStock(order);    // 待支付：只释放库存
     *       } else {
     *           throw new BizException("当前状态不能取消");
     *       }
     *       order.setStatus(CANCELLED);
     *   }
     * </pre>
     * 这段代码在一处还好。问题是它会被复制到 Web 接口、定时任务、
     * 客服后台、运营工具……<b>每一个入口都要抄一遍，而且总有一个抄漏分支。</b>
     */
    private static void demo3TwoCancelBranches() {
        section("3. 同一个 cancel()，两种完全不同的后果");

        System.out.println("  --- 没付钱就取消 ---");
        Order unpaid = new Order("O-2003", "50.00");
        System.out.println("  订单 " + unpaid.orderNo()
                + "，金额 " + Money.yuan(unpaid.amount()));
        System.out.println();
        printStep(1, unpaid, unpaid.cancel());

        System.out.println("  --- 付了钱之后取消 ---");
        Order paid = new Order("O-2004", "50.00");
        System.out.println("  订单 " + paid.orderNo()
                + "，金额 " + Money.yuan(paid.amount()));
        System.out.println();
        int seq = 0;
        printStep(++seq, paid, paid.pay());
        printStep(++seq, paid, paid.cancel());

        System.out.println("  两次调用的都是 order.cancel()，一行代码没变。");
        System.out.println("  但一次是「占用库存已释放」，一次是「将原路退回 "
                + Money.yuan(unpaid.amount()) + "」。");
        System.out.println("  调用方没有写任何 if —— 分支全在状态对象里。");
        System.out.println();
    }

    /**
     * Demo 4：同一套规则用转移表写，跑得通，但丢了副作用。
     *
     * <p>这一段是<b>诚实降级</b>：承认状态模式不是唯一解，
     * 也承认在某些场景下它确实是过度设计。
     *
     * <p>关键在于最后那两行"物流单号"的对比——它把"表表达不了副作用"
     * 这件事从一句断言变成了一个可验证的事实。
     */
    private static void demo4TableCompare() {
        section("4. 同一套规则用「表」写：跑得通，但丢了副作用");

        System.out.println("  转移表（一张表就能表达清楚）：");
        printIndented(TransitionTable.describeAsText(), 4);
        System.out.println();

        // ---- 用表跑一遍 ----
        Order byTable = new Order("O-2005", "99.00");
        System.out.println("  用表跑一遍 " + byTable.orderNo()
                + "（" + Money.yuan(byTable.amount()) + "），只记录状态序列：");
        OrderState cursor = PendingState.INSTANCE;
        for (OrderAction action : List.of(OrderAction.PAY, OrderAction.SHIP, OrderAction.CONFIRM)) {
            Optional<OrderState> next = TransitionTable.next(cursor, action);
            System.out.println("    " + cursor.displayName()
                    + " --" + action.label() + "--> "
                    + next.map(OrderState::displayName).orElse("（不允许）"));
            cursor = next.orElse(cursor);
        }
        System.out.println("    最终状态：" + cursor.displayName());
        System.out.println("    物流单号：" + orEmpty(byTable.trackingNo()));
        System.out.println("    订单对象自己的状态：" + byTable.state().displayName()
                + "   ← 表从头到尾没有碰过这个对象");
        System.out.println();

        // ---- 用状态模式跑一遍 ----
        Order byState = new Order("O-2005", "99.00");
        System.out.println("  同一个订单号，换成状态模式再跑一遍：");
        byState.pay();
        byState.ship();
        byState.confirm();
        System.out.println("    最终状态：" + byState.state().displayName());
        System.out.println("    物流单号：" + orEmpty(byState.trackingNo()));
        System.out.println();

        System.out.println("  两次的状态序列完全一致：待支付 → 已支付 → 已发货 → 已完成。");
        System.out.println("  但表漏掉的东西有两处：");
        System.out.println("    · 物流单号是空的；");
        System.out.println("    · 订单对象自己的状态还停在「待支付」—— 表根本没有碰过它。");
        System.out.println();
        System.out.println("  为什么？表里每个格子只存了「目标状态」一个信息，");
        System.out.println("  挂不住「发货时要把单号写进订单」这件事。");
        System.out.println("  要补上它，只能回到调用方写 if (action == SHIP) { ... }，");
        System.out.println("  于是表退化成「查表 + 一长串 if」，比状态模式更乱，");
        System.out.println("  而且还得把 setState() 请回来 —— 那是本模式最该删掉的东西。");
        System.out.println();
        System.out.println("  判据：转移只有「转到哪」→ 用表；");
        System.out.println("        转移还要顺手做事 → 用状态模式。");
        System.out.println();
    }

    /**
     * Demo 5：一致性自检 + 三个相似模式的对比。
     *
     * <p><b>自检在验什么？</b>
     * {@code allowedActions()} 写在五个状态类里，转移表写在
     * {@link TransitionTable} 里——这是<b>两份独立的规则描述</b>。
     * 它们本可以各自出错、互相矛盾。遍历全部 20 种组合逐一比对，
     * 任何一处写岔都会在这里暴露。
     *
     * <p>这比"单元测试只测正常路径"强得多：正常路径通常只有 4~5 条，
     * 剩下的 15 条非法组合才是真正容易写错、也最容易被忽略的部分。
     *
     * <p>对比表放在最后，是因为只有看完全部四段演示之后，
     * "状态 vs 策略 vs 责任链"这三行才不是空话。
     */
    private static void demo5SelfCheckAndCompare() {
        section("5. 一致性自检与三个模式的对比");

        int combos = 0;
        int allowedCount = 0;
        int deniedCount = 0;
        int acceptedOk = 0;
        int mismatch = 0;
        List<String> details = new ArrayList<>();

        for (OrderState state : TransitionTable.states()) {
            for (OrderAction action : OrderAction.values()) {
                combos++;
                boolean stateSays = state.allowedActions().contains(action);
                boolean tableSays = TransitionTable.allows(state, action);
                if (stateSays != tableSays) {
                    mismatch++;
                    details.add(state.displayName() + " + " + action.label()
                            + "：状态类说 " + stateSays + "，转移表说 " + tableSays);
                }
                if (stateSays) {
                    allowedCount++;
                    // 用一张一次性订单真跑一次，验证"说允许"和"真能做"是一回事
                    Order probe = new Order("PROBE-1", "1.00");
                    TransitionResult result = state.accept(action, probe);
                    if (result.success()) {
                        acceptedOk++;
                    } else {
                        mismatch++;
                        details.add(state.displayName() + " + " + action.label()
                                + "：allowedActions 说允许，accept 却返回拒绝");
                    }
                } else {
                    deniedCount++;
                }
            }
        }

        System.out.println("  --- 自检："
                + TransitionTable.states().size() + " 个状态 × "
                + OrderAction.values().length + " 个动作 = "
                + combos + " 种组合 ---");
        System.out.println("    状态类说允许：" + allowedCount + " 种；说禁止：" + deniedCount + " 种");
        System.out.println("    其中 accept() 实际成功：" + acceptedOk + " / " + allowedCount);
        System.out.println("    两份规则（状态类 vs 转移表）不一致：" + mismatch + " 处");
        if (!details.isEmpty()) {
            for (String detail : details) {
                System.out.println("      - " + detail);
            }
        }
        System.out.println();

        System.out.println("  --- 三个「很像」的模式 ---");
        System.out.println();
        printCompareHeader();
        printCompareRow("一句话", "状态决定行为", "算法可替换", "处理者接力");
        printCompareRow("调用方写什么", "order.pay()", "setStrategy(s)", "chain.submit(req)");
        printCompareRow("关键问题", "我现在是什么状态", "用哪种算法", "该谁处理");
        printCompareRow("会不会拒绝", "会，非法转移被挡", "不会，总要算结果", "会，无人处理返回空");
        printCompareRow("谁在变", "Context 内部的状态", "注入进来的算法", "链上的节点");
        printCompareRow("典型误用", "拿状态做流程编排", "用策略消灭所有 if", "把管道当责任链");
        System.out.println();
    }

    // ==================================================================
    // 打印辅助
    // 只负责排版，不含任何业务判断
    // ==================================================================

    /**
     * 打印一段演示的标题。
     *
     * @param title 标题文本
     */
    private static void section(String title) {
        System.out.println("=== " + title + " ===");
        System.out.println();
    }

    /**
     * 打印一次动作尝试的完整快照。
     *
     * <p>四行分别是：动作与结果、为什么、业务数据、当前可做的动作。
     * 第 3、4 行读的是<b>执行之后</b>的订单，所以能看到状态转移
     * 到底改动了什么——这是验证"副作用没有被丢掉"的直接证据。
     *
     * @param seq    这是本次演示里的第几次尝试
     * @param order  订单
     * @param result 转移结果
     */
    private static void printStep(int seq, Order order, TransitionResult result) {
        String mark = result.success() ? "[OK]" : "[--]";
        System.out.println("  [" + padSeq(seq) + "] " + mark + " " + result.describe());
        System.out.println("       说明：" + result.message());
        System.out.println("       数据：" + order.describeData());
        System.out.println("       当前：" + order.state().displayName()
                + "   可做：" + order.describeAllowed());
        System.out.println();
    }

    /**
     * 打印完整的审计轨迹。
     *
     * <p>轨迹里同时包含成功和被拒绝的步骤——这是刻意的。
     * 只打成功那几步，读者会以为"轨迹 = 状态变化记录"；
     * 把失败也打出来，才能看出它其实是<b>完整的操作日志</b>。
     *
     * @param order 订单
     */
    private static void printHistory(Order order) {
        List<HistoryEntry> history = order.history();
        long success = history.stream().filter(entry -> entry.result().success()).count();
        System.out.println("  轨迹：共 " + history.size() + " 步（"
                + success + " 步成功，" + (history.size() - success) + " 步被拒绝）");
        for (HistoryEntry entry : history) {
            System.out.println("    #" + entry.step() + " " + entry.result().describe());
        }
        System.out.println();
    }

    /**
     * 把多行文本整体缩进后打印。
     *
     * @param text  多行文本
     * @param count 缩进空格数
     */
    private static void printIndented(String text, int count) {
        String prefix = " ".repeat(count);
        for (String line : text.split("\n", -1)) {
            if (!line.isEmpty()) {
                System.out.println(prefix + line);
            }
        }
    }

    /** 打印对比表的表头与分隔线。 */
    private static void printCompareHeader() {
        System.out.println("  " + DisplayWidth.padRight("维度", 14)
                + DisplayWidth.padRight("状态模式", 22)
                + DisplayWidth.padRight("策略模式", 22)
                + "责任链");
        // 分隔线要与表头的实际显示宽度一致：14 + 22 + 22 + “责任链”的宽度
        System.out.println("  " + DisplayWidth.line('-', 14 + 22 + 22 + DisplayWidth.width("责任链")));
    }

    /**
     * 打印对比表的一行。
     *
     * @param dim    维度名
     * @param state  状态模式的说法
     * @param strat  策略模式的说法
     * @param chain  责任链的说法
     */
    private static void printCompareRow(String dim, String state, String strat, String chain) {
        System.out.println("  " + DisplayWidth.padRight(dim, 14)
                + DisplayWidth.padRight(state, 22)
                + DisplayWidth.padRight(strat, 22)
                + chain);
    }

    /**
     * 把序号补到两位，保证 {@code [ 1]} 和 {@code [10]} 的方括号对齐。
     *
     * @param seq 序号
     * @return 两位右对齐的序号文本
     */
    private static String padSeq(int seq) {
        return seq < 10 ? " " + seq : String.valueOf(seq);
    }

    /**
     * 空值显示成 {@code （空）}，让"没有单号"和"单号是空字符串"一眼可分。
     *
     * @param value 可能为空的字符串
     * @return 原值或 {@code （空）}
     */
    private static String orEmpty(String value) {
        return value == null ? "（空）" : value;
    }
}
