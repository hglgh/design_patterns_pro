package com.patterns.behavioral.iterator;

/**
 * 流程节点的四种类型。
 *
 * <p><b>为什么需要这个枚举？</b>
 * 原项目的 {@code ProcessNode} 只有一个 {@code name} 字段：
 *
 * <pre>
 *   record ProcessNode(String name) { }
 * </pre>
 *
 * 于是"这是一个审批节点还是通知节点"，只能靠名字字符串判断：
 *
 * <pre>
 *   if (node.name().contains("审批")) { ... }      // 改个名字就失效
 * </pre>
 *
 * 一旦运营把「总监审批」改成「总监确认」，这段判断就静默失效了。
 *
 * <p>类型是<b>数据的属性</b>，不该由名字推断。这和状态模式里
 * "能用数据描述的差异不要用类"、观察者模式里"实时标记做成字段"
 * 是同一条原则：<b>把规则写成数据，让编译器帮你检查。</b>
 *
 * <p>本项目的 {@code Client} 第 4 段 Demo 就靠它做过滤：
 * "只遍历所有审批节点"——这在前端是"只看我需要处理的步骤"，
 * 在审计里是"列出所有需要人签字的环节"。
 *
 * @author design-patterns-pro
 */
public enum StepType {

    /** 开始节点：流程入口，没有实际动作。 */
    START("开始"),

    /** 审批节点：需要人签字确认，是流程里最需要被单独筛出来的一类。 */
    APPROVAL("审批"),

    /** 通知节点：只发送消息，不阻塞流程。 */
    NOTIFY("通知"),

    /** 结束节点：流程出口。 */
    END("结束");

    private final String label;

    StepType(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于日志与界面文案。
     *
     * @return 类型的中文名
     */
    public String label() {
        return label;
    }
}
