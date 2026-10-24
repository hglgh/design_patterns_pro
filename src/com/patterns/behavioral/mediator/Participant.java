package com.patterns.behavioral.mediator;

/**
 * 协同的参与者（GoF 原书里叫 Colleague，同事）。
 *
 * <p><b>这个接口为什么只有四个方法？</b>
 * 因为"参与者"这件事只需要回答两个问题：
 *
 * <ul>
 *   <li><b>我是谁</b> —— {@link #role()} 和 {@link #name()}</li>
 *   <li><b>我怎么收、往哪发</b> —— {@link #receive(Message)} 和 {@link #hub()}</li>
 * </ul>
 *
 * 剩下三件事（{@code say} / {@code escalate} / {@code closeTicket}）全都有默认实现：
 * 把 {@code this} 交给中枢处理。这是本项目最关键的一处设计：
 *
 * <pre>
 *   default void say(MessageKind kind, String text) {
 *       hub().say(this, kind, text);      // 转发，只有这一句
 *   }
 * </pre>
 *
 * <p><b>为什么不写成抽象类？</b>
 * 抽象类会把"转发"这一行变成三个字段 + 三个方法，每个同事都继承一遍；
 * 而且一个类只能继承一个父类，同事就再也继承不了别的业务基类。
 * 接口 + 默认方法把"能力"而不是"血统"给了参与者：
 * <b>六个同事彼此没有任何继承关系，却共享同一套转发行为。</b>
 *
 * <p><b>为什么 {@link #hub()} 要暴露出去？</b>
 * 因为默认方法需要它。它是参与者<b>唯一</b>的外部依赖 —— 注意这里没有
 * {@code otherParticipants()}、没有 {@code getAgent()}、没有 {@code sendTo(Role)}。
 * 一个参与者如果能看到另一个参与者的引用，中介者就白建了：
 * 耦合会从"中枢到六方"退化成"六方互相到六方"。
 *
 * <p><b>为什么不提供 setHub()？</b>
 * 原项目的 {@code User} 把 {@code protected ChatRoomMediator mediator} 暴露给子类，
 * 于是子类可以在任意时刻把中介者换掉 —— 结果就是"注册在 A 房间、发言走 B 房间"
 * （见文档 §2.3.6 的探针 6）。这里的 {@code hub()} 只读，
 * 绑定关系在构造时就固定，且不可能被绕过。
 *
 * @author design-patterns-pro
 */
public interface Participant {

    /**
     * 我在协同里扮演什么角色。
     *
     * <p>中枢用它做路由决策，所以它必须是稳定的、可比较的，
     * 不能依赖名字字符串。
     *
     * @return 角色枚举，不能为 {@code null}
     */
    Role role();

    /**
     * 我的显示名，用于日志与消息落款。
     *
     * @return 非空白的名字
     */
    String name();

    /**
     * 收到一条消息时怎么反应。
     *
     * <p><b>这是参与者唯一需要自己实现的行为。</b>它只描述"我看到这条消息做了什么"，
     * 不描述"这条消息该转给谁" —— 后者是中枢的职责。
     * 这个边界一旦划清，新增一个角色就只是新增一个 {@code receive()} 实现，
     * 不会牵动任何其他文件。
     *
     * @param message 中枢投递过来的消息，不为 {@code null}
     */
    void receive(Message message);

    /**
     * 我所属的中枢。
     *
     * <p>只读：参与者在构造时被绑定到某个中枢，之后不能换。
     *
     * @return 中枢，不为 {@code null}
     */
    CollaborationHub hub();

    /**
     * 说一句话：交给中枢去路由。
     *
     * @param kind 消息性质，决定路由规则
     * @param text 正文
     */
    default void say(MessageKind kind, String text) {
        hub().say(this, kind, text);
    }

    /**
     * 申请升级：交给中枢去处理。
     *
     * <p>注意这里<b>没有</b> "升级给谁"的参数。谁来接手，由中枢的规则决定 ——
     * 客户不需要知道公司里有"主管"这个角色，更不需要知道主管叫什么。
     *
     * @param reason 升级原因
     */
    default void escalate(String reason) {
        hub().escalate(this, reason);
    }

    /**
     * 申请关闭工单：交给中枢去广播归档。
     */
    default void closeTicket() {
        hub().close(this);
    }
}
