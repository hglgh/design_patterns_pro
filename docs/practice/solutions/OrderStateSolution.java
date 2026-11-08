import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 练习 02 参考实现：订单状态流转。
 *
 * <p>运行方式：</p>
 * <pre>java docs/practice/solutions/OrderStateSolution.java</pre>
 *
 * <p>这个文件里放了<b>两套</b>实现：一套状态模式，一套查表。
 * 目的不是证明状态模式更好，而是让你看清「什么时候查表就够了」。
 * 真实项目里 {@code state/TransitionTable.java} 就是这套对照。</p>
 */
public final class OrderStateSolution {

    // ==================== 一、动作与结果 ====================

    enum OrderAction {
        PAY("支付"),
        SHIP("发货"),
        CONFIRM("确认收货"),
        CANCEL("取消");

        private final String label;

        OrderAction(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * 一次动作的结果。
     *
     * <p>为什么不用 boolean：boolean 只能表达「成没成」，表达不了
     * 「没成是因为状态不允许，还是因为重复操作被幂等忽略」。
     * 运营和客服要的恰恰是后者。</p>
     */
    record TransitionResult(OrderState from, OrderState to, String note) {
        boolean changed() {
            return from != to;
        }

        String render() {
            String arrow = changed()
                    ? from.code() + " → " + to.code()
                    : from.code() + "（不变）";
            return arrow + "  " + note;
        }
    }

    // ==================== 二、状态接口 ====================

    interface OrderState {
        String code();

        /**
         * 收到一个动作时怎么反应。
         *
         * <p>注意参数里有 {@code Order}：状态需要回调订单拿数据（比如发货单号），
         * 但<b>不该自己改订单</b>——改状态由 {@link Order#apply} 统一做。</p>
         */
        TransitionResult accept(OrderAction action, Order order);
    }

    // ==================== 三、五个状态 ====================

    enum Pending implements OrderState {
        INSTANCE;

        public String code() {
            return "待支付";
        }

        public TransitionResult accept(OrderAction action, Order order) {
            return switch (action) {
                case PAY -> new TransitionResult(this, Paid.INSTANCE, "支付成功，等待发货");
                case CANCEL -> new TransitionResult(this, Cancelled.INSTANCE, "用户取消，未支付无需退款");
                case SHIP -> new TransitionResult(this, this, "未支付不能发货");
                case CONFIRM -> new TransitionResult(this, this, "未发货不能确认收货");
            };
        }
    }

    enum Paid implements OrderState {
        INSTANCE;

        public String code() {
            return "已支付";
        }

        public TransitionResult accept(OrderAction action, Order order) {
            return switch (action) {
                // 同一个 PAY，在「已支付」状态下的含义完全不同：这是状态模式存在的理由
                case PAY -> new TransitionResult(this, this, "重复支付，已幂等忽略");
                case SHIP -> new TransitionResult(this, Shipped.INSTANCE,
                        "发货完成，单号 " + order.issueTrackingNo());
                case CANCEL -> new TransitionResult(this, Cancelled.INSTANCE, "已支付取消，需要退款");
                case CONFIRM -> new TransitionResult(this, this, "未发货不能确认收货");
            };
        }
    }

    enum Shipped implements OrderState {
        INSTANCE;

        public String code() {
            return "已发货";
        }

        public TransitionResult accept(OrderAction action, Order order) {
            return switch (action) {
                case CONFIRM -> new TransitionResult(this, Completed.INSTANCE, "确认收货，交易完成");
                case PAY -> new TransitionResult(this, this, "已发货，不能再支付");
                case SHIP -> new TransitionResult(this, this, "已发货，不能重复发货");
                case CANCEL -> new TransitionResult(this, this, "已发货，需走退货流程而不是取消");
            };
        }
    }

    enum Completed implements OrderState {
        INSTANCE;

        public String code() {
            return "已完成";
        }

        public TransitionResult accept(OrderAction action, Order order) {
            return switch (action) {
                case PAY, SHIP, CONFIRM, CANCEL -> new TransitionResult(this, this, "订单已结束，动作被拒绝");
            };
        }
    }

    enum Cancelled implements OrderState {
        INSTANCE;

        public String code() {
            return "已取消";
        }

        public TransitionResult accept(OrderAction action, Order order) {
            return switch (action) {
                case PAY, SHIP, CONFIRM, CANCEL -> new TransitionResult(this, this, "订单已取消，动作被拒绝");
            };
        }
    }

    // ==================== 四、订单：持有状态，控制流转 ====================

    static final class Order {
        private final String orderNo;
        private final List<String> history = new ArrayList<>();
        private OrderState state = Pending.INSTANCE;
        private int trackingSeq = 1000;

        Order(String orderNo) {
            this.orderNo = orderNo;
        }

        TransitionResult apply(OrderAction action) {
            TransitionResult result = state.accept(action, this);
            if (result.changed()) {
                state = result.to();
            }
            history.add(action.label() + "  " + result.render());
            return result;
        }

        String issueTrackingNo() {
            return "SF" + (++trackingSeq);
        }

        OrderState state() {
            return state;
        }

        List<String> history() {
            return history;
        }

        String orderNo() {
            return orderNo;
        }
    }

    // ==================== 五、对照实现：查表 ====================

    /**
     * 同一套业务规则的另一种写法：一张表。
     *
     * <p>优点：规则一眼可见，代码短，加状态只加一行。
     * 缺点：表达不了「同动作在不同状态下含义不同」，也做不了副作用（发货单号）。</p>
     */
    static final class TableOrder {
        private static final Map<OrderState, Map<OrderAction, OrderState>> TABLE = new LinkedHashMap<>();

        static {
            TABLE.put(Pending.INSTANCE, Map.of(
                    OrderAction.PAY, Paid.INSTANCE,
                    OrderAction.CANCEL, Cancelled.INSTANCE));
            TABLE.put(Paid.INSTANCE, Map.of(
                    OrderAction.SHIP, Shipped.INSTANCE,
                    OrderAction.CANCEL, Cancelled.INSTANCE));
            TABLE.put(Shipped.INSTANCE, Map.of(
                    OrderAction.CONFIRM, Completed.INSTANCE));
            TABLE.put(Completed.INSTANCE, Map.of());
            TABLE.put(Cancelled.INSTANCE, Map.of());
        }

        private final String orderNo;
        private OrderState state = Pending.INSTANCE;
        private final List<String> history = new ArrayList<>();

        TableOrder(String orderNo) {
            this.orderNo = orderNo;
        }

        void apply(OrderAction action) {
            OrderState from = state;
            OrderState next = TABLE.getOrDefault(from, Map.of()).get(action);
            if (next == null) {
                history.add(action.label() + "  " + from.code() + "（不变）  不支持的动作");
                return;
            }
            state = next;
            history.add(action.label() + "  " + from.code() + " → " + next.code());
        }

        OrderState state() {
            return state;
        }

        List<String> history() {
            return history;
        }
    }

    // ==================== 六、跑一遍 ====================

    public static void main(String[] args) {
        System.out.println("=== 状态模式：一条正常订单 ===");
        Order order = new Order("SO-2026-001");
        order.apply(OrderAction.PAY);
        order.apply(OrderAction.PAY);
        order.apply(OrderAction.SHIP);
        order.apply(OrderAction.CONFIRM);
        order.apply(OrderAction.CANCEL);
        order.history().forEach(System.out::println);
        System.out.println("终态：" + order.state().code());
        System.out.println();

        System.out.println("=== 同一套状态，换一条异常路径 ===");
        Order abnormal = new Order("SO-2026-002");
        abnormal.apply(OrderAction.SHIP);
        abnormal.apply(OrderAction.PAY);
        abnormal.apply(OrderAction.CANCEL);
        abnormal.history().forEach(System.out::println);
        System.out.println("终态：" + abnormal.state().code());
        System.out.println();

        System.out.println("=== 对照：同一套规则用查表实现 ===");
        TableOrder table = new TableOrder("SO-2026-003");
        table.apply(OrderAction.PAY);
        table.apply(OrderAction.PAY);
        table.apply(OrderAction.SHIP);
        table.history().forEach(System.out::println);
        System.out.println("终态：" + table.state().code());
        System.out.println("查表版丢掉了什么：");
        System.out.println("  1. 「重复支付被幂等忽略」和「未支付不能发货」在表里长得一样（都是没有这条规则）；");
        System.out.println("  2. 发货单号这种副作用无处安放，只能塞回 Order 里做 if 判断；");
        System.out.println("  3. 加一个「部分退款」这种「不换状态但要做别的事」的动作，表根本表达不了。");
        System.out.println();

        System.out.println("=== 加一个新动作，两套实现各要改哪里 ===");
        System.out.println("状态模式：");
        System.out.println("  1. OrderAction 加一个枚举值（编译器会逼你处理所有 switch）；");
        System.out.println("  2. 5 个状态各自表态 —— 这是成本，也是「每个状态都必须想清楚」的收益。");
        System.out.println("查表版：");
        System.out.println("  1. OrderAction 加枚举值；");
        System.out.println("  2. 在 TABLE 里给需要的状态加一行 —— 没加的状态自动变成「不支持」，");
        System.out.println("     编译器不会提醒你漏了哪个状态。");
        System.out.println();

        System.out.println("=== 加一个新状态（比如「退款中」），两套实现各要改哪里 ===");
        System.out.println("状态模式：新增一个 enum 实现 + 相关状态里补分支；");
        System.out.println("查表版：TABLE 加一行 + 所有能进入该状态的动作加映射。");
        System.out.println("结论：状态种类稳定、动作语义一致 → 查表更划算；");
        System.out.println("      状态少但每个状态的语义差别大、有副作用 → 状态模式更划算。");
    }

    private OrderStateSolution() {
    }
}
