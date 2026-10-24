package com.patterns.behavioral.state;

import java.util.Set;

/**
 * 订单状态。整个状态模式只有三个方法，但每一个都不能省。
 *
 * <p><b>一句话定义</b>：状态对象负责回答"这个动作在我这儿意味着什么"。
 * 同一个 {@code pay()} 调用，在"待支付"下是收款，在"已支付"下是重复支付，
 * 在"已完成"下是错乱请求——<b>调用方写的是同一行代码，行为由状态决定</b>。
 * 这就是状态模式相对 {@code if-else} 的全部价值。
 *
 * <h2>为什么是 {@code sealed}？</h2>
 *
 * <p>状态集合是<b>封闭的</b>：一张订单只可能有待支付、已支付、已发货、已完成、已取消
 * 这五种状态。{@code sealed} 把这条业务事实写进类型系统：
 * <ul>
 *   <li>编译器知道全部实现类，所以 {@code switch} 可以<b>穷尽</b>，不需要 {@code default}；</li>
 *   <li>将来新增一种状态时，所有没有覆盖它的 {@code switch} 会<b>编译失败</b>，
 *       而不是在运行时掉进 {@code default} 悄悄走错分支；</li>
 *   <li>{@code permits} 列表本身就是一份<b>可执行的状态清单</b>——
 *       想快速知道有哪些状态，看这一行，不用翻目录。</li>
 * </ul>
 *
 * <p>对比原项目：{@code interface OrderState} 是开放的，任何人都能写一个
 * {@code RefundedState} 并且<b>不需要修改任何现有代码</b>就让它生效。
 * 听起来像"扩展性好"，实际上是<b>失控</b>——状态转移的完整性没人能保证，
 * 也没人知道到底有几种状态。
 *
 * <h2>为什么只有三个方法？</h2>
 *
 * <p>因为状态只需要回答三个问题：
 * <table border="1">
 *   <caption>三个方法各回答什么</caption>
 *   <tr><th>方法</th><th>回答的问题</th><th>谁在用</th></tr>
 *   <tr><td>{@link #displayName()}</td><td>我叫什么</td><td>日志、界面文案</td></tr>
 *   <tr><td>{@link #allowedActions()}</td><td>我这儿能做哪些事</td><td>界面渲染、前置校验</td></tr>
 *   <tr><td>{@link #accept(OrderAction, Order)}</td><td>做这件事会发生什么</td><td>业务逻辑</td></tr>
 * </table>
 *
 * <p><b>注意这里没有 {@code isFinal()}。</b> 终态的本质是"没有允许的动作"，
 * 也就是 {@code allowedActions().isEmpty()}。多写一个 {@code isFinal()}
 * 就多了一个可能和 {@code allowedActions()} 打架的真相来源——
 * 有人把 {@code isFinal()} 改成 {@code true} 却忘了清空动作集合，
 * 于是"终态"的订单还能被取消。
 *
 * <blockquote>
 * <b>凡是能从已有方法推导出来的属性，就不要加第二个方法。</b>
 * 两个真相来源 = 一个必然会出现的矛盾。
 * </blockquote>
 *
 * <h2>为什么 {@code accept} 允许状态拿到 {@code Order}？</h2>
 *
 * <p>因为状态转移往往伴随<b>业务数据的写入</b>：
 * 支付要记下"第几步付的"，发货要生成物流单号，取消要释放库存。
 * 把这些都推给调用方做，状态类就退化成了一张"转移表"——
 * 那正是 {@link TransitionTable} 演示的另一种写法，也是本模式<b>不该</b>被使用的情形。
 *
 * <p>但给出去的权利必须有边界，所以约定是：
 * <ul>
 *   <li>状态<b>可以</b>调用 {@code Order} 上那些包内可见的写方法
 *       （{@code markPaid / issueTrackingNo / markDelivered / markCancelled}）；</li>
 *   <li>状态<b>不能</b>自己改 {@code Order.state}——转移由
 *       {@code Order.transition()} 统一执行，只有一个写入口。</li>
 * </ul>
 * 这条边界让"状态"负责<b>决策</b>，"订单"负责<b>执行</b>。两者分开之后，
 * 想给所有转移加一条统一的日志或指标，只改 {@code Order.transition()} 一处。
 *
 * @author design-patterns-pro
 */
public sealed interface OrderState
        permits PendingState, PaidState, ShippedState, CompletedState, CancelledState {

    /**
     * 状态的中文名，用于日志和界面。
     *
     * <p>为什么不用 {@code getClass().getSimpleName()}？因为它会打出
     * {@code PendingState}——对着用户界面展示英文类名，是典型的
     * "把实现细节漏到表现层"。而枚举常量名也不能直接当中文用，
     * 所以 {@code displayName()} 是必须存在的<b>翻译层</b>。
     *
     * @return 中文状态名，例如 {@code 待支付}
     */
    String displayName();

    /**
     * 当前状态下允许的动作集合。
     *
     * <p>这个方法的用途不是"给业务逻辑判断"，而是<b>给界面用</b>：
     * 前端直接拿这个集合决定哪个按钮可以点、哪个要灰掉。
     * 如果没有它，前端只能自己再写一遍 {@code if (status == 1 && ...)}——
     * 于是状态规则就有了两份，一份在后端状态类里，一份在前端，
     * 而且它们迟早会不一致。
     *
     * <p>返回 {@link Set} 而不是 {@code List}：语义上是"允许的集合"，
     * 顺序无意义，且天然去重。实现类应当返回<b>不可变</b>集合，
     * 防止调用方拿到引用后偷偷往里加动作。
     *
     * @return 允许的动作集合；终态返回空集合
     */
    Set<OrderAction> allowedActions();

    /**
     * 在当前状态下执行某个动作。
     *
     * <p><b>契约（实现类必须遵守）</b>：
     * <ol>
     *   <li>只有当 {@code allowedActions().contains(action)} 为真时才可能成功；</li>
     *   <li>成功时返回的目标状态必须非空，且应当是另一个真实存在的状态；</li>
     *   <li>失败时只返回 {@link TransitionResult#rejected}，<b>不得修改 order 的任何数据</b>
     *       ——被拒绝的动作不能留下副作用；</li>
     *   <li>本方法<b>不修改</b> {@code order} 的状态，只做业务数据写入，
     *       真正的状态切换由 {@code Order.transition()} 完成。</li>
     * </ol>
     *
     * <p>第 4 条是最容易做错的一条。如果状态自己调 {@code order.setState(...)}，
     * 那么"状态什么时候变"就散落在五个类里；更糟的是，
     * 一次失败的转移也可能已经改了状态，而调用方毫不知情。
     * 把切换动作收在 {@code Order} 里，才能保证<b>成功才切、失败一定不切</b>。
     *
     * @param action 要执行的动作
     * @param order  订单，用于写入业务数据和读取上下文
     * @return 转移结果，成功或带原因的拒绝
     */
    TransitionResult accept(OrderAction action, Order order);

    /**
     * 构造一个"不该发生"的异常，供各状态在 {@code switch} 的剩余分支里使用。
     *
     * <p><b>它为什么存在？</b>
     * 接口把"哪些动作允许"交给了 {@link #allowedActions()}，把"每个允许的动作做什么"
     * 交给了 {@link #accept}——这是两个独立的实现，理论上可能不一致：
     * 有人在 {@code allowedActions()} 里加了 {@code CANCEL}，却忘了在
     * {@code accept} 里加对应的分支。
     *
     * <p>这种<b>自相矛盾</b>不应该被静默容忍。但也不能用 {@code default} 返回一个
     * 假结果——那会把编程错误伪装成业务拒绝，上线后极难查。
     * 所以每个状态的 {@code switch} 里，<b>不允许的分支一律走到这里抛异常</b>：
     * 不变量一旦被破坏，就在第一次被踩到时大声崩掉，而不是留下一串诡异的订单。
     *
     * <p>把构造逻辑收在接口里，是为了让五个状态的报错格式完全一致——
     * 排查时不用去分辨"这句错误文案是哪个类写的"。
     *
     * @param state  出问题的状态
     * @param action 不该被收到的动作
     * @return 异常对象（由调用方 {@code throw}）
     */
    static IllegalStateException unreachable(OrderState state, OrderAction action) {
        return new IllegalStateException(
                "[" + state.displayName() + "] 收到了不该收到的动作「" + action.label()
                        + "」：allowedActions() 与 accept() 的实现不一致，"
                        + "这是一个编程错误，不是业务错误");
    }
}
