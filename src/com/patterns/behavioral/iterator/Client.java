package com.patterns.behavioral.iterator;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * 迭代器模式的演示入口。
 *
 * <p>六段 Demo，每段对应原项目的一个具体问题：
 *
 * <ol>
 *   <li>增强 for + 两个独立游标 —— 原项目没实现 {@code Iterable}；</li>
 *   <li>同一棵树两种遍历顺序 —— 原项目把遍历写死在数组下标里；</li>
 *   <li>遍历算法跟着 {@code Iterable} 走 —— 原项目只认自己的容器；</li>
 *   <li>迭代器可以再包装 —— 原项目想加筛选只能改容器；</li>
 *   <li>遍历中改结构立刻报错 —— 原项目没有 {@code modCount}；</li>
 *   <li>取到底抛异常而非返回 {@code null} —— 原项目让错误延后爆发。</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public final class Client {

    /**
     * 工具类，不实例化。
     */
    private Client() {
    }

    /**
     * 程序入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        demoOneTree();
        demoTwoOrders();
        demoAlgorithmFollowsContainer();
        demoFilter();
        demoFailFast();
        demoExhausted();
        summary();
    }

    /**
     * Demo 1：一棵真实的审批流树，一次增强 for 遍历。
     *
     * <p>这段演示三件事：
     *
     * <ul>
     *   <li>树是真的树（有分支、有顺序块），不是平坦数组；</li>
     *   <li>增强 for 能直接用，因为 {@link Workflow} 实现了
     *       {@link Iterable}；</li>
     *   <li>每次 {@code iterator()} 都是新游标，两个游标互不干扰。</li>
     * </ul>
     */
    private static void demoOneTree() {
        section("Demo 1：一棵审批流树，一次增强 for 遍历");
        Workflow flow = buildFlow();

        System.out.println("  树形结构（这里用递归打印，只是为了看形状）：");
        printTree(flow.roots().get(0), "", true, true);

        System.out.println();
        System.out.println("  增强 for 遍历（编译器把它翻成 iterator() + hasNext() + next()）：");
        int count = 0;
        for (WorkflowNode node : flow) {
            count++;
            System.out.printf("    %d. %s%n", count, node);
        }
        System.out.println("  共 " + count + " 个节点");

        System.out.println();
        System.out.println("  同时开两个游标，各自记各自的进度：");
        Iterator<WorkflowNode> first = flow.iterator();
        Iterator<WorkflowNode> second = flow.iterator();
        System.out.println("    first  第 1 个：" + first.next());
        System.out.println("    first  第 2 个：" + first.next());
        System.out.println("    second 第 1 个：" + second.next());
        System.out.println("    first  第 3 个：" + first.next());
        System.out.println("  如果 iterator() 返回同一个实例，上面的 second 会从 first 停下的地方继续");
    }

    /**
     * Demo 2：同一棵树，两种遍历顺序。
     *
     * <p>两次遍历共用同一份树结构，唯一的差别是
     * {@link Workflow#iterator(TraversalOrder)} 传的参数不同。
     *
     * <p>{@link Workflow}、{@link WorkflowNode}、
     * {@link StepNode}、{@link BranchNode}、{@link SequenceNode}
     * 全都没有"顺序"这个概念 —— 顺序只活在迭代器里。
     */
    private static void demoTwoOrders() {
        section("Demo 2：同一棵树，两种顺序");
        Workflow flow = buildFlow();

        System.out.println("  深度优先 iterator(TraversalOrder.DEPTH_FIRST)：");
        printWithIndex(flow.iterator(TraversalOrder.DEPTH_FIRST));

        System.out.println();
        System.out.println("  广度优先 iterator(TraversalOrder.BREADTH_FIRST)：");
        printWithIndex(flow.iterator(TraversalOrder.BREADTH_FIRST));

        System.out.println();
        System.out.println("  两次遍历的输出条数一样，顺序完全不同，而树本身没有被改过一个字段");
    }

    /**
     * Demo 3：遍历算法跟着 {@code Iterable} 走，不跟着 {@link Workflow} 走。
     *
     * <p>{@link #printAll} 的参数类型是 {@code Iterable<WorkflowNode>}。
     * 它完全不认识 {@link Workflow}，却能打印整棵树；
     * 它也不认识 {@link List}，却能打印一个临时列表。
     *
     * <p>这就是"遍历协议"的价值：调用方只需要承诺"我能被遍历"，
     * 至于怎么遍历、遍历的是树还是数组，由实现自己决定。
     */
    private static void demoAlgorithmFollowsContainer() {
        section("Demo 3：遍历算法跟着 Iterable 走，不跟着 Workflow 走");
        Workflow flow = buildFlow();

        printAll("传入 Workflow（一棵树）", flow);

        List<WorkflowNode> adHoc = List.of(
                step("临时加签", StepType.APPROVAL),
                step("临时抄送", StepType.NOTIFY)
        );
        System.out.println();
        printAll("传入 List.of(...)（一个扁平列表）", adHoc);

        System.out.println();
        System.out.println("  printAll 只声明了 Iterable<WorkflowNode>，");
        System.out.println("  它既不知道树有多深，也不知道元素存在数组还是链表里");
    }

    /**
     * Demo 4：给迭代器再套一层，只取审批节点。
     *
     * <p>同一个 {@link FilteringIterator} 类，被包装的对象换一次，
     * 过滤就自动换到另一种遍历顺序上 —— 这是"组合"而不是"继承"。
     */
    private static void demoFilter() {
        section("Demo 4：给迭代器再套一层，只取审批节点");
        Workflow flow = buildFlow();

        Predicate<WorkflowNode> onlyApproval = node ->
                node instanceof StepNode step && step.type() == StepType.APPROVAL;

        System.out.println("  在深度优先上过滤：");
        printWithIndex(new FilteringIterator(
                flow.iterator(TraversalOrder.DEPTH_FIRST),
                onlyApproval,
                "只取审批节点"));

        System.out.println();
        System.out.println("  在广度优先上过滤（同一个 FilteringIterator 类，只换了被包装的迭代器）：");
        printWithIndex(new FilteringIterator(
                flow.iterator(TraversalOrder.BREADTH_FIRST),
                onlyApproval,
                "只取审批节点"));

        System.out.println();
        System.out.println("  两次结果恰好相同：两个审批节点都在最深层，左右顺序也没变。");
        System.out.println("  过滤条件和遍历顺序是两个正交的维度 —— 组合起来时，");
        System.out.println("  顺序差异可能被树结构「藏」起来，不一定每次都能看出来。");
        System.out.println("  另外，七个非审批节点被跳过了但并没有消失：");
        System.out.println("  FilteringIterator 在 hasNext() 里把它们预读出来再跳过，");
        System.out.println("  它们仍然在树的原处，换一个条件就能重新被取到");
    }

    /**
     * Demo 5：遍历途中改结构，立刻抛异常。
     *
     * <p>注意两处对比：
     *
     * <ul>
     *   <li>{@code hasNext()} 仍然返回 {@code true} —— 它只查栈，
     *       不查 {@code modCount}，这是刻意与 JDK 保持一致；</li>
     *   <li>{@code next()} 立刻抛 {@link ConcurrentModificationException}
     *       —— 真正的检查在这里。</li>
     * </ul>
     */
    private static void demoFailFast() {
        section("Demo 5：遍历途中改结构，立刻抛 ConcurrentModificationException");
        Workflow flow = new Workflow("小流程")
                .add(step("开始", StepType.START))
                .add(step("审批", StepType.APPROVAL));

        Iterator<WorkflowNode> cursor = flow.iterator();
        System.out.println("  取了第 1 个：" + cursor.next());
        System.out.println("  hasNext() = " + cursor.hasNext() + "（栈里还压着「审批」）");

        System.out.println("  现在往审批流里追加一个根节点……");
        flow.add(step("中途塞进来的结束节点", StepType.END));

        System.out.println("  hasNext() 仍然返回 " + cursor.hasNext()
                + " —— 它只查栈，不查 modCount");
        try {
            cursor.next();
            System.out.println("  没有抛异常，这是 bug");
        } catch (ConcurrentModificationException e) {
            System.out.println("  next() 抛出：" + e.getClass().getSimpleName());
            System.out.println("  消息：" + e.getMessage());
        }
        System.out.println("  新加的节点不会出现在这次遍历里 —— 遍历范围在创建迭代器那一刻就固定了");
    }

    /**
     * Demo 6：到底了还取，抛异常而不是返回 {@code null}。
     *
     * <p>原项目的 {@code next()} 在没元素时 {@code return null}，
     * 错误会推迟到调用方使用返回值的那一行才爆成
     * {@code NullPointerException}。那时栈顶显示的是
     * "谁用了 null"，而不是"谁多取了一次"。
     */
    private static void demoExhausted() {
        section("Demo 6：到底了还取，抛异常而不是返回 null");
        Workflow empty = new Workflow("空流程");
        Iterator<WorkflowNode> cursor = empty.iterator();

        System.out.println("  空审批流，直接遍历零次：");
        int visited = 0;
        for (WorkflowNode ignored : empty) {
            visited++;
        }
        System.out.println("    for 循环体执行了 " + visited + " 次，没有抛异常");

        System.out.println("  hasNext() = " + cursor.hasNext());
        try {
            cursor.next();
            System.out.println("  没有抛异常，这是 bug");
        } catch (NoSuchElementException e) {
            System.out.println("  next() 抛出：" + e.getClass().getSimpleName());
            System.out.println("  消息：" + e.getMessage());
        }

        System.out.println();
        System.out.println("  对照原项目：next() 到底返回 null，");
        System.out.println("  异常会推迟到 node.name() 那一行才爆成 NullPointerException，");
        System.out.println("  栈顶指向「谁用了 null」，而不是「谁多取了一次」");
    }

    /**
     * 收尾：六段 Demo 与原项目六个问题的对应关系。
     */
    private static void summary() {
        section("六段 Demo 对应原项目的六个问题");
        System.out.println("  1. 增强 for + 两个独立游标   —— 原项目没实现 Iterable，只能手写 while 加强制类型转换");
        System.out.println("  2. 同一棵树两种遍历顺序       —— 原项目把遍历写死在数组下标里，只有一种走法");
        System.out.println("  3. 遍历算法跟着 Iterable 走   —— 原项目的遍历只能用于 Workflow 自己");
        System.out.println("  4. 迭代器可以再包装           —— 原项目想加筛选只能改容器或复制一份遍历代码");
        System.out.println("  5. 遍历中改结构立刻报错       —— 原项目没有 modCount，行为未定义");
        System.out.println("  6. 取到底抛 NoSuchElementException —— 原项目返回 null，错误延后爆发");
    }

    // ------------------------------------------------------------------
    // 建树
    // ------------------------------------------------------------------

    /**
     * 建一棵真实的差旅报销审批流。
     *
     * <pre>
     *   顺序块「报销主流程」
     *     ├─ 开始节点「提交报销单」
     *     └─ 分支「金额是否超过 1000」条件：amount &gt; 1000
     *          ├─ 顺序块「总监线」
     *          │    ├─ 审批节点「总监审批」
     *          │    └─ 通知节点「通知财务」
     *          └─ 顺序块「主管线」
     *               ├─ 审批节点「主管审批」
     *               └─ 通知节点「通知申请人」
     * </pre>
     *
     * @return 建好的审批流
     */
    private static Workflow buildFlow() {
        WorkflowNode directorLine = new SequenceNode("总监线", List.of(
                step("总监审批", StepType.APPROVAL),
                step("通知财务", StepType.NOTIFY)
        ));
        WorkflowNode managerLine = new SequenceNode("主管线", List.of(
                step("主管审批", StepType.APPROVAL),
                step("通知申请人", StepType.NOTIFY)
        ));
        WorkflowNode amountGate = new BranchNode(
                "金额是否超过 1000",
                "amount > 1000",
                directorLine,
                managerLine
        );
        WorkflowNode mainLine = new SequenceNode("报销主流程", List.of(
                step("提交报销单", StepType.START),
                amountGate
        ));
        return new Workflow("差旅报销").add(mainLine);
    }

    /**
     * 造一个步骤节点的简写。
     *
     * @param name 节点名
     * @param type 节点类型
     * @return 步骤节点
     */
    private static StepNode step(String name, StepType type) {
        return new StepNode(name, type);
    }

    // ------------------------------------------------------------------
    // 输出工具
    // ------------------------------------------------------------------

    /**
     * 递归打印树形结构。
     *
     * <p>这里刻意用递归而不是迭代器，因为两件事不一样：
     *
     * <ul>
     *   <li>递归适合"把整棵树一次画完"，它不需要暂停、不需要游标；</li>
     *   <li>迭代器适合"一个一个处理"，可以随时停下、可以多个同时走。</li>
     * </ul>
     *
     * <p>所以本项目<b>没有</b>用迭代器替换掉这种递归 ——
     * 该用递归的地方用递归，才是对模式最诚实的态度。
     *
     * @param node   当前节点
     * @param prefix 当前节点之前的缩进（含竖线）
     * @param last   当前节点是否是其父节点的最后一个孩子
     * @param root   是否是根节点
     */
    private static void printTree(WorkflowNode node, String prefix, boolean last, boolean root) {
        String connector = root ? "" : (last ? "└─ " : "├─ ");
        System.out.println("    " + prefix + connector + node);

        List<WorkflowNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            boolean childLast = i == children.size() - 1;
            String childPrefix = root ? "" : prefix + (last ? "   " : "│  ");
            printTree(children.get(i), childPrefix, childLast, false);
        }
    }

    /**
     * 按迭代器顺序带序号打印，走完为止。
     *
     * @param iterator 任意来源的节点迭代器
     */
    private static void printWithIndex(Iterator<WorkflowNode> iterator) {
        int index = 0;
        while (iterator.hasNext()) {
            index++;
            System.out.printf("    %d. %s%n", index, iterator.next());
        }
    }

    /**
     * 打印任意可遍历对象里的节点。
     *
     * <p>参数类型是 {@link Iterable} 而不是 {@link Workflow} ——
     * 这个方法因此对"树"和"列表"一视同仁，
     * 因为它只用到"能被遍历"这一个能力。
     *
     * @param title  输出标题
     * @param source 任意可遍历的节点来源
     */
    private static void printAll(String title, Iterable<WorkflowNode> source) {
        System.out.println("  " + title + "：");
        int index = 0;
        for (WorkflowNode node : source) {
            index++;
            System.out.printf("    %d. %s%n", index, node);
        }
        System.out.println("    共 " + index + " 个节点");
    }

    /**
     * 已经打印过的小节数。
     *
     * <p>只用来决定标题前要不要补一个空行：第一段前面不空，
     * 后面每段之间空一行，输出就不会以空行开头。
     */
    private static int printedSections;

    /**
     * 打印一个小节标题。
     *
     * @param title 小节标题
     */
    private static void section(String title) {
        if (printedSections > 0) {
            System.out.println();
        }
        printedSections++;
        System.out.println("[" + title + "]");
    }
}
