package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 一线客服：工单的默认负责方。
 *
 * <p><b>客服是这张工单里唯一"什么都收"的角色</b>：客户的提问直接到这里，
 * 仓库的缺货上报抄送给他，升级时他是被抄送的人，归档时他也要收。
 * 为什么会这样？因为客服在业务上就是<b>枢纽</b> ——
 * 他是那个"出了问题要背"的人，所以每一条相关事实都得让他知道。
 *
 * <p><b>但"知道"不等于"由他转发"。</b>这是两个容易混掉的概念：
 *
 * <ul>
 *   <li>如果仓库上报缺货时，由<b>仓库</b>去调客服、调财务、调客户 ——
 *       那么仓库就变成了半个中介者，它要知道公司里所有相关角色。</li>
 *   <li>如果由<b>客服</b>去转发 —— 那客服就成了上帝对象，
 *       所有协同规则都会堆到它身上，而且客户直接提问时的路径又不经过它。</li>
 * </ul>
 *
 * 本项目的做法：<b>客服只做自己的反应</b>，转发交给中枢。
 * 所以这个类里没有一行"通知财务"的代码，但缺货上报时财务确实收到了 ——
 * 因为规则写在中枢的 {@code route()} 里。
 *
 * @author design-patterns-pro
 */
public final class Agent implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一名客服。
     *
     * @param name 客服称呼，不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Agent(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("客服称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.AGENT;
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
     * 客服收到消息时的反应。
     *
     * <p>七种消息类型里有四种轮到客服，所以这里写了四个分支；
     * 剩下三种（答复、退款确认、提示）本来就不该到这里：
     * 答复是客服自己发出的，退款确认是财务直接给客户的，提示只推给客户。
     * <b>少写一个 {@code default}，等于用代码说清"这条消息不该出现在我这里"</b> ——
     * 如果哪天真的收到了，它会被安静地忽略，而不会多打一行没用的日志。
     *
     * <p>要不要 {@code default}，取决于"收到意外消息时你想知道还是不想知道"：
     * 客户不想知道（省略），仓库想知道（报警），通知器不想外发（安全边界）。
     * 三个同事三种选择，见文档 §4.13。
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case QUESTION -> System.out.println("        [" + name + "] 客服：接单，先看一眼历史记录。");
            case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 客服：收到缺货，这单走退款。");
            case ESCALATION -> System.out.println("        [" + name + "] 客服：抄送已阅，这单不再由我跟。");
            case ARCHIVE -> System.out.println("        [" + name + "] 客服：从我的待办里移除。");
        }
    }
}
