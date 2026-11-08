import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 练习 01 参考实现：电商订单优惠计算。
 *
 * <p>运行方式（单文件源模式，不需要先编译）：</p>
 * <pre>java docs/practice/solutions/OrderDiscountSolution.java</pre>
 *
 * <p>这里刻意做成「一个小订单的优惠链」，而不是把 {@code combined/orderservice} 抄一遍：
 * 账本、追踪、配置解析全部去掉了，只保留「条件 + 算法 + 互斥 + 封顶」四件事。
 * 想看完整工程版，去读 {@code src/com/patterns/combined/orderservice}。</p>
 */
public final class OrderDiscountSolution {

    // ==================== 一、事实：不变的部分 ====================

    /** 订单事实。用 record 保证「一份订单在计算过程中不会被改」。 */
    record Order(BigDecimal amount, boolean member, boolean weekend, int points) {
        Order {
            if (amount == null) {
                throw new IllegalArgumentException("金额不能为 null");
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("金额不能为负：" + amount.toPlainString());
            }
        }

        BigDecimal money() {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ==================== 二、规则：门槛会单独变 ====================

    /** 使用条件。抽出来的唯一理由：门槛每周调，算法几个月不动。 */
    interface Requirement {
        boolean test(Order order);

        String describe();

        static Requirement always() {
            return new Requirement() {
                public boolean test(Order order) {
                    return true;
                }

                public String describe() {
                    return "无门槛";
                }
            };
        }

        static Requirement memberOnly() {
            return new Requirement() {
                public boolean test(Order order) {
                    return order.member();
                }

                public String describe() {
                    return "仅会员";
                }
            };
        }

        static Requirement weekendOnly() {
            return new Requirement() {
                public boolean test(Order order) {
                    return order.weekend();
                }

                public String describe() {
                    return "仅周末";
                }
            };
        }

        static Requirement atLeast(String threshold) {
            BigDecimal min = new BigDecimal(threshold);
            return new Requirement() {
                public boolean test(Order order) {
                    return order.money().compareTo(min) >= 0;
                }

                public String describe() {
                    return "满 " + min.toPlainString();
                }
            };
        }

        static Requirement hasPoints(int min) {
            return new Requirement() {
                public boolean test(Order order) {
                    return order.points() >= min;
                }

                public String describe() {
                    return "积分 ≥ " + min;
                }
            };
        }
    }

    // ==================== 三、算法：会新增的部分 ====================

    /** 优惠算法。新增一种优惠 = 新增一个实现，链和条件都不动。
     *
     * <p>{@code price} 是<b>当前价</b>（前面节点减过的价），不是订单原价。
     * 传原价是这类系统最常见的一个 bug：九折会在第二张券之后又按原价算一遍。</p>
     */
    interface DiscountPolicy {
        String name();

        BigDecimal discount(BigDecimal price, Order order);

        static BigDecimal valid(BigDecimal value, String who) {
            if (value == null) {
                throw new IllegalStateException(who + " 返回了 null");
            }
            if (value.signum() < 0) {
                throw new IllegalStateException(who + " 返回了负数：" + value.toPlainString());
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    record FullReduction(BigDecimal threshold, BigDecimal reduction) implements DiscountPolicy {
        public String name() {
            return "满减";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            return price.compareTo(threshold) >= 0 ? reduction : BigDecimal.ZERO;
        }
    }

    record Percentage(BigDecimal rate) implements DiscountPolicy {
        public String name() {
            return "百分比折扣";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            BigDecimal off = price.multiply(BigDecimal.ONE.subtract(rate));
            return DiscountPolicy.valid(off, name());
        }
    }

    record PointsDeduction(BigDecimal perPoint, int maxPoints) implements DiscountPolicy {
        public String name() {
            return "积分抵扣";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            int usable = Math.min(order.points(), maxPoints);
            return DiscountPolicy.valid(perPoint.multiply(BigDecimal.valueOf(usable)), name());
        }
    }

    // ==================== 四、装饰器：横切能力，不属于任何算法 ====================

    /** 封顶装饰器：总减免不超过上限。注意它包的是「算法」，不是「链节点」。 */
    static final class Capped implements DiscountPolicy {
        private final DiscountPolicy delegate;
        private final BigDecimal cap;

        Capped(DiscountPolicy delegate, String cap) {
            this.delegate = delegate;
            this.cap = new BigDecimal(cap);
        }

        public String name() {
            return delegate.name() + "（封顶 " + cap.toPlainString() + "）";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            return delegate.discount(price, order).min(cap);
        }
    }

    /** 追踪装饰器：只观察，不改结果。 */
    static final class Traced implements DiscountPolicy {
        private final DiscountPolicy delegate;
        private final List<String> trace;

        Traced(DiscountPolicy delegate, List<String> trace) {
            this.delegate = delegate;
            this.trace = trace;
        }

        public String name() {
            return delegate.name();
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            BigDecimal before = delegate.discount(price, order);
            trace.add("    [trace] " + delegate.name() + " 算出 " + before.toPlainString());
            return before;
        }
    }

    // ==================== 五、链：顺序、互斥、终止 ====================

    /** 链上节点共享的可变状态。链是不可以复用的，每次计算新建一个。 */
    static final class ChainState {
        final Order order;
        BigDecimal price;
        final Set<String> usedGroups = new LinkedHashSet<>();
        final List<String> log = new ArrayList<>();

        ChainState(Order order) {
            this.order = order;
            this.price = order.money();
        }

        void apply(String label, BigDecimal reduction) {
            BigDecimal actual = reduction.min(price);
            price = price.subtract(actual);
            log.add(String.format("  %-26s -%s  →  %s", label, actual.toPlainString(), price.toPlainString()));
        }
    }

    enum Kind { APPLIED, SKIPPED }

    record Decision(Kind kind, BigDecimal reduction, String note) {
        static Decision applied(BigDecimal reduction) {
            return new Decision(Kind.APPLIED, reduction, "");
        }

        static Decision skipped(String note) {
            return new Decision(Kind.SKIPPED, BigDecimal.ZERO, note);
        }
    }

    /** 链骨架：process 是 final，子类只能决定「这一步要不要减、减多少」。 */
    abstract static class DiscountHandler {
        private final String name;
        private DiscountHandler next;

        protected DiscountHandler(String name) {
            this.name = name;
        }

        final DiscountHandler linkTo(DiscountHandler next) {
            this.next = next;
            return this;
        }

        final void process(ChainState state) {
            Decision decision = decide(state);
            if (decision.kind() == Kind.APPLIED) {
                state.log.add("  · " + name + " 命中");
                state.apply(name, decision.reduction());
            } else {
                state.log.add("  · " + name + " 跳过（" + decision.note() + "）");
            }
            if (next != null) {
                next.process(state);
            }
        }

        protected abstract Decision decide(ChainState state);
    }

    /** 策略与链的接缝：先问条件，再算金额。 */
    static final class PolicyNode extends DiscountHandler {
        private final DiscountPolicy policy;
        private final Requirement requirement;

        PolicyNode(String name, DiscountPolicy policy, Requirement requirement) {
            super(name);
            this.policy = policy;
            this.requirement = requirement;
        }

        protected Decision decide(ChainState state) {
            if (!requirement.test(state.order)) {
                return Decision.skipped(requirement.describe() + " 不满足");
            }
            return Decision.applied(policy.discount(state.price, state.order));
        }
    }

    /** 互斥组的接缝：同组只允许一张券生效。 */
    static final class ExclusiveNode extends DiscountHandler {
        private final String group;
        private final DiscountPolicy policy;
        private final Requirement requirement;

        ExclusiveNode(String name, String group, DiscountPolicy policy, Requirement requirement) {
            super(name);
            this.group = group;
            this.policy = policy;
            this.requirement = requirement;
        }

        protected Decision decide(ChainState state) {
            if (state.usedGroups.contains(group)) {
                return Decision.skipped("「" + group + "」组已占用");
            }
            if (!requirement.test(state.order)) {
                return Decision.skipped(requirement.describe() + " 不满足");
            }
            state.usedGroups.add(group);
            return Decision.applied(policy.discount(state.price, state.order));
        }
    }

    /** 链对象：只负责进出与整体约束，不负责算。 */
    static final class DiscountChain {
        private final DiscountHandler head;
        private final BigDecimal cap;

        private DiscountChain(DiscountHandler head, BigDecimal cap) {
            this.head = head;
            this.cap = cap;
        }

        static Builder builder() {
            return new Builder();
        }

        ChainState calculate(Order order) {
            ChainState state = new ChainState(order);
            head.process(state);
            // 封顶是「整条链的结果」上的约束，和「某个算法的上限」不是同一件事：
            // 前者只能在链跑完之后截断，后者适合做成策略装饰器（见 Capped）。
            if (cap != null) {
                BigDecimal reduction = order.money().subtract(state.price);
                if (reduction.compareTo(cap) > 0) {
                    state.log.add("  · 总减免 " + reduction.toPlainString()
                            + " 超过上限 " + cap.toPlainString() + "，截断");
                    state.price = order.money().subtract(cap);
                }
            }
            return state;
        }

        static final class Builder {
            private final List<DiscountHandler> nodes = new ArrayList<>();
            private BigDecimal cap;

            Builder stage(String name, DiscountPolicy policy, Requirement requirement) {
                nodes.add(new PolicyNode(name, policy, requirement));
                return this;
            }

            Builder exclusive(String name, String group, DiscountPolicy policy, Requirement requirement) {
                nodes.add(new ExclusiveNode(name, group, policy, requirement));
                return this;
            }

            Builder cap(String amount) {
                this.cap = new BigDecimal(amount);
                return this;
            }

            DiscountChain build() {
                if (nodes.isEmpty()) {
                    throw new IllegalStateException("至少需要一个节点");
                }
                DiscountHandler head = nodes.get(0);
                DiscountHandler cursor = head;
                for (int i = 1; i < nodes.size(); i++) {
                    cursor.linkTo(nodes.get(i));
                    cursor = nodes.get(i);
                }
                return new DiscountChain(head, cap);
            }
        }
    }

    // ==================== 六、改造前：不用模式的写法 ====================

    static BigDecimal before(Order order, String[] coupons) {
        BigDecimal price = order.money();
        for (String coupon : coupons) {
            if (coupon.equals("full100_20")) {
                if (price.compareTo(new BigDecimal("100")) >= 0) {
                    price = price.subtract(new BigDecimal("20"));
                }
            } else if (coupon.equals("member90")) {
                if (order.member()) {
                    price = price.multiply(new BigDecimal("0.9"));
                }
            } else if (coupon.equals("weekend85")) {
                if (order.weekend()) {
                    price = price.multiply(new BigDecimal("0.85"));
                }
            } else if (coupon.equals("points")) {
                int usable = Math.min(order.points(), 500);
                price = price.subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(usable)));
            } else {
                throw new IllegalArgumentException("未知券：" + coupon);
            }
        }
        // 封顶逻辑复制一遍
        if (order.money().subtract(price).compareTo(new BigDecimal("50")) > 0) {
            price = order.money().subtract(new BigDecimal("50"));
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 七、跑一遍 ====================

    public static void main(String[] args) {
        Order order = new Order(new BigDecimal("320.00"), true, true, 800);
        System.out.println("订单：金额 320.00，会员，周末，积分 800");
        System.out.println();

        System.out.println("=== 改造前：if-else + 一段重复的封顶逻辑 ===");
        String[] coupons = {"full100_20", "member90", "points"};
        System.out.println("券：满减 / 会员九折 / 积分");
        System.out.println("应付：" + before(order, coupons).toPlainString());
        System.out.println("问题：加一种券要改 3 处；没地方记录「哪张券跳过了、为什么」；");
        System.out.println("      封顶逻辑写死在方法末尾，换个业务就要再复制一遍。");
        System.out.println();

        System.out.println("=== 改造后：条件 + 算法 + 链 + 装饰器 ===");
        List<String> trace = new ArrayList<>();
        DiscountChain chain = DiscountChain.builder()
                .stage("满 100 减 20", new FullReduction(new BigDecimal("100"), new BigDecimal("20")),
                        Requirement.atLeast("100"))
                .exclusive("会员九折", "折扣券", new Traced(new Percentage(new BigDecimal("0.9")), trace),
                        Requirement.memberOnly())
                .exclusive("周末八五折", "折扣券", new Percentage(new BigDecimal("0.85")),
                        Requirement.weekendOnly())
                .stage("积分抵扣", new Capped(new PointsDeduction(new BigDecimal("0.01"), 500), "3"),
                        Requirement.hasPoints(1))
                .cap("50")
                .build();

        ChainState result = chain.calculate(order);
        result.log.forEach(System.out::println);
        trace.forEach(System.out::println);
        System.out.println("应付：" + result.price.toPlainString());
        System.out.println();

        System.out.println("=== 加一种「第二件半价」，改动面 ===");
        System.out.println("1. 新增一个 DiscountPolicy 实现（1 个文件）");
        System.out.println("2. 在 build() 里加一行 .stage(...)（1 行）");
        System.out.println("3. 条件、链骨架、装饰器、改造前的 before() ——全都不用动");
        System.out.println();

        System.out.println("=== 边界：负金额必须在构造时炸，不能流到计算里 ===");
        try {
            new Order(new BigDecimal("-1"), false, false, 0);
        } catch (IllegalArgumentException e) {
            System.out.println("拒绝：" + e.getMessage());
        }
        System.out.println();

        System.out.println("=== 改造前的真 bug：同一批券换个顺序，用户付的钱不一样 ===");
        Order small = new Order(new BigDecimal("150.00"), true, false, 200);
        System.out.println("小订单：150.00，会员，非周末，积分 200");
        System.out.println("顺序 A（满减 → 会员 → 积分）："
                + before(small, new String[]{"full100_20", "member90", "points"}).toPlainString());
        System.out.println("顺序 B（会员 → 满减 → 积分）："
                + before(small, new String[]{"member90", "full100_20", "points"}).toPlainString());
        System.out.println("差的 2 元来自「满减门槛用当前价还是原价」——旧代码里没有任何地方规定这件事，");
        System.out.println("而定门槛的运营和传券顺序的前端，是两个团队。");
        System.out.println();

        System.out.println("=== 两种「封顶」不是同一件事 ===");
        System.out.println("1. 单个算法的上限 → 策略装饰器 Capped（本例把积分抵抵扣在 3 元）");
        System.out.println("2. 整条链的总上限 → 链对象上的约束（本例总减免超 50 就截断）");
        System.out.println("前者可以多个策略各配一个，后者全局只能有一个。搞混就会写出「优惠券各自封顶了，");
        System.out.println("但叠起来还是减了 200」的 bug。");
        System.out.println();

        System.out.println("=== 注意：互斥只挡「同组第二张」，跨组照常叠加 ===");
        System.out.println("会员九折 与 周末八五折 同属「折扣券」组 → 第二张被跳过，见上面的日志。");
        System.out.println("满减、积分 属于其他组 → 正常叠加。");
    }

    private OrderDiscountSolution() {
    }
}
