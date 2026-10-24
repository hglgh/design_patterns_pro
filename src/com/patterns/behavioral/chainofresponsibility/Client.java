package com.patterns.behavioral.chainofresponsibility;

import com.patterns.behavioral.chainofresponsibility.approval.ApprovalChain;
import com.patterns.behavioral.chainofresponsibility.approval.ApprovalContext;
import com.patterns.behavioral.chainofresponsibility.approval.ApprovalDecision;
import com.patterns.behavioral.chainofresponsibility.approval.LimitApprover;
import com.patterns.behavioral.chainofresponsibility.approval.RefundRequest;
import com.patterns.behavioral.chainofresponsibility.discount.CouponRule;
import com.patterns.behavioral.chainofresponsibility.discount.DiscountOrder;
import com.patterns.behavioral.chainofresponsibility.discount.DiscountPipeline;
import com.patterns.behavioral.chainofresponsibility.discount.DiscountRule;
import com.patterns.util.DisplayWidth;
import com.patterns.util.Money;

import java.util.List;

/**
 * 客户端：责任链模式的完整演示。
 *
 * <p>运行方式（在 {@code design_patterns_pro} 目录下）：
 * <pre>
 *   javac --release 25 -encoding UTF-8 -g -d out -sourcepath src ^
 *         src/com/patterns/behavioral/chainofresponsibility/Client.java
 *   java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out ^
 *        com.patterns.behavioral.chainofresponsibility.Client
 * </pre>
 *
 * <p>五段演示分别回答一个问题：
 * <ol>
 *   <li>{@link #demo1ChainWalk} —— 一条请求怎么在链上走？为什么它会在半路停下？</li>
 *   <li>{@link #demo2Veto} —— 为什么"否决"也必须放在链首？</li>
 *   <li>{@link #demo3OrderMatters} —— 顺序换了会怎样？组装期能拦住哪些错？</li>
 *   <li>{@link #demo4Pipeline} —— <b>什么时候不该用责任链</b>（原项目的折扣场景）</li>
 *   <li>{@link #demo5Compare} —— 链 vs 管道，一张表说清区别</li>
 * </ol>
 *
 * <p><b>注意第 4 段的位置</b>：它没有用 {@code Handler}，一个类都没用。
 * 这不是遗漏，是本 Demo 想说的最重要的一句话——
 * <b>判断"该不该用某个模式"，比"会用某个模式"重要得多。</b>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1ChainWalk();
        demo2Veto();
        demo3OrderMatters();
        demo4Pipeline();
        demo5Compare();
    }

    // ==================================================================
    // 演示 1：一张退款单怎么在链上走
    // ==================================================================

    /**
     * 同一个链、五张金额不同的退款单，看它们分别停在哪一个节点。
     *
     * <p>重点看两样东西：
     * <ul>
     *   <li><b>轨迹长度</b>：金额越小，走到的节点越少。这就是"链"和"管道"最本质的区别；</li>
     *   <li><b>轨迹内容</b>：每个节点都写清了"为什么无权/为什么批准"，
     *       而不是只留一句"处理完成"。</li>
     * </ul>
     */
    private static void demo1ChainWalk() {
        System.out.println("=== 1. 一张退款单怎么在链上走 ===");
        ApprovalChain chain = ApprovalChain.standard();
        System.out.println("  链的形状：" + chain.describe());
        System.out.println();

        record Case(String orderNo, String amount, int days) {
        }
        List<Case> cases = List.of(
                new Case("R-1001", "150", 3),
                new Case("R-1002", "800", 3),
                new Case("R-1003", "8000", 3),
                new Case("R-1004", "50000", 3),
                new Case("R-1005", "500000", 3));

        int nodeCount = chain.describe().split(" → ").length;
        for (Case item : cases) {
            RefundRequest request = new RefundRequest(
                    item.orderNo(), Money.of(item.amount()), "商品与描述不符", item.days());
            ApprovalDecision decision = chain.submit(request);
            printDecision(request, decision, nodeCount);
        }
    }

    // ==================================================================
    // 演示 2：链首的一票否决
    // ==================================================================

    /**
     * 两笔一模一样的 800 元退款，只有"距下单天数"不同。
     *
     * <p>30 天那张在<b>第 1 个节点</b>就被驳回，一个审批人都没惊动。
     * 如果 {@code PolicyGuard} 被放到链尾，这两张单子都会先走完四个审批人——
     * 最后那张仍然会被驳回，<b>但成本完全不同</b>：
     * 多出四次无意义的判断、四个人收到通知、审计日志里多出四条"无权"噪声。
     *
     * <p>这就是"顺序"在责任链里的真正含义：
     * <b>它不只是决定谁来处理，还决定了"多少工作被浪费"。</b>
     */
    private static void demo2Veto() {
        System.out.println("=== 2. 链首的一票否决：同样 800 元，只差「距下单天数」 ===");
        System.out.println();

        ApprovalChain chain = ApprovalChain.standard();
        int nodeCount = chain.describe().split(" → ").length;

        RefundRequest within = new RefundRequest("R-2001", Money.of("800"), "不想要了", 3);
        RefundRequest expired = new RefundRequest("R-2002", Money.of("800"), "不想要了", 30);

        printDecision(within, chain.submit(within), nodeCount);
        printDecision(expired, chain.submit(expired), nodeCount);
    }

    // ==================================================================
    // 演示 3：顺序就是策略
    // ==================================================================

    /**
     * 同样一笔 800 元的退款，只把链的顺序颠倒过来，<b>批准人就从主管变成了经理</b>。
     *
     * <p>"批不批"没变（都批了），"谁批的"变了。
     * 这看起来只是署名问题，但在真实业务里它意味着：
     * 绩效算谁的、审批记录里签的是谁、出问题追谁的责。
     * <b>责任链的顺序是业务规则，不是代码风格。</b>
     *
     * <p>后半段演示组装期的三道保险，以及 {@code linkTo} 返回值那个坑。
     * 这些校验都在<b>启动时</b>触发，代价是一次 O(n) 遍历；
     * 不做的代价是运行期 {@code StackOverflowError}——而且是在某笔真实订单进来的时候。
     */
    private static void demo3OrderMatters() {
        System.out.println("=== 3. 顺序就是策略：同样 800 元，换顺序就换了批准人 ===");

        ApprovalChain forward = ApprovalChain.of(List.of(
                new LimitApprover("客服专员", "200"),
                new LimitApprover("客服主管", "2000"),
                new LimitApprover("售后经理", "20000")));
        ApprovalChain reversed = ApprovalChain.of(List.of(
                new LimitApprover("售后经理", "20000"),
                new LimitApprover("客服主管", "2000"),
                new LimitApprover("客服专员", "200")));

        System.out.println("  正序：" + forward.describe());
        System.out.println("  逆序：" + reversed.describe());
        System.out.println();

        RefundRequest firstCase = new RefundRequest("R-3001", Money.of("800"), "少件", 3);
        printDecision(firstCase, forward.submit(firstCase), 3);

        RefundRequest secondCase = new RefundRequest("R-3002", Money.of("800"), "少件", 3);
        printDecision(secondCase, reversed.submit(secondCase), 3);

        System.out.println("  --- 组装期的三道保险 ---");
        Handler<ApprovalContext, ApprovalDecision> first = new LimitApprover("节点A", "100");
        first.linkTo(new LimitApprover("节点B", "200"));
        try {
            first.linkTo(new LimitApprover("节点C", "300"));
        } catch (IllegalStateException e) {
            System.out.println("  ✓ " + e.getMessage());
        }

        Handler<ApprovalContext, ApprovalDecision> left = new LimitApprover("节点X", "100");
        Handler<ApprovalContext, ApprovalDecision> right = new LimitApprover("节点Y", "200");
        left.linkTo(right);
        try {
            right.linkTo(left);
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ " + e.getMessage());
        }

        Handler<ApprovalContext, ApprovalDecision> self = new LimitApprover("节点Z", "100");
        try {
            self.linkTo(self);
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ " + e.getMessage());
        }

        System.out.println();
        System.out.println("  --- linkTo 的返回值是个坑 ---");
        // 你以为 head 是"甲"，实际上拿到的是"乙"——因为 linkTo 返回的是"下一个"
        Handler<ApprovalContext, ApprovalDecision> head =
                new LimitApprover("甲", "100").linkTo(new LimitApprover("乙", "200"));
        System.out.println("  写 head = 甲.linkTo(乙)，head.name() 却是：" + head.name());
        System.out.println("  所以请用 Handler.chain(List.of(甲, 乙))，它保证返回头节点");
        System.out.println();
    }

    // ==================================================================
    // 演示 4：该用 List 的时候别用链表
    // ==================================================================

    /**
     * 原项目的折扣场景：<b>每一条规则都必须执行</b>，没有任何一步需要"停下来"。
     *
     * <p>这里修掉了原项目的三个真实问题，三张订单一一对应：
     * <ul>
     *   <li>{@code D-4003}（恰好 200 元）：原项目 {@code > 200} 不触发满减，本实现触发；</li>
     *   <li>{@code D-4004}（85 元用券）：原项目不校验门槛直接减 30，本实现给出"未达门槛"的原因；</li>
     *   <li>{@code D-4005}（25 元用 30 元券）：原项目<b>减成负数</b>，本实现最多减到 0。</li>
     * </ul>
     * 三条都不需要责任链，只需要一个 {@code List} 加一层循环。
     */
    private static void demo4Pipeline() {
        System.out.println("=== 4. 该用 List 的时候别用链表：折扣管道 ===");
        DiscountPipeline pipeline = DiscountPipeline.standard();
        System.out.println("  管道顺序：" + String.join(" → ",
                pipeline.rules().stream().map(DiscountRule::name).toList()));
        System.out.println();

        record Case(String orderNo, String amount, boolean member, boolean coupon, String note) {
        }
        List<Case> cases = List.of(
                new Case("D-4001", "250", true, true, "原项目同款用例"),
                new Case("D-4002", "200", false, false, "恰好 200 元：原项目不触发满减"),
                new Case("D-4003", "85", true, true, "未达券门槛 100 元"),
                new Case("D-4004", "1000", true, true, "叠加三档"));

        for (Case item : cases) {
            DiscountOrder order = DiscountOrder.of(item.orderNo(), item.amount(), item.member(), item.coupon());
            printDiscount(pipeline.apply(order), item.note());
        }

        System.out.println("  --- 券面额大于应付金额时会怎样 ---");
        // 原项目的券没有门槛，25 元的订单会被 30 元的券减成 -5 元
        DiscountPipeline noThreshold = new DiscountPipeline(List.of(new CouponRule("SAVE30", "30", "0.01")));
        DiscountOrder tiny = DiscountOrder.of("D-4005", "25", false, true);
        printDiscount(noThreshold.apply(tiny), "原项目在这里算出 -25.0");
        System.out.println("  注意轨迹里券申报的是「减 ￥30.00」，账单里记的是「-￥25.00」——");
        System.out.println("  前者是规则想减多少，后者是系统实际扣了多少。两个数字都必须留下。");
        System.out.println();
    }

    // ==================================================================
    // 演示 5：链 vs 管道
    // ==================================================================

    /**
     * 一张表把两种结构摆在一起。
     *
     * <p>判断用哪个，只需要问一句：<b>这一步处理完之后，后面还该不该执行？</b>
     */
    private static void demo5Compare() {
        System.out.println("=== 5. 责任链 vs 管道：一句话判据 ===");
        System.out.println();
        printRow("维度", "责任链（Handler + next）", "管道（List 循环）");
        printRow("命中之后", "立刻停止，后面的节点不执行", "继续执行，后面的规则照样要跑");
        printRow("结构", "每个节点持有 next，递归下传", "一个 List，for 循环");
        printRow("返回值", "Optional：空 = 一路都没人处理", "每条规则都有结果，含未命中的原因");
        printRow("典型场景", "审批、过滤、拦截、熔断降级", "计价、校验清单、后置处理");
        printRow("原项目", "——", "折扣叠加（被误写成链表，多写 200 行）");
        System.out.println();
        System.out.println("  判据：这条规则命中之后，后面的规则还该不该执行？");
        System.out.println("        该 → 管道        不该 → 责任链");
        System.out.println();
    }

    // ==================================================================
    // 打印辅助
    // ==================================================================

    /**
     * 打印一笔退款的完整轨迹与结论。
     *
     * <p>这里用了<b>模式匹配的 {@code switch}</b>（JDK 21 正式、JDK 25 稳定）：
     * {@code ApprovalDecision} 是 sealed 的，所以三个分支穷尽之后<b>不需要 default</b>。
     * 将来若新增一种结论，这个 switch 会直接编译失败，而不是在运行期悄悄走 default。
     *
     * @param request   原始请求
     * @param decision  链给出的结论
     * @param nodeCount 链上一共几个节点，用于显示"实际惊动了几个人"
     */
    private static void printDecision(RefundRequest request, ApprovalDecision decision, int nodeCount) {
        System.out.println("  退款单 " + request.orderNo() + "  申请 " + Money.yuan(request.amount())
                + "  距下单 " + request.daysAfterPurchase() + " 天");
        for (ApprovalContext.Step step : decision.auditTrail()) {
            System.out.println("    " + DisplayWidth.padRight(step.handler(), 14)
                    + DisplayWidth.padRight(step.verdict(), 6) + step.detail());
        }
        switch (decision) {
            case ApprovalDecision.Granted granted ->
                    System.out.println("    ⇒ 批准 " + Money.yuan(granted.approvedAmount())
                            + "，批准人：" + granted.approver());
            case ApprovalDecision.Denied denied ->
                    System.out.println("    ⇒ 驳回：" + denied.reason() + "（" + denied.rejectedBy() + "）");
            case ApprovalDecision.NeedsHumanReview review ->
                    System.out.println("    ⇒ 转人工：" + review.reason());
        }
        System.out.println("    （本单实际惊动了 " + decision.auditTrail().size() + "/" + nodeCount + " 个节点）");
        System.out.println();
    }

    /**
     * 打印一笔订单的折扣轨迹。
     *
     * @param result 管道结果
     * @param note   这笔订单想验证什么
     */
    private static void printDiscount(DiscountPipeline.Result result, String note) {
        DiscountOrder order = result.order();
        System.out.println("  订单 " + order.orderNo() + "  原价 " + Money.yuan(order.originalAmount())
                + "（" + note + "）");
        for (DiscountPipeline.Step step : result.steps()) {
            System.out.println("    " + (step.applied() ? "✔" : "·") + " "
                    + DisplayWidth.padRight(step.rule(), 32) + step.reason());
        }
        System.out.println("    ⇒ 应付 " + Money.yuan(order.payableAmount())
                + "，共优惠 " + Money.yuan(order.totalDiscount()));
        if (!order.appliedRules().isEmpty()) {
            System.out.println("    账单：" + String.join(" | ", order.appliedRules()));
        }
        System.out.println();
    }

    /**
     * 打印一行三列表格。
     *
     * @param a 第一列
     * @param b 第二列
     * @param c 第三列
     */
    private static void printRow(String a, String b, String c) {
        System.out.println("  " + DisplayWidth.padRight(a, 12)
                + DisplayWidth.padRight(b, 34) + c);
    }
}
