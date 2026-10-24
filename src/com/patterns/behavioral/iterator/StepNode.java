package com.patterns.behavioral.iterator;

import java.util.List;
import java.util.Objects;

/**
 * 一个具体步骤：走到就执行，没有岔路。
 *
 * <p><b>一、为什么 type 必须是字段，不能靠名字推断？</b>
 *
 * <p>答案在 {@link StepType} 里已经写过了，这里补一个具体后果：
 *
 * <pre>
 *   // 原项目唯一能写出的过滤
 *   if (node.name().contains("审批")) { ... }
 * </pre>
 *
 * <p>这段代码在「总监审批」上能跑通，在「主管审批」上也能跑通，
 * 于是它顺利通过测试。等运营把节点名改成「总监确认」，
 * 测试还是绿的，但这段过滤已经再也挑不出这个节点了 ——
 * 名字是给人看的文案，类型是给机器判断的数据，
 * 两者的修改频率和修改人完全不同。
 *
 * <p><b>二、为什么 children() 返回共享的空列表？</b>
 *
 * <p>步骤节点是叶子，永远没有子节点。
 *
 * <p>如果写成 {@code return List.of();}，每次调用都会新建一个
 * 空列表对象。遍历一个 200 节点的流程，两个迭代器加起来要调用
 * 两百多次 {@code children()}，纯属浪费。
 *
 * <p>空列表是不可变的，全局共享一个完全安全 ——
 * 这也是 JDK 里 {@code List.of()} 本身就返回共享空实例的原因。
 * 这里显式写出来，是为了让"叶子节点没有子节点"从"每次现算"
 * 变成"这个类的事实"。
 *
 * <p><b>三、为什么用 record？</b>
 *
 * <p>步骤节点没有任何行为，只有名字和类型两个值。
 * 用 record 省掉 getter / equals / hashCode / toString 四份样板，
 * 而且天然不可变：树一旦建好，遍历过程中不会被谁偷偷改掉，
 * 迭代器的 fail-fast 才有意义 —— 如果节点能改，
 * "遍历时结构被修改"就根本检测不出来。
 *
 * @author design-patterns-pro
 * @see StepType
 * @see BranchNode
 */
public record StepNode(String name, StepType type) implements WorkflowNode {

    /**
     * 所有叶子节点共享的空子节点列表。
     *
     * <p>不可变，可以安全地被任意多个迭代器同时读取。
     */
    private static final List<WorkflowNode> NO_CHILDREN = List.of();

    /**
     * 校验并构造步骤节点。
     *
     * <p>三个检查都放在紧凑构造器里，而不是留给调用方自觉：
     * 一个没有名字或没有类型的步骤节点，打印出来是
     * {@code null节点「null」}，排查起来要一路回溯到建树的地方。
     *
     * @throws NullPointerException     名字或类型为 {@code null} 时
     * @throws IllegalArgumentException 名字为空白时
     */
    public StepNode {
        Objects.requireNonNull(name, "节点名不能为 null");
        Objects.requireNonNull(type, "节点类型不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("节点名不能为空白");
        }
    }

    /**
     * 步骤节点是叶子，永远没有子节点。
     *
     * <p>这正是它和 {@link BranchNode} 在遍历视角下的唯一区别：
     * 迭代器拿到这个节点时，不会往待访问集合里加任何东西。
     *
     * @return 共享的不可变空列表
     */
    @Override
    public List<WorkflowNode> children() {
        return NO_CHILDREN;
    }

    /**
     * 形如 {@code 审批节点「总监审批」}。
     *
     * <p>把类型的中文名放在前面，是为了让遍历输出一眼能看出
     * "这一行是哪种节点"，而不是只看到一串岗位名。
     *
     * @return 便于阅读的节点描述
     */
    @Override
    public String toString() {
        return type.label() + "节点「" + name + "」";
    }
}
