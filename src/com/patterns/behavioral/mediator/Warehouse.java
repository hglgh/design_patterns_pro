package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 仓库：唯一能回答"还有没有货"的一方。
 *
 * <p><b>这个类是"同事应该有多薄"的最好例子。</b>
 * 它对外只有一个动作：{@code say(STOCK_SHORTAGE, "EP-2024 无线耳机 缺货 37 件")}。
 * 至于"缺货要不要让财务知道""客户要不要收到一句道歉" —— 它不关心，
 * 也不该关心。仓库系统对接的是 WMS，它连"财务"这个词都不该出现。
 *
 * <p><b>它只收一种消息。</b>中性的路由表里，仓库的收件箱只有归档；
 * 这在业务上也说得通：工单关闭时，仓库要把占用的库存锁定释放掉。
 * 所以下面这段 {@code receive} 里有一个 {@code default}：
 *
 * <pre>
 *   default -&gt; 报警
 * </pre>
 *
 * <p>这个 {@code default} 和客户那边的"故意不写 default"是一对对照：
 *
 * <ul>
 *   <li>客户收到内部消息 = 无所谓，忽略即可；</li>
 *   <li>仓库收到提问 = <b>路由表出错了</b>，必须喊出来，
 *       否则这个 bug 会以"仓库没有任何反应"的形式潜伏很久。</li>
 * </ul>
 *
 * <p>同一个语法（{@code default}），在两种场景下语义完全相反。
 * "该不该写 default"没有统一答案，取决于"漏掉这个分支会造成什么后果"。
 *
 * @author design-patterns-pro
 */
public final class Warehouse implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一个仓库参与者。
     *
     * @param name 仓库名称，不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Warehouse(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("仓库名称不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.WAREHOUSE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }

    /**
     * 仓库收到消息时的反应。
     *
     * <p>正常情况只会收到归档（释放库存锁定）。
     * 收到其他类型说明中枢的规则表写错了，这里会明确打一行报警，
     * 而不是静默吞掉。
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ARCHIVE -> System.out.println("        [" + name + "] 仓库：解除库存锁定，从待办移除。");
            default -> System.out.println("        [" + name + "] 仓库：!! 这是我不该收到的消息类型「"
                    + message.kind().label() + "」，请检查中枢的路由表");
        }
    }
}
