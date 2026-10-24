package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 通知器：把协同里的关键事件外发成站内信 / 短信。
 *
 * <p><b>为什么它也算"参与者"？</b>
 * 因为它具备参与者的全部特征：有身份（{@link Role#NOTIFIER}）、
 * 会收消息、会做反应。至于它背后是人、是部门、还是另一个系统，
 * 对中枢来说没有区别 —— <b>中介者只关心"谁能收什么"</b>。
 *
 * <p>这带来一个实际好处：<b>接第三方渠道不需要改业务代码</b>。
 * 明天公司要换成钉钉机器人，只要写一个 {@code DingTalkNotifier}
 * 实现同样的 {@link Participant}，在 {@code join()} 时替换掉这个类，
 * 中枢和另外五个同事一行都不用改。
 *
 * <p><b>这里的 {@code default} 是一个安全边界。</b>
 * 通知器会把消息发到"公司外面"（客户手机、主管手机），
 * 所以它对<b>什么能外发</b>必须保守：
 *
 * <ul>
 *   <li>升级 → 外发短信给主管（他需要马上知道）；</li>
 *   <li>归档 → 外发站内信给客户（会话结束的通知）；</li>
 *   <li>其余 → <b>一律不发</b>。哪怕某天有人把内部消息误路由到通知器，
 *       它也不会把"财务正在冻结发票"这种话发给客户。</li>
 * </ul>
 *
 * <p>这条 {@code default} 和仓库那条 {@code default}（协议违例报警）不同：
 * 仓库是"想知道出了什么错"，通知器是"就算出错也不敢往外发"。
 * 同样是兜底分支，兜的东西不一样。
 *
 * @author design-patterns-pro
 */
public final class Notifier implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一个通知器。
     *
     * @param name 渠道名称（如"站内信网关"），不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Notifier(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("通知渠道名称不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.NOTIFIER;
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
     * 通知器收到消息时的反应：只外发两类，其余一律不外发。
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「"
                    + message.text() + "」");
            case ARCHIVE -> System.out.println("        [" + name + "] 外发站内信给客户：「"
                    + message.text() + "」");
            default -> System.out.println("        [" + name + "] 不在外发白名单，丢弃（防止内部消息外泄）");
        }
    }
}
