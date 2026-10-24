package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 客户：售后工单的发起方。
 *
 * <p><b>这个类为什么只有四十行？</b>
 * 因为它<b>只有一件事要做</b>：收到消息时，从"客户"的视角做出反应。
 * 至于"我的问题该转给谁"、"要不要同时通知财务" —— 一句都没有。
 * 客户甚至不知道公司里有"仓库""财务""主管"这些角色。
 *
 * <p>对比原项目的 {@code ChatUser}：它的 {@code send()} 里写着
 * {@code mediator.sendMessage(message, this)}，看起来也在"委托中介"，
 * 但中介的实现是"发给除我以外的所有人" —— 也就是说，
 * <b>聊天室把"发给谁"写死在了中介里，参与者只是被动接受</b>。
 * 一旦业务需要"客服只发给客户""仓库要发给三方"，
 * 那个中介就完全不够用了，只能推倒重写。
 *
 * <p>本项目的客户类<b>不关心</b>这些差异：它调用 {@code say(QUESTION, ...)}，
 * 交给中枢决定。同一个客户对象，在中枢规则改变之后不需要改一行代码。
 *
 * @author design-patterns-pro
 */
public final class Customer implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一个客户参与者。
     *
     * @param name 客户称呼，不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Customer(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("客户称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.CUSTOMER;
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
     * 客户收到消息时的反应。
     *
     * <p>注意这里的 {@code switch} <b>没有 default</b>：
     * 客户只关心"答复""退款""归档"三种，其余内部消息（缺货、升级）对他不可见。
     * 箭头式 {@code switch} <b>语句</b>不穷尽也能编译，这正是我们想要的 ——
     * 客户不该被迫为"内部消息"写一句"我什么都不做"。
     *
     * <p>（如果这里用 {@code switch} <b>表达式</b>，就必须穷尽或写 default，
     * 因为表达式必须产生一个值。这个区别在文档 §4.13 里有编译器证据。）
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ANSWER -> System.out.println("        [" + name + "] 客户：好，那我等结果。");
            case REFUND_CONFIRMED -> System.out.println("        [" + name + "] 客户：钱到账了，问题解决。");
            case ARCHIVE -> System.out.println("        [" + name + "] 客户：会话结束，给这次服务打 5 星。");
        }
    }
}
