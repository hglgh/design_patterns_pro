package com.patterns.behavioral.mediator;

/**
 * 售后协同里的六种角色。
 *
 * <p><b>为什么要有这个枚举？</b>
 * 原项目判断"这条消息该给谁"靠的是引用比较：
 *
 * <pre>
 *   for (User user : users) {
 *       if (user != sender) {          // 除了我，其他人都给
 *           user.receive(sender.name + "：" + message);
 *       }
 *   }
 * </pre>
 *
 * 这段代码能跑，是因为聊天室只有一种语义：<b>广播</b>。
 * 但真实的售后协同不是广播 —— 客户提问只该给"当前负责方"，
 * 仓库上报缺货要同时给客服、财务、客户。这时候"我是谁"就成了
 * 路由的输入，而 {@code user != sender} 回答不了"我是谁"。
 *
 * <p>一旦要用"身份"做判断，就必须有一个明确的身份表示。
 * 用字符串（{@code name.equals("客服")}）是最差的选择：改文案就失效；
 * 用具体类（{@code instanceof Agent}）次之：加角色要改所有判断，
 * 而且"同一个角色多个实例"没法表达。枚举是这里的正解：
 * <b>角色是一个封闭集合，正好是枚举的形状。</b>
 *
 * <p>注意 {@link #NOTIFIER} 不是"人"，而是"外发渠道"（站内信、短信）。
 * 它出现在这里，是因为在中介者眼里，<b>所有参与者都只是"能收消息的一方"</b>，
 * 不管它背后是人、是部门、还是另一个系统。
 *
 * @author design-patterns-pro
 */
public enum Role {

    /** 客户：售后工单的发起方，只应该看到给"客户"看的消息。 */
    CUSTOMER("客户"),

    /** 一线客服：工单的默认负责方，负责判断、答复、发起跨部门协调。 */
    AGENT("客服"),

    /** 仓库：唯一能回答"还有没有货"的角色。 */
    WAREHOUSE("仓库"),

    /** 财务：唯一能回答"钱退不退、退多少"的角色。 */
    FINANCE("财务"),

    /** 主管：升级之后的负责方，也是异常工单的兜底人。 */
    SUPERVISOR("主管"),

    /** 通知器：把消息外发成站内信 / 短信的渠道，通常对接外部系统。 */
    NOTIFIER("通知器");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 中文名，用于日志与界面文案。
     *
     * <p>把中文名放在枚举里，而不是散落在各处 {@code switch} 里，
     * 是为了让"新增一个角色"只改一处 —— 编译器会提醒你补上这个名字。
     *
     * @return 角色的中文名
     */
    public String displayName() {
        return displayName;
    }
}
