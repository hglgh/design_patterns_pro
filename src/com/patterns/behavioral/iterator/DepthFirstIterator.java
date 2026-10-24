package com.patterns.behavioral.iterator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 深度优先迭代器：一条路走到底，再回头走另一条。
 *
 * <p><b>一、为什么用显式栈，不用递归？</b>
 *
 * <p>深度优先最直观的实现是递归：
 *
 * <pre>
 *   void visit(WorkflowNode node) {
 *       System.out.println(node);
 *       for (WorkflowNode child : node.children()) {
 *           visit(child);
 *       }
 *   }
 * </pre>
 *
 * <p>这段代码更短，但它做不到迭代器要做的三件事：
 *
 * <ul>
 *   <li><b>不能暂停。</b>递归一旦开始就必须走完，
 *       想"只看前 3 个节点"没有任何办法 —— 除非在递归里
 *       撒一个 {@code return false} 的传播标志，把代码搅乱；</li>
 *   <li><b>不能同时开两个游标。</b>递归函数没有"进度"这个概念，
 *       第二次调用只能从头再来；</li>
 *   <li><b>深度受方法栈限制。</b>几万层的流程树会直接
 *       {@code StackOverflowError}，而这个错误在数据量小的时候
 *       完全测不出来，上线遇到真实大流程才炸。</li>
 * </ul>
 *
 * <p>显式栈把"走到哪了"从方法调用栈搬到了堆上的
 * {@code ArrayDeque} 里 —— 于是进度变成了对象状态，
 * 可以暂停、可以复制、可以有任意多个，深度也只受堆大小限制。
 *
 * <p><b>二、为什么压栈要反序？</b>
 *
 * <p>栈是后进先出。要让 {@code children().get(0)}（成立分支）
 * 先被弹出，它就必须<b>最后</b>压进去：
 *
 * <pre>
 *   children = [then, else]
 *   push(else)     // 先压，后被弹
 *   push(then)     // 后压，先被弹
 *   pop()  → then
 * </pre>
 *
 * <p>如果按正序压，输出会变成"不成立分支在前" —— 程序不会报错，
 * 结果只是顺序反了，而且看起来仍然像个合法的深度优先遍历
 * （只是左右颠倒）。这是本模式里最容易写错、最难发现的一处：
 * <b>反序压栈是唯一保证输出顺序与声明一致的地方。</b>
 *
 * <p><b>三、为什么 hasNext() 不检查 modCount？</b>
 *
 * <p>这是刻意与 JDK 保持一致。{@code ArrayList.Itr} 的
 * {@code hasNext()} 只做 {@code cursor != size}，
 * 真正的检查在 {@code next()} 里。
 *
 * <p>代价是真实存在的：遍历途中删掉倒数第二个元素，
 * {@code hasNext()} 会直接返回 {@code false}，不抛异常，
 * 剩下的元素被静默跳过。本项目选择保留这个行为，是因为
 * "一个 <b>布尔查询</b>方法却抛异常"更违反直觉；
 * 要防住静默漏发，靠的是 {@link #next()} 里的检查，
 * 而不是让 {@code hasNext()} 变成会爆炸的查询。
 *
 * <p><b>四、为什么 next() 到底要抛异常，而不是返回 null？</b>
 *
 * <p>原项目的实现是 {@code return null}：
 *
 * <pre>
 *   public Object next() {
 *       if (hasNext()) { return nodes[index++]; }
 *       return null;              // 到底了
 *   }
 * </pre>
 *
 * <p>这看起来"温和"，实际把错误藏起来了：
 *
 * <pre>
 *   while (it.hasNext()) { ... }        // 正常，永远不会拿到 null
 *   it.next();                          // 不小心多调一次 → null
 *   node.name()                         // → NullPointerException，
 *                                       //   栈里显示的是这一行，
 *                                       //   而不是"多调了 next()"那一行
 * </pre>
 *
 * <p>{@link NoSuchElementException} 的价值不在于"让程序崩"，
 * 而在于<b>把崩溃点定在真正的错处</b>：调用方多取了一次，
 * 异常就抛在多取的那一行，异常类型本身也说明了"没有元素"。
 *
 * <p>这也是 {@link Iterator} 接口的契约：JDK 文档明确写着
 * {@code next()} 在无更多元素时"throws NoSuchElementException"。
 *
 * @author design-patterns-pro
 * @see BreadthFirstIterator
 */
public final class DepthFirstIterator implements Iterator<WorkflowNode> {

    /**
     * 待访问节点栈。
     *
     * <p>用 {@code ArrayDeque} 而不是 {@code Stack}：
     * {@code Stack} 是 {@code Vector} 的子类，所有方法都带
     * 同步锁，单线程下纯属负担；{@code ArrayDeque} 明确
     * 声明为"用作栈时比 Stack 快"。
     *
     * <p>它同样不允许 {@code null} 元素 —— 而我们的节点
     * 在构造时已经保证非 {@code null}，两条约束正好对上。
     */
    private final Deque<WorkflowNode> stack = new ArrayDeque<>();

    /**
     * 所属审批流，用来做 fail-fast 对账。
     */
    private final Workflow workflow;

    /**
     * 创建迭代器那一刻的修改次数。
     *
     * <p>它是<b>快照</b>，不会跟着 {@code workflow.modCount}
     * 一起变 —— 变了就永远对得上，检查也就形同虚设。
     */
    private final int expectedModCount;

    /**
     * 创建一个深度优先迭代器。
     *
     * <p>构造时就把根节点<b>反序</b>压栈，之后每次
     * {@link #next()} 只负责"弹一个、再把它的孩子反序压回去"。
     *
     * <p>这样做的结果是 {@link #hasNext()} 完全没有副作用：
     * 它只回答"栈空不空"，调一百次也不会改变遍历进度。
     * 如果在 {@code hasNext()} 里做展开，重复调用就会
     * 重复展开同一批孩子。
     *
     * @param workflow 要遍历的审批流，不能为 {@code null}
     * @throws NullPointerException 审批流为 {@code null} 时
     */
    public DepthFirstIterator(Workflow workflow) {
        this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
        this.expectedModCount = workflow.modCount();

        List<WorkflowNode> roots = workflow.roots();
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
    }

    /**
     * 是否还有未访问的节点。
     *
     * <p>只查栈，不改栈：<b>无副作用</b>。
     *
     * <p>注意它不检查 {@code modCount}，理由见类注释第三节。
     *
     * @return 栈非空时返回 {@code true}
     */
    @Override
    public boolean hasNext() {
        return !stack.isEmpty();
    }

    /**
     * 取下并返回下一个节点，同时把它的孩子压栈。
     *
     * <p>三步顺序不能变：
     *
     * <ol>
     *   <li>先对账（结构是否被改过）；</li>
     *   <li>再判空（是否真的取得到）；</li>
     *   <li>最后弹栈并展开孩子。</li>
     * </ol>
     *
     * <p>如果先弹栈再对账，异常抛出时栈已经被改动，
     * 调用方 catch 之后继续用这个迭代器，就会漏掉那个节点。
     *
     * @return 下一个节点，不为 {@code null}
     * @throws ConcurrentModificationException 遍历期间审批流被修改时
     * @throws NoSuchElementException          已经没有节点时
     */
    @Override
    public WorkflowNode next() {
        workflow.checkForComodification(expectedModCount);

        if (stack.isEmpty()) {
            throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
        }

        WorkflowNode node = stack.pop();

        List<WorkflowNode> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(children.get(i));
        }

        return node;
    }
}
