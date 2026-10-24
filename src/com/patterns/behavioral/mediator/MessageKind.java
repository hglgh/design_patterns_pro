package com.patterns.behavioral.mediator;

/**
 * 协同消息的七种语义。
 *
 * <p><b>为什么不能只用一个 {@code String}？</b>
 * 原项目的中介者接口是：
 *
 * <pre>
 *   void sendMessage(String message, User sender);
 * </pre>
 *
 * 这里"消息是什么性质"完全丢失了。到了真实业务里，中枢必须知道
 * 这是提问、是答复、还是缺货上报 —— 因为<b>不同的语义走不同的路由</b>。
 *
 * <p>如果只剩下字符串，唯一的出路是猜：
 *
 * <pre>
 *   if (message.contains("缺货")) {          // 靠文案猜语义
 *       notifyFinance();
 *   }
 * </pre>
 *
 * 这段代码的寿命取决于运营会不会改写文案。审核把「缺货」改成
 * 「暂无库存」，路由立刻静默失效 —— 这正是迭代器模式文档里
 * §2.9「类型只能靠名字猜」的同一个坑。
 *
 * <p>把语义做成枚举之后，{@code switch} 是穷尽的：
 * 新增一种消息类型，编译器会在中枢的规则表上直接报错，
 * 而不是等到线上某条消息没人接。
 *
 * <p><b>为什么 {@link #ESCALATION} 和 {@link #ARCHIVE} 也在里面？</b>
 * 因为它们同样"要发给某些人"。中介者的职责是路由，
 * 而不是限制"只能由 say() 触发"。区别只在入口：
 * 这两个由 {@code escalate()} / {@code close()} 产生，
 * 不允许通过 {@code say()} 手工发送（见 {@code AfterSalesHub} 的规则表）。
 *
 * @author design-patterns-pro
 */
public enum MessageKind {

    /** 提问：客户提出的问题，只该到"当前负责方"手里。 */
    QUESTION("提问"),

    /** 答复：客服或主管给出的结论，只该回到客户手里。 */
    ANSWER("答复"),

    /** 缺货上报：仓库给出的事实，需要客服、财务、客户三方同时知道。 */
    STOCK_SHORTAGE("缺货上报"),

    /** 退款确认：财务给出的结论，需要客户知道，也需要客服留痕。 */
    REFUND_CONFIRMED("退款确认"),

    /** 提示：任意角色发出的补充说明，只推给客户，避免内部噪音外泄。 */
    NOTICE("提示"),

    /** 升级：由 {@code escalate()} 产生，发给主管并抄送客服。 */
    ESCALATION("升级"),

    /** 归档：由 {@code close()} 产生，是本项目唯一一处真正的广播。 */
    ARCHIVE("归档");

    private final String label;

    MessageKind(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于日志与界面文案。
     *
     * @return 消息类型的中文名
     */
    public String label() {
        return label;
    }
}
