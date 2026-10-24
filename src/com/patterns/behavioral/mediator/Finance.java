package com.patterns.behavioral.mediator;

import java.util.Objects;

/**
 * 财务：唯一能回答"钱退不退、退多少"的一方。
 *
 * <p><b>为什么财务要关心"缺货"？</b>
 * 因为"仓库没货"直接意味着"这单发不出去"，而发不出去就要退款，
 * 退款要走财务。这条因果关系是<b>业务规则</b>，不是技术细节 ——
 * 它必须写在某个地方。
 *
 * <p>问题在于写在哪里。三种选择：
 *
 * <ul>
 *   <li>写在仓库里：仓库要知道财务存在。仓库从"查库存"变成"懂退款流程"。</li>
 *   <li>写在财务里：财务要知道"缺货"这件事。问题变成"财务怎么知道客户提问、
 *       怎么知道客服答复"，最终它得把整条链路都听一遍。</li>
 *   <li>写在中枢里：仓库只管上报缺货，中枢负责让财务知道。<b>本项目选的这个。</b></li>
 * </ul>
 *
 * <p>这三种写法在代码行数上差不多，差别在<b>改动成本</b>：
 * 半年后公司决定"缺货也要通知供应商"，改动点分别是
 * "改仓库"、"改财务 + 加一条链路"、"在 {@code route()} 的
 * {@code STOCK_SHORTAGE} 分支里加一个角色"。只有最后一种是"改一行"。
 *
 * <p>注意财务的 {@code receive} 只有两个分支，没有 {@code default}：
 * 它的收件箱里只该有"缺货"和"归档"两种，其余类型到这里就是无意义的噪音。
 *
 * @author design-patterns-pro
 */
public final class Finance implements Participant {

    private final String name;
    private final CollaborationHub hub;

    /**
     * 创建一名财务参与者。
     *
     * @param name 财务称呼，不能为空白
     * @param hub  所属中枢，不能为 {@code null}
     */
    public Finance(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("财务称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.FINANCE;
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
     * 财务收到消息时的反应。
     *
     * <p>收到缺货：冻结原有发票、准备退款。
     * 收到归档：把凭证归档，流程结束。
     *
     * @param message 中枢投递过来的消息
     */
    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 财务：冻结原发票，准备全额退款。");
            case ARCHIVE -> System.out.println("        [" + name + "] 财务：退款凭证已归档。");
        }
    }
}
