package com.patterns.behavioral.mediator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 售后工单协同中枢（具体中介者）。
 *
 * <p><b>这个类是整个模式的心脏，它只做四件事：</b>
 *
 * <ol>
 *   <li><b>保管名单</b> —— 谁参加了这张工单（{@link #join(Participant)}）。</li>
 *   <li><b>执行规则</b> —— 一条消息该发给谁（{@link #route(Role, MessageKind)}）。</li>
 *   <li><b>投递并隔离</b> —— 一家炸了不影响其他家（{@link #deliver}）。</li>
 *   <li><b>记录事实</b> —— 投递失败、升级、关闭都要留下痕迹。</li>
 * </ol>
 *
 * <p><b>为什么规则要放在这里，而不是放在参与者里？</b>
 * 看一条真实规则："仓库上报缺货时，客服、财务、客户<b>三方同时</b>要知道。"
 *
 * <ul>
 *   <li>如果把这条规则写在 {@code Warehouse} 里，仓库就必须持有客服、财务、客户的引用 ——
 *       仓库从此依赖三个具体角色，而它本来只需要回答"有没有货"。</li>
 *   <li>如果写在 {@code Client} 里（谁调用谁负责通知），那么"缺货要通知财务"这件事
 *       就散落在每个调用点，第二个调用点忘了写，就是一次线上事故。</li>
 * </ul>
 *
 * 放在中枢里，这条规则<b>只有一个副本</b>，一改全改，而且能被单独测试：
 * 不需要真的连仓库和财务，只断言"这条消息的收件人集合是这三个角色"。
 *
 * <p><b>为什么路由表用 {@code switch} 表达式而不是 if-else 链？</b>
 * 因为 {@code switch} 对枚举是穷尽的：新增一种 {@link MessageKind}，
 * 这个文件立刻编译失败，逼你回答"这种消息该给谁"。
 * 用 if-else 链的话，新类型会静默地掉进 {@code else}，然后<b>没人收到</b> ——
 * 这个坑在迭代器模式文档 §7.2.3 里有真实编译器证据。
 *
 * <p><b>这个类的边界在哪？</b>
 * 它<b>不</b>知道工单的业务细节（退款金额怎么算、库存怎么扣），
 * 也<b>不</b>知道消息内容是什么意思。它只知道"什么角色发的、什么性质的消息、
 * 该给哪些角色"。业务判断留在参与者自己那里，协同规则留在这里 ——
 * 这条分界线一旦模糊，中枢就会长成一个什么都知道的上帝类。
 *
 * @author design-patterns-pro
 */
public final class AfterSalesHub implements CollaborationHub {

    /** 一张工单一个中枢，工单号用于日志。 */
    private final String ticketId;

    /** 按加入顺序保存，{@link #roster()} 与归档广播都用它。 */
    private final List<Participant> participants = new ArrayList<>();

    /** 角色 → 参与者。路由表只认角色，这里是唯一的查表入口。 */
    private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);

    /** 投递失败的痕迹。不抛给调用方，但要留证。 */
    private final List<String> failures = new ArrayList<>();

    /** 当前负责方：客户提问默认找客服，升级后变成主管。 */
    private Role assignee = Role.AGENT;

    /** 成功投递的条数，用于演示"隔离之后其他人照常收到"。 */
    private int delivered;

    /** 工单是否已关闭。关闭后不允许再加入、发言、升级。 */
    private boolean closed;

    /**
     * 创建一张工单的协同中枢。
     *
     * @param ticketId 工单号，不能为空白
     */
    public AfterSalesHub(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("工单号不能为空白");
        }
        this.ticketId = ticketId.strip();
    }

    // ---------------------------------------------------------------- 名单

    /**
     * 让一个参与者加入这张工单的协同。
     *
     * <p><b>为什么"同角色只能有一个"？</b>
     * 因为路由表是按角色写的。如果允许两个客服同时加入，
     * {@code byRole.put(AGENT, ...)} 就会覆盖掉前一个 ——
     * 那个被覆盖的人还在 {@code participants} 名单里，
     * 却再也收不到任何按角色路由的消息。<b>静默失效比报错可怕得多。</b>
     *
     * <p>原项目正好相反：{@code registerUser} 既不查重也不查空，
     * 同一个人注册两次就收到两条消息（探针 2），
     * 注册一个 {@code null} 就在广播时把整条链路炸断（探针 3）。
     *
     * @param participant 要加入的参与者
     * @throws IllegalArgumentException 该角色已经有人了
     * @throws IllegalStateException    工单已经关闭
     */
    @Override
    public void join(Participant participant) {
        Objects.requireNonNull(participant, "参与者不能为 null");
        if (closed) {
            throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能加入新参与者");
        }
        Role role = participant.role();
        Participant existing = byRole.get(role);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "角色「" + role.displayName() + "」已经由「" + existing.name()
                            + "」担任，不能再加入「" + participant.name() + "」");
        }
        byRole.put(role, participant);
        participants.add(participant);
    }

    // ---------------------------------------------------------------- 发言

    /**
     * 发一条消息，由中枢按规则表投递。
     *
     * <p>执行顺序是刻意安排的：
     *
     * <ol>
     *   <li>先校验（空、空白、已关闭）—— 非法请求不该产生任何日志噪音；</li>
     *   <li>再构造 {@link Message}（它自己还会再校验一遍，双保险）；</li>
     *   <li>然后算路由 —— 这一步是纯函数，没有副作用；</li>
     *   <li>最后才打印、投递。</li>
     * </ol>
     *
     * @param from 发送方
     * @param kind 消息性质
     * @param text 正文
     */
    @Override
    public void say(Participant from, MessageKind kind, String text) {
        Objects.requireNonNull(from, "发送方不能为 null");
        Objects.requireNonNull(kind, "消息类型不能为 null");
        if (closed) {
            throw new IllegalStateException(
                    "工单 " + ticketId + " 已关闭，" + from.name() + " 不能再发言");
        }

        Message message = new Message(from.role(), kind, text);
        List<Role> targets = route(from.role(), kind);

        System.out.println("  [" + label(from) + "] " + kind.label() + "：" + message.text());

        if (targets.isEmpty()) {
            System.out.println("      路由未命中：角色「" + from.role().displayName()
                    + "」不能发送「" + kind.label() + "」，消息丢弃");
            return;
        }
        deliver(message, targets);
    }

    /**
     * 路由规则表：给定"谁发的"和"什么性质"，回答"该给哪些角色"。
     *
     * <p>这张表就是"中介者知道交互规则"的全部内容。它有三个特点：
     *
     * <ul>
     *   <li><b>只依赖枚举，不依赖实例。</b>所以换一个人当客服，规则不用改。</li>
     *   <li><b>纯函数。</b>同样的输入永远同样的输出，不读字段、不写日志，
     *       可以脱离控制台单独测试。</li>
     *   <li><b>穷尽。</b>{@code switch} 没有 {@code default}，
     *       新增 {@link MessageKind} 时编译器会在这里报错。</li>
     * </ul>
     *
     * <p>表格版（文档 §5.8 有同一张表的展开说明）：
     *
     * <pre>
     *   提问   客户  → 当前负责方（默认客服，升级后是主管）
     *   答复   客服/主管 → 客户
     *   缺货   仓库  → 客服 + 财务 + 客户
     *   退款   财务  → 客户 + 客服
     *   提示   任意  → 客户
     *   升级   ——    → 只能由 escalate() 产生（主管 + 客服 + 通知器）
     *   归档   ——    → 只能由 close() 产生（广播给其余所有人）
     * </pre>
     *
     * @param from 发送方角色
     * @param kind 消息性质
     * @return 收件方角色列表；空列表表示这条消息不该被放行
     */
    private List<Role> route(Role from, MessageKind kind) {
        return switch (kind) {
            case QUESTION -> from == Role.CUSTOMER
                    ? List.of(assignee)
                    : List.of();
            case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
                    ? List.of(Role.CUSTOMER)
                    : List.of();
            case STOCK_SHORTAGE -> from == Role.WAREHOUSE
                    ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
                    : List.of();
            case REFUND_CONFIRMED -> from == Role.FINANCE
                    ? List.of(Role.CUSTOMER, Role.AGENT)
                    : List.of();
            case NOTICE -> List.of(Role.CUSTOMER);
            case ESCALATION, ARCHIVE -> List.of();
        };
    }

    // ---------------------------------------------------------------- 升级与关闭

    /**
     * 申请升级：交给主管，抄送当前客服与通知器，并把"当前负责方"改成主管。
     *
     * <p><b>为什么升级要改 {@code assignee}？</b>
     * 因为升级的意义就是"这张单子以后归主管管"。
     * 如果只发一条通知而不改负责人，客户下一次提问还是会被路由到客服，
     * 主管只是"围观"了一下 —— 这是很多工单系统里真实存在的 bug。
     *
     * <p><b>为什么还要发给通知器？</b>
     * 因为"系统里的升级"和"主管真的知道"是两件事。主管可能半小时才看一次后台，
     * 所以升级事件要同时推一条短信。这就是中介者存在的意义：
     * <b>一条业务动作需要触发几个不同的下游，这个知识只该写在一处。</b>
     *
     * @param from   发起方
     * @param reason 升级原因
     */
    @Override
    public void escalate(Participant from, String reason) {
        Objects.requireNonNull(from, "发起方不能为 null");
        if (closed) {
            throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能再升级");
        }
        Message message = new Message(from.role(), MessageKind.ESCALATION, reason);

        System.out.println("  [" + label(from) + "] 申请升级：" + message.text());
        System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
        assignee = Role.SUPERVISOR;
        deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
    }

    /**
     * 申请关闭：广播归档通知给其余所有参与者。
     *
     * <p><b>这是本项目唯一一处真正的广播。</b>
     * 广播的代价是"每个参与者都得处理这条消息"，所以它必须有明确的业务理由：
     * 工单结束了，所有人的待办里都该把它划掉。除了这一刻，任何"顺手广播一下"
     * 都会把中介者退化成大喇叭 —— 那就不需要中介者了，观察者模式更合适。
     *
     * @param from 发起方
     */
    @Override
    public void close(Participant from) {
        Objects.requireNonNull(from, "发起方不能为 null");
        if (closed) {
            throw new IllegalStateException("工单 " + ticketId + " 已经关闭过了");
        }
        Message message = new Message(from.role(), MessageKind.ARCHIVE,
                "工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭");

        System.out.println("  [" + label(from) + "] 申请关闭工单 " + ticketId);

        List<Role> targets = new ArrayList<>();
        for (Participant participant : participants) {
            if (participant != from) {
                targets.add(participant.role());
            }
        }
        deliver(message, targets);
        closed = true;
        System.out.println("  工单 " + ticketId + " 状态：已关闭");
    }

    // ---------------------------------------------------------------- 投递

    /**
     * 把一条消息投递给若干角色，并对每一个收件方做异常隔离。
     *
     * <p><b>为什么要 try-catch？</b>
     * 因为收件方是外部世界：短信网关会超时、财务系统会返回 500、
     * 某个同事的客户端可能刚崩。如果没有隔离，第一个异常就会中断整个循环，
     * 排在后面的人<b>什么也收不到</b>，而且调用方会以为"已经通知完了"。
     *
     * <p>原项目的 {@code sendMessage} 就是裸循环：探针 3 里一个 {@code null}
     * 收件人抛出 {@code NullPointerException}，后面所有用户全部漏收。
     *
     * <p><b>为什么失败不抛给调用方？</b>
     * 因为"通知失败"和"业务失败"是两件事。仓库上报缺货成功了，
     * 只是财务的收件箱坏了 —— 这时候让整个上报回滚是错的。
     * 失败要进 {@link #deliveryFailures()} 被监控看到，而不是毁掉主流程。
     *
     * @param message 要投递的消息
     * @param targets 收件方角色列表
     */
    private void deliver(Message message, List<Role> targets) {
        System.out.println("      路由 → " + describeTargets(targets));

        for (Role role : targets) {
            Participant target = byRole.get(role);
            if (target == null) {
                System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
                continue;
            }
            try {
                target.receive(message);
                delivered++;
            } catch (RuntimeException e) {
                String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
                        + "：" + e.getMessage();
                failures.add(record);
                System.out.println("      ! 已隔离：" + record);
            }
        }
    }

    // ---------------------------------------------------------------- 查询

    @Override
    public List<Participant> roster() {
        return List.copyOf(participants);
    }

    @Override
    public Role assignee() {
        return assignee;
    }

    @Override
    public List<String> deliveryFailures() {
        return List.copyOf(failures);
    }

    /**
     * 成功投递的条数。
     *
     * @return 累计成功投递次数
     */
    public int deliveredCount() {
        return delivered;
    }

    /**
     * 工单是否已关闭。
     *
     * @return 已关闭返回 {@code true}
     */
    public boolean closed() {
        return closed;
    }

    /**
     * 工单号。
     *
     * @return 工单号
     */
    public String ticketId() {
        return ticketId;
    }

    // ---------------------------------------------------------------- 日志工具

    /** 参与者的人类可读标签，形如 {@code 客服-小王}。 */
    private static String label(Participant participant) {
        return participant.role().displayName() + "-" + participant.name();
    }

    /** 把角色列表翻译成 {@code 客服-小王、财务-小赵} 这样的字符串。 */
    private String describeTargets(List<Role> targets) {
        List<String> parts = new ArrayList<>();
        for (Role role : targets) {
            Participant participant = byRole.get(role);
            parts.add(participant == null
                    ? role.displayName() + "（未加入）"
                    : label(participant));
        }
        return String.join("、", parts);
    }
}
