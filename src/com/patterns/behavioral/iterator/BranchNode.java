package com.patterns.behavioral.iterator;

import java.util.List;
import java.util.Objects;

/**
 * 分支节点：一个判断，根据条件走两条路径之一。
 *
 * <p><b>一、为什么只有两条分支，不支持 else if？</b>
 *
 * <p>三条以上的分支可以嵌套表达：把第二条分支再接一个
 * {@code BranchNode} 即可。这不是能力缺陷，而是刻意收窄：
 *
 * <ul>
 *   <li>两分支的树是二叉树，遍历逻辑最短，教学时不会被
 *       "第 N 个孩子"的下标问题干扰；</li>
 *   <li>两分支已经能表达任意形状的决策树，多分支只是糖；</li>
 *   <li>{@link #children()} 永远返回固定两个元素，
 *       迭代器不需要考虑"空分支"和"分支数量可变"两种边界。</li>
 * </ul>
 *
 * <p><b>二、为什么 thenBranch / elseBranch 不允许为 null？</b>
 *
 * <p>允许 null 的树在遍历时会变成定时炸弹：
 * 迭代器展开时要把它们压栈，一旦有一个是 null，
 * 要么 NPE 冒到调用方，要么得让迭代器到处写判空。
 *
 * <p>而"这条分支上暂时没有节点"其实是个业务表达问题，
 * 应该用一个显式的占位步骤（比如「无需审批，直接通过」）来表达，
 * 而不是用 {@code null} 让结构本身变得不可信。
 *
 * <p>把这条约束写在构造器里，遍历代码就永远不用判空 ——
 * <b>约束前置到数据入口，比散落在每个消费者里检查更省事。</b>
 *
 * <p><b>三、children() 为什么每次新建列表？</b>
 *
 * <p>record 不允许额外实例字段，没法像 {@link StepNode} 那样
 * 缓存一个共享的不可变列表。
 *
 * <p>这里没有为此放弃 record 改成普通类，是因为代价不对称：
 * 每次 {@code List.of(a, b)} 只是分配一个两元素的小数组，
 * 而 record 带来的不可变性和样板代码消除是长期收益。
 * 若将来出现"一次遍历几万个分支节点"的场景，再把
 * {@code BranchNode} 改成手写类并缓存子节点列表即可，
 * 接口 {@link WorkflowNode#children()} 不用动。
 *
 * <p><b>四、condition 为什么是字符串，不是 Predicate？</b>
 *
 * <p>因为节点描述的是<b>流程长什么样</b>，不是<b>怎么算</b>。
 * 把 {@code Predicate<Order>} 塞进节点，等于让数据结构
 * 依赖业务参数类型，节点就无法被单独打印、序列化、传输。
 * 真正的条件求值属于流程引擎，条件是它的输入。
 *
 * @author design-patterns-pro
 * @see StepNode
 */
public record BranchNode(
        String name,
        String condition,
        WorkflowNode thenBranch,
        WorkflowNode elseBranch
) implements WorkflowNode {

    /**
     * 校验并构造分支节点。
     *
     * <p>四条非空校验加两条空白校验，保证建好的树里
     * 不存在"走到一半发现缺东西"的节点。
     *
     * @throws NullPointerException     任意参数为 {@code null} 时
     * @throws IllegalArgumentException 名字或条件为空白时
     */
    public BranchNode {
        Objects.requireNonNull(name, "分支名不能为 null");
        Objects.requireNonNull(condition, "条件不能为 null");
        Objects.requireNonNull(thenBranch, "条件成立时走的节点不能为 null");
        Objects.requireNonNull(elseBranch, "条件不成立时走的节点不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("分支名不能为空白");
        }
        if (condition.isBlank()) {
            throw new IllegalArgumentException("条件不能为空白");
        }
    }

    /**
     * 返回两条分支，顺序固定为「先成立，后不成立」。
     *
     * <p>这个顺序和代码里 {@code if / else} 的书写顺序一致，
     * 也和一般人脑补流程图的顺序一致。深度优先遍历依赖它
     * 来决定谁先被访问 —— 压栈时反过来压，弹出时就是这个顺序。
     *
     * @return 含且仅含两个元素的不可变列表
     */
    @Override
    public List<WorkflowNode> children() {
        return List.of(thenBranch, elseBranch);
    }

    /**
     * 形如 {@code 分支「金额是否超过 1000」条件：amount > 1000}。
     *
     * <p>把条件原文一起打出来，是因为调试遍历结果时
     * 最常问的问题是"这两个分支到底是怎么分的"。
     *
     * @return 便于阅读的分支描述
     */
    @Override
    public String toString() {
        return "分支「" + name + "」条件：" + condition;
    }
}
