package com.patterns.behavioral.iterator;

import java.util.List;

/**
 * 审批流里的一个节点：要么是具体步骤，要么是分支。
 *
 * <p><b>一、为什么需要这层接口？</b>
 *
 * <p>原项目用 {@code record ProcessNode(String name)} 表达一切，
 * 线和点没有区别，流程被塞进一条平坦数组：
 *
 * <pre>
 *   nodes[0] = 开始
 *   nodes[1] = 审批
 *   nodes[2] = 通知
 *   nodes[3] = 结束
 * </pre>
 *
 * <p>现实里的审批流不是一条直线。金额超过 1000 走总监，
 * 否则走主管 —— 这是树，不是数组。数组装不下分叉，
 * 所以原项目只能表达"一条线"，一遇到条件分支就得把
 * {@code if} 判断硬编码进业务代码，节点列表和真实流程开始脱节。
 *
 * <p><b>二、为什么是 sealed？</b>
 *
 * <p>节点只有三种：没有岔路的步骤（{@link StepNode}）、
 * 有两条岔路的分支（{@link BranchNode}）、
 * 按顺序排开的若干节点（{@link SequenceNode}）。
 * 用 {@code sealed} 把这个封闭的集合写进类型系统，换来两件事：
 *
 * <ul>
 *   <li>写 {@code switch} 时不需要 {@code default}，漏掉一种编译器直接报错；</li>
 *   <li>别人无法在你不知情的情况下塞进第三种节点，
 *       而那个新节点可能压根不支持某种遍历方式。</li>
 * </ul>
 *
 * <p><b>三、为什么把 children() 放在接口上？</b>
 *
 * <p>两种遍历器（深度优先、广度优先）都只需要回答同一个问题：
 * 「从这个节点往下，还有谁？」
 *
 * <p>{@code children()} 就是这个问题的答案。把它放在接口上，
 * 迭代器只依赖这个接口，<b>不认识具体是哪种节点</b>。
 * 于是将来加一种 {@code LoopNode}（循环子流程），
 * 只要实现 {@code children()}，两个迭代器一行都不用改。
 *
 * <p>如果反过来，让迭代器用 {@code switch} 判断节点类型再展开，
 * 那每加一种节点就要改两处迭代器 —— 忘了改就静默少遍历一层，
 * 而且不会报错，只是结果少了几个节点。
 *
 * <p><b>四、遍历的是结构，不是执行路径</b>
 *
 * <p>这一点最容易混淆：分支节点有两条分支，遍历会把
 * <b>两条都走一遍</b>，而运行时只会走其中一条。
 *
 * <p>这不矛盾 —— 遍历回答的是「这个流程里一共有哪些步骤」。
 * 就像 IDE 的「查找所有引用」会把所有分支里的调用点都列出来，
 * 而程序运行时只走一条。执行走哪条分支是流程引擎的事，
 * 不是迭代器的事。
 *
 * @author design-patterns-pro
 * @see StepNode
 * @see BranchNode
 * @see SequenceNode
 */
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {

    /**
     * 节点名，用于展示与日志。
     *
     * @return 节点名，不为 {@code null}，不为空白
     */
    String name();

    /**
     * 直接子节点，按「先左后右」的顺序返回。
     *
     * <p>顺序是有意义的：深度优先遍历压栈时要反过来压，
     * 才能保证左分支先被访问。如果这里返回的顺序不确定，
     * 两种遍历的输出就不可复现，"深度优先"这个承诺也就不成立了。
     *
     * <p>返回的列表必须不可变：迭代器会直接把它当快照使用，
     * 若调用方能改动它，遍历顺序会在遍历途中莫名其妙地变。
     *
     * @return 子节点列表；叶子节点返回空列表，绝不为 {@code null}
     */
    List<WorkflowNode> children();

    /**
     * 是否是叶子节点。
     *
     * <p>放在接口上作为默认方法，是因为它的定义完全由
     * {@link #children()} 推导得出，不需要每个实现各写一遍。
     *
     * <p>这里刻意<b>不</b>定义"是不是审批节点"之类的业务判断 ——
     * 那属于 {@link StepNode#type()}，只有步骤节点才有，
     * 放到接口上会逼着分支节点也回答一个它没有的概念。
     *
     * @return 没有子节点时返回 {@code true}
     */
    default boolean isLeaf() {
        return children().isEmpty();
    }
}
