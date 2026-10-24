package com.patterns.behavioral.iterator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 广度优先迭代器：一层一层往外扩。
 *
 * <p><b>一、和深度优先的差别只有一个数据结构</b>
 *
 * <p>把两个类并排看，会发现它们的骨架一模一样：
 * 构造时装入根节点、{@code hasNext()} 判空、
 * {@code next()} 取出一个再把它的孩子装进去。
 *
 * <p>唯一的差别是：
 *
 * <table border="1">
 *   <caption>两种遍历的全部差异</caption>
 *   <tr><th>环节</th><th>{@link DepthFirstIterator}</th><th>本类</th></tr>
 *   <tr><td>数据结构</td><td>{@code Deque} 当栈（后进先出）</td>
 *       <td>{@code Deque} 当队列（先进先出）</td></tr>
 *   <tr><td>取出</td><td>{@code pop()}（取栈顶）</td>
 *       <td>{@code pollFirst()}（取队首）</td></tr>
 *   <tr><td>装入孩子</td><td>反序 {@code push()}（后压先出）</td>
 *       <td>正序 {@code addLast()}（先加先出）</td></tr>
 * </table>
 *
 * <p>这正好是迭代器模式最值得看的地方：<b>换一种遍历方式，
 * 不需要改容器一个字符。</b> {@link Workflow} 完全不知道
 * 有这两个类的存在（它只知道 {@link TraversalOrder} 有两个值），
 * 树的结构 {@link WorkflowNode#children()} 也毫不知情。
 *
 * <p>如果遍历写在容器里（比如 {@code Workflow.depthFirstPrint()}），
 * 加第二种顺序就要改容器类；加第五种就要改五遍，
 * 而且每改一次都要重新回归测试所有已有的遍历。
 *
 * <p><b>二、为什么用同一个 ArrayDeque 双端接口？</b>
 *
 * <p>{@code Deque} 同时支持两头进出，所以"当栈用"和
 * "当队列用"是同一个类型。这带来一个副作用：<b>写错了不会报错</b>。
 *
 * <p>比如本类里把 {@code pollFirst()} 写成 {@code pollLast()}，
 * 代码照样编译、照样跑完、不抛任何异常 —— 只是遍历顺序
 * 悄悄变成了"倒着的广度优先"。这类错误只能靠单元测试
 * 逐字比对输出来发现，编译器帮不上忙。
 *
 * <p>所以下面两处都写了显式的注释说明为什么是这一头。
 *
 * <p><b>三、fail-fast 与深度优先共用同一份检查</b>
 *
 * <p>{@link Workflow#checkForComodification(int)} 是包级方法，
 * 只给两个迭代器用。让两边共用同一份实现，是为了避免
 * "深度优先会抛、广度优先不抛"这种分裂行为 ——
 * 同一棵树的两种走法，对"结构被改了"这件事必须有同一个态度。
 *
 * @author design-patterns-pro
 * @see DepthFirstIterator
 */
public final class BreadthFirstIterator implements Iterator<WorkflowNode> {

    /**
     * 待访问节点队列。
     *
     * <p>这里用它当<b>队列</b>：从头部取、往尾部加。
     * 队首永远是最早进来（层级最靠上）的那个节点，
     * 于是所有第 N 层节点一定排在第 N+1 层之前被取出。
     */
    private final Deque<WorkflowNode> queue = new ArrayDeque<>();

    /** 所属审批流，用来做 fail-fast 对账。 */
    private final Workflow workflow;

    /** 创建迭代器那一刻的修改次数。 */
    private final int expectedModCount;

    /**
     * 创建一个广度优先迭代器。
     *
     * <p>根节点<b>正序</b>入队：先入队的会先出队，
     * 所以第一个根节点就是第一个被访问的节点。
     * 这一点和深度优先恰好相反（那边要反序压栈），
     * 因为栈和队列的取出方向不同。
     *
     * @param workflow 要遍历的审批流，不能为 {@code null}
     * @throws NullPointerException 审批流为 {@code null} 时
     */
    public BreadthFirstIterator(Workflow workflow) {
        this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
        this.expectedModCount = workflow.modCount();

        List<WorkflowNode> roots = workflow.roots();
        for (WorkflowNode root : roots) {
            // 正序入队：队列先进先出，第一个根节点第一个被访问
            queue.addLast(root);
        }
    }

    /**
     * 是否还有未访问的节点。
     *
     * <p>只查队列，不改队列，无副作用。
     *
     * @return 队列非空时返回 {@code true}
     */
    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    /**
     * 取下并返回下一个节点，同时把它的孩子入队。
     *
     * <p>孩子<b>正序</b>加在队尾：这样同一层的节点会按
     * "先左后右"的顺序排在一起，输出才符合"一层一层展开"。
     *
     * @return 下一个节点，不为 {@code null}
     * @throws ConcurrentModificationException 遍历期间审批流被修改时
     * @throws NoSuchElementException          已经没有节点时
     */
    @Override
    public WorkflowNode next() {
        workflow.checkForComodification(expectedModCount);

        if (queue.isEmpty()) {
            throw new NoSuchElementException("广度优先遍历已经到底，没有下一个节点");
        }

        WorkflowNode node = queue.pollFirst();

        // 正序入队：先左后右，同一层按声明顺序展开
        queue.addAll(node.children());

        return node;
    }
}
