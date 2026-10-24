package com.patterns.behavioral.iterator;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 审批流：一棵由 {@link WorkflowNode} 组成的树，可以按多种顺序遍历。
 *
 * <p><b>一、implements Iterable 是本项目最重要的一行</b>
 *
 * <p>原项目的 {@code Workflow} 只有 {@code iterator()} 方法，
 * 没有实现 {@link Iterable}。看起来只差一个接口，实际差的是：
 *
 * <pre>
 *   for (ProcessNode node : workflow) { ... }      // 编译错误
 * </pre>
 *
 * <p>增强 for 循环是<b>编译器语法糖</b>，它只认 {@code Iterable}：
 * 写 {@code for (X x : expr)} 时，编译器要求 {@code expr}
 * 的类型实现了 {@code Iterable<X>}，否则直接报
 * "for-each 不适用于表达式类型"。
 *
 * <p>于是原项目的调用方永远只能手写这段：
 *
 * <pre>
 *   Iterator it = workflow.iterator();
 *   while (it.hasNext()) {
 *       ProcessNode node = (ProcessNode) it.next();   // 还要强转
 *       ...
 *   }
 * </pre>
 *
 * <p>更隐蔽的代价在别的 API 上。{@code Iterable} 提供了一整套
 * 默认方法：{@code forEach}、{@code spliterator}。有了它，
 * {@code StreamSupport.stream(workflow.spliterator(), false)}
 * 就能把审批流变成流，接上 filter / map / collect；
 * 没有它，这些全都得自己重写一遍。
 *
 * <p><b>二、iterator() 每次必须返回新的迭代器</b>
 *
 * <p>这是 {@code Iterable} 的契约，也是它和"直接暴露一个
 * 迭代器字段"的根本区别：
 *
 * <pre>
 *   Iterator&lt;WorkflowNode&gt; a = workflow.iterator();
 *   Iterator&lt;WorkflowNode&gt; b = workflow.iterator();
 *   // a 和 b 各自独立走，互不影响
 * </pre>
 *
 * <p>如果 {@code iterator()} 返回同一个实例，第二次
 * {@code for} 循环会从上一次停下的位置继续 —— 看起来像
 * "第二次遍历什么都没输出"，非常难查。
 *
 * <p><b>三、为什么 iterator() 要有一个带排序参数的重载？</b>
 *
 * <p>迭代器模式的核心承诺是：<b>遍历算法和容器结构分离</b>。
 *
 * <p>容器只提供 {@code children()}（结构），遍历算法住在
 * 迭代器里（顺序）。同一棵树因此可以有任意多种走法，
 * 而树本身一行都不用改 —— 这就是为什么
 * {@link DepthFirstIterator} 和 {@link BreadthFirstIterator}
 * 能共存，而不是二选一。
 *
 * <p><b>四、modCount 为什么放在容器里，不放在迭代器里？</b>
 *
 * <p>"结构被改了几次"是容器的事实，不是某个迭代器的事实。
 * 迭代器只能记下"我开始遍历时的次数"，然后回来对账。
 *
 * <p>把计数器放容器里，还能让多个迭代器共享同一份真相：
 * 两个迭代器同时遍历，任何一个发现数字对不上，都说明
 * 遍历期间有人动过结构 —— 而它们各自看到的数字一定相同。
 *
 * @author design-patterns-pro
 * @see DepthFirstIterator
 * @see BreadthFirstIterator
 * @see FilteringIterator
 */
public final class Workflow implements Iterable<WorkflowNode> {

    /** 审批流名字，只用于展示。 */
    private final String name;

    /**
     * 根节点列表。
     *
     * <p>允许有多个根，是因为现实里一个流程常常由几条
     * 互不相连的链条组成（比如「正常流程」和「打回重走」）。
     * 用 {@code List} 而不是数组，是为了不再有原项目
     * {@code if (size < nodes.length)} 那个容量上限 ——
     * 数组满了静默丢弃节点，是原项目最危险的一个 bug。
     */
    private final List<WorkflowNode> roots = new ArrayList<>();

    /**
     * 结构修改次数。
     *
     * <p>只有真正改变"树长什么样"的操作才加一：
     * {@link #add} 加了一个根节点，算；读操作不算。
     *
     * <p>包级可见，因为它是容器和迭代器之间的内部协议，
     * 不是给调用方用的 API —— 暴露成 {@code public} 只会
     * 引诱别人拿它做业务判断。
     */
    private int modCount;

    /**
     * 创建一个空的审批流。
     *
     * <p>空审批流是合法状态：遍历它零次，不抛异常。
     * 这与 {@code next()} 在空迭代器上抛异常并不矛盾 ——
     * 前者是"没有节点"，后者是"没有下一个却偏要取"。
     *
     * @param name 审批流名字，不能为 {@code null} 或空白
     * @throws NullPointerException     名字为 {@code null} 时
     * @throws IllegalArgumentException 名字为空白时
     */
    public Workflow(String name) {
        Objects.requireNonNull(name, "审批流名字不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("审批流名字不能为空白");
        }
        this.name = name;
    }

    /**
     * 追加一个根节点，返回自身以便链式调用。
     *
     * <p>返回 {@code this} 而不是 {@code void}，是为了让建树代码
     * 能写成一条从头到尾读下来的链：
     *
     * <pre>
     *   new Workflow("报销")
     *       .add(step("开始", StepType.START))
     *       .add(decision);
     * </pre>
     *
     * <p>这是本项目在 API 层面唯一"额外加的东西"，它和迭代器
     * 模式无关，纯粹是为了让 {@code Client} 里的建树代码短一点。
     *
     * @param node 要追加的根节点，不能为 {@code null}
     * @return 当前审批流，便于链式调用
     * @throws NullPointerException 节点为 {@code null} 时
     */
    public Workflow add(WorkflowNode node) {
        Objects.requireNonNull(node, "节点不能为 null");
        roots.add(node);
        modCount++;
        return this;
    }

    /**
     * 一次性追加多个根节点。
     *
     * @param nodes 要追加的节点，不能为 {@code null}
     * @return 当前审批流，便于链式调用
     * @throws NullPointerException 列表或其中任一节点为 {@code null} 时
     */
    public Workflow addAll(List<WorkflowNode> nodes) {
        Objects.requireNonNull(nodes, "节点列表不能为 null");
        nodes.forEach(this::add);
        return this;
    }

    /**
     * 根节点快照。
     *
     * <p>返回 {@code List.copyOf} 而不是 {@code roots} 本身：
     * 迭代器在构造时会把根节点压进自己的栈，此后即使
     * {@link #add} 追加了新根节点，也不会出现在这次遍历里 ——
     * 这正是"遍历范围在创建迭代器那一刻固定下来"的含义。
     *
     * <p>若直接把内部列表交出去，调用方一个 {@code clear()}
     * 就能把迭代器脚下抽空，而 {@code modCount} 却毫不知情。
     *
     * @return 不可变的根节点列表，不为 {@code null}
     */
    public List<WorkflowNode> roots() {
        return List.copyOf(roots);
    }

    /**
     * 根节点数量。
     *
     * <p>注意它<b>不是</b>节点总数：整棵树的节点数要遍历才知道，
     * 因为分支下面的节点只有走过 {@code children()} 才数得清。
     * 方法名刻意用 {@code rootCount} 而不是 {@code size}，
     * 就是为了不让人误以为这是个平坦集合。
     *
     * @return 根节点数量
     */
    public int rootCount() {
        return roots.size();
    }

    /**
     * 审批流名字。
     *
     * @return 名字，不为 {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * 返回默认顺序（深度优先）的迭代器。
     *
     * <p>这是 {@link Iterable} 要求实现的方法，也是增强 for
     * 循环背后真正调用的方法。<b>每次调用都返回全新实例</b>，
     * 两次 {@code for} 循环之间不会有任何残留状态。
     *
     * <p>默认选深度优先，是因为它和"顺着流程读下去"的
     * 直觉一致：先看完条件成立那条链的整条路径。
     *
     * @return 新的深度优先迭代器
     */
    @Override
    public Iterator<WorkflowNode> iterator() {
        return iterator(TraversalOrder.DEPTH_FIRST);
    }

    /**
     * 按指定顺序返回迭代器。
     *
     * <p>这里是整个模式唯一知道"有几种遍历方式"的地方。
     * 加一种新顺序，只需要在 {@link TraversalOrder} 里加一个
     * 枚举值，再在这里加一个 {@code case} —— 编译器的
     * 穷尽性检查会提醒你漏了哪个分支。
     *
     * @param order 遍历顺序，不能为 {@code null}
     * @return 新的迭代器
     * @throws NullPointerException 顺序为 {@code null} 时
     */
    public Iterator<WorkflowNode> iterator(TraversalOrder order) {
        Objects.requireNonNull(order, "遍历顺序不能为 null");
        return switch (order) {
            case DEPTH_FIRST -> new DepthFirstIterator(this);
            case BREADTH_FIRST -> new BreadthFirstIterator(this);
        };
    }

    /**
     * 当前的修改次数，供迭代器记账用。
     *
     * @return 结构修改次数
     */
    int modCount() {
        return modCount;
    }

    /**
     * 校验结构是否在遍历期间被改过。
     *
     * <p>把这段检查放在容器里而不是每个迭代器里，是因为
     * 两个迭代器的检查逻辑必须完全一致 —— 否则会出现
     * "深度优先会抛异常、广度优先不会"这种更难查的分裂行为。
     *
     * @param expectedModCount 迭代器创建时记下的修改次数
     * @throws ConcurrentModificationException 结构已被修改时
     */
    void checkForComodification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                    "遍历期间审批流被修改：迭代器创建时修改了 "
                            + expectedModCount + " 次，现在是 " + modCount + " 次");
        }
    }

    /**
     * 形如 {@code 审批流「差旅报销」[2 个根节点]}。
     *
     * @return 便于阅读的审批流描述
     */
    @Override
    public String toString() {
        return "审批流「" + name + "」[" + roots.size() + " 个根节点]";
    }
}
