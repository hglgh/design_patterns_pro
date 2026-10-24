package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 主管：升级之后的负责方，也是异常工单的兜底人。
 *
 * <p><b>这个类证明了一件事：中介者的规则一变，参与者的行为也跟着变，但代码不用改。</b>
 *
 * <p>升级之前，客户的提问被路由到客服，主管什么都收不到；
 * 升级之后，中枢把 {@code assignee} 从客服改成主管，
 * <b>同一个客户、同一句 {@code say(QUESTION, ...)}，收件人变成了主管</b>。
 * 主管的代码里本来就写着"收到提问就回答"，所以它不需要知道
 * "自己是从什么时候开始接单的"。
 *
 * <p>这就是中介者模式最容易被忽略的收益：<b>路由是运行时的、可变的</b>。
 * 如果把"客户提问发给客服"写死在客户类里（{@code agent.receive(...)}），
 * 升级之后就必须改客户类的代码 —— 而客户类可能已经发布到 App 里了。
 *
 * <p>主管的三个分支对应三种真实场景：
 *
 * <ul>
 *   <li>{@code ESCALATION} —— 刚刚接手，先看上下文；</li>
 *   <li>{@code QUESTION} —— 接手之后客户继续追问；</li>
 *   <li>{@code ARCHIVE} —— 关闭确认。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public final class Supervisor implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一名主管。
     *
     * @param name 主管称呼，不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Supervisor(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("主管称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.SUPERVISOR;
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
     * 主管收到消息时的反应。
     *
     * <p>注意这里<b>没有</b> "我是被升级才来的"这种判断 ——
     * 主管不需要知道升级这件事发生在自己身上，
     * 它只知道"有提问就回答，有归档就确认"。
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ESCALATION -> System.out.println("        [" + name + "] 主管：接手，先翻一遍上下文再回客户。");
            case QUESTION -> System.out.println("        [" + name + "] 主管：客户又追问了，直接答复。");
            case ARCHIVE -> System.out.println("        [" + name + "] 主管：确认关闭，同意归档。");
            default -> System.out.println("        [" + name + "] 主管：（记录在案，无需动作）");
        }
    }
}
