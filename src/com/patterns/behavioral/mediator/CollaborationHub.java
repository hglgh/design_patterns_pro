package com.patterns.behavioral.mediator;

import java.util.List;

/**
 * 协同中枢：参与者之间<b>唯一</b>的通道。
 *
 * <p><b>这个接口里为什么没有一条路由规则？</b>
 * 这是本项目最容易被写错的地方。很多人会把中介者接口写成：
 *
 * <pre>
 *   void sendToAgent(String text);
 *   void sendToFinance(String text);
 *   void broadcast(String text);
 * </pre>
 *
 * 这样写有两个问题：
 *
 * <ol>
 *   <li><b>把"谁该收"的决定权交给了参与者。</b>客户突然能指定"发给财务"，
 *       于是参与者又开始依赖具体角色 —— 耦合只是从"依赖对象"变成"依赖角色"，
 *       并没有消失。</li>
 *   <li><b>接口会随业务规则一起膨胀。</b>每加一条协同规则就要加一个方法，
 *       而规则是最常变的东西。接口该稳定，规则该集中一处。</li>
 * </ol>
 *
 * <p>所以这里只留四个"动作"：
 * {@link #join(Participant)}（谁能参加）、{@link #say(Participant, MessageKind, String)}（发一条消息）、
 * {@link #escalate(Participant, String)}（申请升级）、{@link #close(Participant)}（申请归档）。
 * <b>"这句话该给谁"完全由实现类决定</b>，参与者连表达这个想法的语法都没有。
 *
 * <p><b>为什么参数是 {@code Participant} 而不是 {@code Role}？</b>
 * 因为发出动作的必须是"真实存在的一方"。传 {@code Role} 就等于允许
 * 任何人冒充任何角色发言 —— 这是权限问题，也是可追溯性问题。
 * 中枢需要知道"是谁在说话"，才能在日志、超时、升级里定位到具体的人。
 *
 * <p><b>为什么把只读查询也放进接口？</b>
 * {@link #roster()}、{@link #assignee()}、{@link #deliveryFailures()} 都是给
 * 演示与运维看的。它们不改变状态，放在接口里让"中枢现状"成为契约的一部分，
 * 而不是靠反射或强转去偷看实现类。
 *
 * @author design-patterns-pro
 */
public interface CollaborationHub {

    /**
     * 让一个参与者加入协同。
     *
     * <p>同一角色只能有一个参与者：售后协同里，"两个客服同时负责一张工单"
     * 不是靠"加入两次"表达的，而是靠"升级 / 转派"这类显式动作。
     * 这个约束放在中枢里，就永远不会出现"同一个人收到两条"的问题
     * （原项目允许重复注册，探针 2 就是它的后果）。
     *
     * @param participant 要加入的参与者，不能为 {@code null}
     * @throws IllegalArgumentException 该角色已经有人了
     * @throws IllegalStateException    工单已经关闭
     */
    void join(Participant participant);

    /**
     * 发一条消息，由中枢决定投递给谁。
     *
     * @param from 发送方，不能为 {@code null}
     * @param kind 消息性质
     * @param text 正文
     * @throws IllegalStateException    工单已经关闭
     * @throws IllegalArgumentException 正文为空白
     */
    void say(Participant from, MessageKind kind, String text);

    /**
     * 申请升级：把工单交给主管，并抄送当前客服。
     *
     * @param from   发起方
     * @param reason 升级原因
     */
    void escalate(Participant from, String reason);

    /**
     * 申请关闭工单：向所有参与者广播归档通知。
     *
     * @param from 发起方
     */
    void close(Participant from);

    /**
     * 当前已加入的参与者，按加入顺序。
     *
     * @return 不可变列表，不暴露内部集合
     */
    List<Participant> roster();

    /**
     * 当前负责方。客户提问会被路由到这里。
     *
     * @return 角色枚举
     */
    Role assignee();

    /**
     * 投递失败的记录。一个参与者炸了不应该影响其他人，但必须留下痕迹。
     *
     * @return 不可变列表，每条是一行可读文本
     */
    List<String> deliveryFailures();
}
