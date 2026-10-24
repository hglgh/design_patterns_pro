package com.patterns.behavioral.iterator;

import java.util.List;
import java.util.Objects;

/**
 * 顺序块：一组按先后顺序排开的节点。
 *
 * <p><b>一、为什么有了分支还需要顺序块？</b>
 *
 * <p>{@link BranchNode} 表达的语义是"<b>择一</b>"：
 * 两条分支只走一条。但现实流程里还有另一半语义 ——
 * "<b>依次</b>"：审批完<b>之后</b>再通知财务。
 *
 * <p>没有顺序块时，"审批 → 通知"这条链无处安放：
 *
 * <pre>
 *   // BranchNode 的 thenBranch 只能放一个节点，
 *   // 放了「总监审批」，「通知财务」就丢了
 *   new BranchNode("金额判断", "amount &gt; 1000", 总监审批, 主管审批)
 * </pre>
 *
 * <p>能想到的补救都很糟：给 {@link StepNode} 加子节点
 * （步骤就不再是叶子，遍历时根本无法判断该不该继续往下走）、
 * 或者干脆把链拆成多个根往 {@link Workflow#add} 里塞
 * （根节点之间没有父子关系，树就退化成了碎片的集合）。
 *
 * <p>加上顺序块之后，三种节点刚好覆盖全部组合需求：
 *
 * <table border="1">
 *   <caption>三种节点的分工</caption>
 *   <tr><th>节点</th><th>含义</th><th>children() 返回</th></tr>
 *   <tr><td>{@link StepNode}</td><td>做一件事</td><td>空列表</td></tr>
 *   <tr><td>{@link BranchNode}</td><td>根据条件二选一</td><td>固定的两个节点</td></tr>
 *   <tr><td>本类</td><td>依次做若干件事</td><td>构造时传入的整个列表</td></tr>
 * </table>
 *
 * <p><b>二、它是 children() 抽象价值的直接证据</b>
 *
 * <p>本项目先写好了 {@link DepthFirstIterator} 和
 * {@link BreadthFirstIterator}，之后才加入这个类。
 * 加入过程中，两个迭代器、{@link Workflow}、
 * {@link TraversalOrder} <b>一行都没有改动</b>。
 *
 * <p>原因很简单：迭代器只调用 {@link WorkflowNode#children()}，
 * 从不判断"这是哪种节点"。顺序块只要回答"我的孩子是谁"，
 * 就自动被两种遍历方式支持。
 *
 * <p>反过来，如果迭代器里写着
 * {@code if (node instanceof BranchNode b) { ... }}
 * 来决定怎么展开，那加入第三种节点时，两处迭代器就会
 * 悄悄少遍历一层 —— 不报错，只是结果不对。
 *
 * <p><b>三、为什么构造时要做 List.copyOf？</b>
 *
 * <p>假设只是 {@code this.steps = steps}，调用方之后一个
 * {@code list.add(...)} 就能改掉已经建好的树。而
 * {@link Workflow#modCount} 完全不知情（它只统计
 * {@link Workflow#add}），迭代器的 fail-fast 也就失效了 ——
 * 遍历结果会随着外部列表的变化而变化。
 *
 * <p>{@code List.copyOf} 做的是<b>真拷贝</b>：
 * 生成一个新的、不可变的列表。外部再怎么改，
 * 树里的结构一动不动。
 *
 * <p><b>四、为什么不允许空的顺序块？</b>
 *
 * <p>空顺序块在遍历时不产出任何节点，但它自己会占一行输出。
 * 于是日志里看起来"这里有个环节"，点进去却是空的 ——
 * 排查时会怀疑是遍历器漏了节点。
 *
 * <p>真需要表达"这一步可以跳过"时，应该放一个明确的步骤
 * （比如「无需审批，自动通过」），让流程在数据上可见，
 * 而不是用空集合让结构变得不可信。
 *
 * @author design-patterns-pro
 * @see StepNode
 * @see BranchNode
 */
public record SequenceNode(String name, List<WorkflowNode> steps) implements WorkflowNode {

    /**
     * 校验并构造顺序块。
     *
     * @throws NullPointerException     名字或步骤列表为 {@code null} 时
     * @throws IllegalArgumentException 名字为空白、步骤列表为空、
     *                                  或其中含有 {@code null} 元素时
     */
    public SequenceNode {
        Objects.requireNonNull(name, "顺序块名字不能为 null");
        Objects.requireNonNull(steps, "步骤列表不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("顺序块名字不能为空白");
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("顺序块至少要有一个节点：" + name);
        }

        // 真拷贝：挡住外部列表后续的修改，也让 null 元素在这里就暴露
        steps = List.copyOf(steps);
    }

    /**
     * 顺序块的孩子就是它的步骤，顺序即构造时给定的顺序。
     *
     * <p>这里返回的 {@code steps} 已经是 {@code List.copyOf}
     * 生成的不可变列表，可以放心直接交给迭代器 ——
     * 与 {@link BranchNode#children()} 每次新建列表不同，
     * 本类的列表只建一次，之后反复复用。
     *
     * @return 不可变的步骤列表，至少含一个元素
     */
    @Override
    public List<WorkflowNode> children() {
        return steps;
    }

    /**
     * 形如 {@code 顺序块「总监线」(2 步)}。
     *
     * <p>把步数打出来，是为了让树形输出一眼能看出
     * "这个块下面还有几个节点"，避免误以为它是叶子。
     *
     * @return 便于阅读的顺序块描述
     */
    @Override
    public String toString() {
        return "顺序块「" + name + "」(" + steps.size() + " 步)";
    }
}
