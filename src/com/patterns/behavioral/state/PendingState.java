package com.patterns.behavioral.state;

import com.patterns.util.Money;

import java.util.Set;

/**
 * 待支付：订单刚创建，还没付钱。
 *
 * <p><b>这个状态能做什么？</b> 两件：付钱，或者取消。
 * 既不能发货（钱还没到），也不能确认收货（货还没发）。
 *
 * <p><b>为什么用 {@code enum} 实现状态？</b>
 * 三个理由，每一个都是实打实的：
 * <ol>
 *   <li><b>状态对象没有实例数据。</b> {@code PendingState} 里没有任何字段，
 *       所有行为都由类本身决定。这种情况下创建多个实例毫无意义——
 *       原项目每次转移都 {@code new PaidState()}，一个订单走完流程创建了 3 个对象，
 *       而它们彼此完全等价。</li>
 *   <li><b>{@code enum} 天生是单例，而且序列化安全。</b> 自己写
 *       {@code private static final PendingState INSTANCE} 也能做到单例，
 *       但反序列化会造出新对象；{@code enum} 由 JVM 保证不会。</li>
 *   <li><b>可以放心用 {@code ==} 比较。</b> 单例意味着引用相等就是值相等，
 *       在大量状态判断里省掉一次 {@code equals} 调用。</li>
 * </ol>
 *
 * <p><b>什么情况下状态不该用 enum？</b> 当状态需要<b>携带数据</b>时。
 * 比如"限时折扣中"这个状态要记住截止时间、"部分退款"要记住退了多少钱，
 * 这时状态对象每次都是不同的实例，必须用 {@code record} 或普通类。
 * <b>有数据就用类，没数据就用枚举</b>——这条判据适用于所有"多实例共享行为"的场景。
 *
 * @author design-patterns-pro
 */
public enum PendingState implements OrderState {

    /** 唯一实例。 */
    INSTANCE;

    /**
     * 允许的动作。
     *
     * <p>用 {@link Set#of} 而不是 {@code new HashSet<>()}：
     * {@code Set.of} 返回<b>不可变</b>集合，调用方拿到之后没法偷偷
     * {@code add(OrderAction.SHIP)} 把"待支付可以发货"这条规则改掉。
     * 这比在注释里写"请勿修改"可靠得多——<b>用类型系统表达约束，而不是用礼貌。</b>
     *
     * <p>缓存成 {@code static final} 而不是每次 {@code Set.of(...)} 新建：
     * 这个方法会被界面层<b>频繁调用</b>（每次渲染订单详情都要问一遍），
     * 每次新建一个小集合虽然便宜，但完全没必要。
     */
    private static final Set<OrderAction> ALLOWED =
            Set.of(OrderAction.PAY, OrderAction.CANCEL);

    @Override
    public String displayName() {
        return "待支付";
    }

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;
    }

    /**
     * 处理支付或取消。
     *
     * <p><b>注意 {@code switch} 里有四个分支，但只有两个会真的执行。</b>
     * 另外两个走 {@link OrderState#unreachable} 抛异常，因为
     * {@code Order.transition()} 在调用本方法之前已经用
     * {@code allowedActions()} 做过一次校验。
     *
     * <p>那为什么还要写这两个分支？因为 <b>{@code switch} 必须穷尽</b>——
     * 这正是把动作定义成 enum 换来的好处：将来如果有人给
     * {@code OrderAction} 加了第五个动作，这个 {@code switch} 会
     * <b>编译失败</b>，逼着人回来想清楚"待支付状态下这个新动作该怎么处理"。
     * 如果当初用 {@code default -> } 一笔带过，就会在运行时静默走错分支。
     *
     * @param action 动作
     * @param order  订单
     * @return 转移结果
     */
    @Override
    public TransitionResult accept(OrderAction action, Order order) {
        return switch (action) {
            case PAY -> {
                // 副作用：把「已经付过款」这个事实写进订单，而不是记时间——时间会让输出不可复现
                order.markPaid();
                yield TransitionResult.ok(action, this, PaidState.INSTANCE,
                        "收到 " + Money.yuan(order.amount()) + "，等待卖家发货");
            }
            case CANCEL -> {
                order.markCancelled("用户主动取消，未付款");
                yield TransitionResult.ok(action, this, CancelledState.INSTANCE,
                        "订单已取消，占用库存已释放");
            }
            case SHIP, CONFIRM -> throw OrderState.unreachable(this, action);
        };
    }
}
