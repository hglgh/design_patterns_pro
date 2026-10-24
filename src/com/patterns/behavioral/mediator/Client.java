package com.patterns.behavioral.mediator;

import java.util.List;

/**
 * 演示入口：六段 Demo，每段对应原项目的一个真实问题。
 *
 * <p><b>读这段代码的顺序建议：</b>
 * 先看 {@link #build()} 看清"六个同事 + 一个中枢"是怎么搭起来的，
 * 再看每段 Demo 里那<b>一两行业务调用</b> ——
 * 你会发现所有 Demo 加起来只用到三个动作：
 *
 * <pre>
 *   customer.say(...)       / agent.say(...) / warehouse.say(...)
 *   customer.escalate(...)
 *   agent.closeTicket()
 * </pre>
 *
 * <p>没有一行"通知财务"、没有一行"发给主管"。
 * <b>六段 Demo 覆盖的所有跨角色协同，全部写在中枢的规则表里。</b>
 * 这就是中介者模式的核心：把"谁和谁说话"从参与者身上拿走。
 *
 * @author design-patterns-pro
 */
public final class Client {

    /** section() 只在第一段之前不打印空行，避免输出首行是空行。 */
    private static int printedSections;

    private Client() {
    }

    /**
     * 程序入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        demoOneRoutingToOne();
        demoTwoStockShortage();
        demoThreeRefund();
        demoFourEscalation();
        demoFiveClose();
        demoSixIsolation();
        summary();
    }

    // ------------------------------------------------------------------ 场景搭建

    /**
     * 一张工单的全套参与方。
     *
     * <p>用一个 record 打包，是为了让每段 Demo 里能写 {@code team.customer()}
     * 而不必声明六个局部变量 —— 演示代码越短，"中介者到底省了什么"越明显。
     *
     * @param hub        协同中枢
     * @param customer   客户
     * @param agent      客服
     * @param warehouse  仓库
     * @param finance    财务
     * @param supervisor 主管
     * @param notifier   通知器
     */
    private record Team(AfterSalesHub hub, Customer customer, Agent agent,
                        Warehouse warehouse, Finance finance, Supervisor supervisor,
                        Notifier notifier) {
    }

    /**
     * 建一套完整的协同：一个中枢 + 六个参与者。
     *
     * <p>注意这里<b>没有</b>任何"谁认识谁"的代码。六个参与者都只认识 {@code hub}，
     * 而 {@code hub} 认识所有人 —— 这是一个星形结构，不是网状结构。
     *
     * @return 一整套参与者
     */
    private static Team build() {
        AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
        Customer customer = new Customer("李女士", hub);
        Agent agent = new Agent("小王", hub);
        Warehouse warehouse = new Warehouse("华东仓", hub);
        Finance finance = new Finance("小赵", hub);
        Supervisor supervisor = new Supervisor("张主管", hub);
        Notifier notifier = new Notifier("站内信网关", hub);

        hub.join(customer);
        hub.join(agent);
        hub.join(warehouse);
        hub.join(finance);
        hub.join(supervisor);
        hub.join(notifier);
        return new Team(hub, customer, agent, warehouse, finance, supervisor, notifier);
    }

    // ------------------------------------------------------------------ Demo 1

    /**
     * Demo 1：客户说一句话，只该到一个人手里。
     *
     * <p>对照原项目：{@code ChatRoom.sendMessage} 是广播，
     * "客户的一句话"会同时打到仓库、财务、主管的手机上 —— 这在售后场景里是事故。
     */
    private static void demoOneRoutingToOne() {
        section("Demo 1：客户的一句话，只该到一个人手里");
        Team team = build();
        printRoster(team.hub());

        team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");

        System.out.println("  当前负责方：" + team.hub().assignee().displayName());
        System.out.println("  只有 1 个人收到。原项目的 sendMessage 会发给除发送者外的所有人（5 个）");
    }

    // ------------------------------------------------------------------ Demo 2

    /**
     * Demo 2：仓库一句"缺货"，中枢同时通知三方。
     *
     * <p>对照原项目：它没有"三方协同"这个概念，
     * 只能在 {@code Warehouse} 里硬写 {@code agent.receive(...); finance.receive(...); customer.receive(...);}
     * —— 仓库从此依赖三个具体角色。
     */
    private static void demoTwoStockShortage() {
        section("Demo 2：仓库一句「缺货」，中枢同时通知三方");
        Team team = build();

        team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
        team.agent().say(MessageKind.ANSWER, "我查一下库存，稍等两分钟。");
        team.warehouse().say(MessageKind.STOCK_SHORTAGE, "EP-2024 无线耳机，华东仓缺货 37 件");

        System.out.println("  这条消息由仓库发出，但客服、财务、客户都收到了");
        System.out.println("  三个收件人不是仓库决定的，是中枢的 route() 决定的");
        System.out.println("  通知器没有收到：它只外发升级与归档，缺货这种内部事实不外发");
    }

    // ------------------------------------------------------------------ Demo 3

    /**
     * Demo 3：财务确认退款，只告知该知道的人。
     *
     * <p>对照原项目：没有"该知道"的概念 —— 要么全员广播，要么单独私聊，
     * 而"私聊"需要发言者持有对方的引用，又回到耦合。
     */
    private static void demoThreeRefund() {
        section("Demo 3：同一张工单，换个角色发言就换一批收件人");
        Team team = build();

        team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
        team.warehouse().say(MessageKind.STOCK_SHORTAGE, "EP-2024 无线耳机，华东仓缺货 37 件");
        team.finance().say(MessageKind.REFUND_CONFIRMED, "已退 299 元，1-3 个工作日到账");

        System.out.println("  退款确认只到 客户 + 客服。仓库收不到这条 —— 它不需要知道钱的事");
    }

    // ------------------------------------------------------------------ Demo 4

    /**
     * Demo 4：升级之后，同一句 {@code say(QUESTION, ...)} 换了收件人。
     *
     * <p>对照原项目：路由是写死的"发给除我之外的所有人"，
     * 不存在"负责方变化"这种事，也就不可能支持升级。
     */
    private static void demoFourEscalation() {
        section("Demo 4：升级之后，同一句话换了一个收件人");
        Team team = build();

        team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
        System.out.println("  当前负责方：" + team.hub().assignee().displayName());

        team.customer().escalate("等了 3 天没人处理，要求升级");
        System.out.println("  当前负责方：" + team.hub().assignee().displayName());

        team.customer().say(MessageKind.QUESTION, "到底什么时候能退？");
        System.out.println("  客户代码一个字没改，收件人从「客服」变成了「主管」");
    }

    // ------------------------------------------------------------------ Demo 5

    /**
     * Demo 5：关闭工单是一次真正的广播；关闭之后不能再发言。
     *
     * <p>对照原项目：任何时候都能给任何人发消息，没有"会话生命周期"的概念，
     * 也就不存在"关闭"这个状态。
     */
    private static void demoFiveClose() {
        section("Demo 5：关闭工单 —— 唯一一处真正的广播");
        Team team = build();

        team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
        team.agent().closeTicket();

        System.out.println("  关闭之后再发言：");
        try {
            team.customer().say(MessageKind.QUESTION, "等等，我还有个问题");
        } catch (IllegalStateException e) {
            System.out.println("    抛出 IllegalStateException：" + e.getMessage());
        }
        System.out.println("  已关闭 = " + team.hub().closed() + "，成功投递 " + team.hub().deliveredCount() + " 次");
    }

    // ------------------------------------------------------------------ Demo 6

    /**
     * Demo 6：一家炸了，其他家照常收到；没定义路由的消息被拦下。
     *
     * <p>两件事在同一段里演示：
     *
     * <ol>
     *   <li><b>异常隔离。</b>短信网关超时，不影响客服和客户收到归档通知；</li>
     *   <li><b>未定义路由被拒绝。</b>仓库试图发"提问"，中枢直接丢弃并说明原因 ——
     *       而不是像原项目那样，谁都能往任何地方发。</li>
     * </ol>
     */
    private static void demoSixIsolation() {
        section("Demo 6：一家炸了，其他家照常；没定义路由的消息被拦下");
        AfterSalesHub hub = new AfterSalesHub("AS-2024-0918");
        Customer customer = new Customer("王先生", hub);
        Agent agent = new Agent("小李", hub);
        Warehouse warehouse = new Warehouse("华南仓", hub);
        BrokenNotifier broken = new BrokenNotifier(hub);

        hub.join(customer);
        hub.join(agent);
        hub.join(warehouse);
        hub.join(broken);
        printRoster(hub);

        System.out.println("  —— 仓库试图发一条「提问」（路由表里没有这条边）——");
        warehouse.say(MessageKind.QUESTION, "有人能告诉我这单寄回哪里吗？");

        System.out.println("  —— 缺货上报：财务没加入，跳过；其余照常 ——");
        warehouse.say(MessageKind.STOCK_SHORTAGE, "EP-2024 无线耳机，华南仓缺货");

        System.out.println("  —— 关闭工单：短信网关故障，其他人照常收到 ——");
        agent.closeTicket();

        System.out.println("  投递失败记录（" + hub.deliveryFailures().size() + " 条）：");
        for (String failure : hub.deliveryFailures()) {
            System.out.println("    · " + failure);
        }
    }

    /**
     * 一个会炸的通知器：模拟"外部渠道超时"。
     *
     * <p>它存在的意义是证明中枢的 try-catch 真的有用 ——
     * 而不是"看起来写了 try-catch"。
     */
    private static final class BrokenNotifier implements Participant {

        private final CollaborationHub hub;

        BrokenNotifier(CollaborationHub hub) {
            this.hub = hub;
        }

        @Override
        public Role role() {
            return Role.NOTIFIER;
        }

        @Override
        public String name() {
            return "短信网关（故障）";
        }

        @Override
        public CollaborationHub hub() {
            return hub;
        }

        @Override
        public void receive(Message message) {
            throw new IllegalStateException("短信通道超时，" + message.kind().label() + " 未送达");
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void printRoster(AfterSalesHub hub) {
        List<String> labels = hub.roster().stream()
                .map(p -> p.role().displayName() + "-" + p.name())
                .toList();
        System.out.println("  参与方（" + labels.size() + "）：" + String.join("、", labels));
    }

    private static void section(String title) {
        if (printedSections > 0) {
            System.out.println();
        }
        printedSections++;
        System.out.println("[" + title + "]");
    }

    private static void summary() {
        section("六段 Demo 对应原项目的六个问题");
        System.out.println("  1. 客户提问只到负责方       —— 原项目只能广播给所有人");
        System.out.println("  2. 缺货触发三方协同         —— 原项目没有「一条消息多个收件人」的规则");
        System.out.println("  3. 换个角色发言换批收件人   —— 原项目的收发两端永远是「除我之外」");
        System.out.println("  4. 升级后负责方变更         —— 原项目没有「负责方」这个概念");
        System.out.println("  5. 关闭是唯一一次广播       —— 原项目全程都在广播");
        System.out.println("  6. 一家炸了不影响其他家     —— 原项目裸循环，一个 null 就断链");
        System.out.println();
        System.out.println("  六个同事的代码里没有一行「通知某某」——协同规则只写在中枢的 route() 里");
    }
}
