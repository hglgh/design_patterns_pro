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

    /**
     * 使用条件。抽出来的唯一理由：门槛每周调，算法几个月不动。
     *
     * <p><b>为什么 {@code test} 要收 {@code currentPrice}？</b>
     * 「满 100」里的 100 到底和哪个价比，是业务决定，不是实现细节：
     * 和原价比，用户先用了别的券就能「绕」过门槛；和当前价比，门槛随实付走。
     * 把它放进签名，两种口径就必须在装配时明确选一个 —— 这正是 v5「差 2 元」的根治办法。</p>
     */
    interface Requirement {
        boolean test(Order order, BigDecimal currentPrice);

        String describe();

        /** 门槛是数据不是行为：只校验「不能为负」，不换算、不抽象成类。 */
        private static BigDecimal threshold(String text, String what) {
            BigDecimal value = new BigDecimal(text);
            if (value.signum() < 0) {
                throw new IllegalArgumentException(what + " 不能为负：" + value.toPlainString());
            }
            return value;
        }

        static Requirement always() {
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
                    return true;
                }

                public String describe() {
                    return "无门槛";
                }
            };
        }

        static Requirement memberOnly() {
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
                    return order.member();
                }

                public String describe() {
                    return "仅会员";
                }
            };
        }

        static Requirement weekendOnly() {
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
                    return order.weekend();
                }

                public String describe() {
                    return "仅周末";
                }
            };
        }

        /**
         * 满 N：按<b>当前价</b>判断。
         *
         * <p>这是改造前 {@code before()} 原本的行为：它循环里的 {@code price} 是「已经减过前几张券的价」。
         * 重构要保持外部行为不变，所以默认实现选当前价，而不是原价。</p>
         */
        static Requirement atLeast(String threshold) {
            BigDecimal min = threshold(threshold, "门槛");
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
                    return currentPrice.compareTo(min) >= 0;
                }

                public String describe() {
                    return "当前价满 " + min.toPlainString();
                }
            };
        }

        /**
         * 满 N：按<b>订单原价</b>判断，只用于对照。
         *
         * <p>它和 {@link #atLeast(String)} 在同一张订单上会给出不同答案 ——
         * 「原价 120、先减 30 到 90」时，原价口径说「满 100」成立，当前价口径说「不成立」。
         * 哪个才对是业务决定，代码的责任是<b>让它显式</b>。</p>
         */
        static Requirement atLeastOriginal(String threshold) {
            BigDecimal min = threshold(threshold, "门槛");
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
                    return order.money().compareTo(min) >= 0;
                }

                public String describe() {
                    return "原价满 " + min.toPlainString();
                }
            };
        }

        static Requirement hasPoints(int min) {
            if (min < 0) {
                throw new IllegalArgumentException("积分门槛不能为负：" + min);
            }
            return new Requirement() {
                public boolean test(Order order, BigDecimal currentPrice) {
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
     *
     * <p><b>算法不判条件。</b>「满 100」由 {@link Requirement} 表达，
     * 实现类只回答「减多少」。两边各判一次，就会出现「日志说命中、实际减 0」的矛盾。</p>
     *
     * <p>所有实现的返回值由链节点统一过 {@link #valid}，实现类自己不重复校验。</p>
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

    /**
     * 固定金额减免（「减 20」这一步）。
     *
     * <p><b>它不看门槛。</b>「满 100」是 {@link Requirement} 的事 —— 这是本练习
     * 最容易被写错的一处：原来这里又存了 {@code threshold} 又判了一次，
     * 而 {@code Requirement.atLeast} 也在判，且两边用的价（当前价 / 原价）还不一样，
     * 于是会出现「日志说命中、实际减 0」。把「满」搬出去后，
     * 一个满减券 = {@code 条件(满100) + 算法(减20)}，是两个对象，不是一个。</p>
     */
    record FlatReduction(BigDecimal amount) implements DiscountPolicy {
        FlatReduction {
            if (amount == null) {
                throw new IllegalArgumentException("立减金额不能为 null");
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("立减金额不能为负：" + amount.toPlainString());
            }
        }

        public String name() {
            return "立减 " + amount.toPlainString();
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            return amount;
        }
    }

    /** 百分比折扣。{@code rate} 是<b>实付比例</b>：0.9 = 九折，必须落在 (0, 1) 开区间。 */
    record Percentage(BigDecimal rate) implements DiscountPolicy {
        Percentage {
            if (rate == null) {
                throw new IllegalArgumentException("折扣比例不能为 null");
            }
            if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException(
                        "折扣比例必须在 (0, 1) 之间（0.9 = 九折）：" + rate.toPlainString());
            }
        }

        public String name() {
            return "百分比折扣";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            return price.multiply(BigDecimal.ONE.subtract(rate));
        }
    }

    /** 积分抵扣。{@code perPoint} 必须为正，{@code maxPoints} 不得为负。 */
    record PointsDeduction(BigDecimal perPoint, int maxPoints) implements DiscountPolicy {
        PointsDeduction {
            if (perPoint == null) {
                throw new IllegalArgumentException("每积分抵扣额不能为 null");
            }
            if (perPoint.signum() <= 0) {
                throw new IllegalArgumentException("每积分抵扣额必须为正：" + perPoint.toPlainString());
            }
            if (maxPoints < 0) {
                throw new IllegalArgumentException("可用积分上限不能为负：" + maxPoints);
            }
        }

        public String name() {
            return "积分抵扣";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            int usable = Math.min(order.points(), maxPoints);
            return perPoint.multiply(BigDecimal.valueOf(usable));
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
            if (this.cap.signum() < 0) {
                throw new IllegalArgumentException("封顶金额不能为负：" + this.cap.toPlainString());
            }
        }

        public String name() {
            return delegate.name() + "（封顶 " + cap.toPlainString() + "）";
        }

        public BigDecimal discount(BigDecimal price, Order order) {
            // 装饰器要把结果拿去 min()，所以必须先校验，不能等链节点 —— null 在这里就会 NPE。
            BigDecimal raw = DiscountPolicy.valid(delegate.discount(price, order), delegate.name());
            return raw.min(cap);
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
            BigDecimal before = DiscountPolicy.valid(delegate.discount(price, order), delegate.name());
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
            // 兑底：任何一个算法都不该把价格减成负数。正常路径上链节点已经校验过一轮，
            // 这里是「用不信任的眼光看待每一个外部输入」的最后一层。
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
            // 门槛只在这里判一次：拿到的是「当前价」，不是订单原价。
            if (!requirement.test(state.order, state.price)) {
                return Decision.skipped(requirement.describe() + " 不满足");
            }
            // 所有策略的返回值统一从这里过一道：null / 负数 / 未对齐到分。
            BigDecimal amount = DiscountPolicy.valid(policy.discount(state.price, state.order), policy.name());
            return Decision.applied(amount);
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
            if (!requirement.test(state.order, state.price)) {
                return Decision.skipped(requirement.describe() + " 不满足");
            }
            // 先算钱、再占用组：算法抛异常时不该把组标记成已用。
            BigDecimal amount = DiscountPolicy.valid(policy.discount(state.price, state.order), policy.name());
            state.usedGroups.add(group);
            return Decision.applied(amount);
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
                BigDecimal value = new BigDecimal(amount);
                if (value.signum() < 0) {
                    throw new IllegalArgumentException("整链封顶不能为负：" + value.toPlainString());
                }
                this.cap = value;
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
                // 「满 100」在条件里，「减 20」在算法里 —— 一个满减券是两个对象，不是两个 if。
                .stage("满 100 减 20", new FlatReduction(new BigDecimal("20")), Requirement.atLeast("100"))
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
        System.out.println("1. 单个算法的上限 → 策略装饰器 Capped（本例让积分最多抵 3 元）");
        System.out.println("2. 整条链的总上限 → 链对象上的约束（本例总减免超 50 就截断）");
        System.out.println("前者可以多个策略各配一个，后者全局只能有一个。搞混就会写出「优惠券各自封顶了，");
        System.out.println("但叠起来还是减了 200」的 bug。");
        System.out.println();

        System.out.println("=== 注意：互斥只挡「同组第二张」，跨组照常叠加 ===");
        System.out.println("会员九折 与 周末八五折 同属「折扣券」组 → 第二张被跳过，见上面的日志。");
        System.out.println("满减、积分 属于其他组 → 正常叠加。");
        System.out.println();

        System.out.println("=== 边界一：满 100 的门槛，比原价还是比当前价 ===");
        Order gap = new Order(new BigDecimal("120.00"), false, false, 0);
        System.out.println("订单 120.00，先立减 30（当前价变 90.00），再问「满 100 减 20」：");
        System.out.println("  按当前价（atLeast）       → " + chainPrice(gap, true).toPlainString());
        System.out.println("  按原价（atLeastOriginal） → " + chainPrice(gap, false).toPlainString());
        System.out.println("同样是「满 100」，两种口径差 20 元。代码不替你选，但签名逼你在装配时写下选哪个。");
        System.out.println();

        System.out.println("=== 边界二：装配顺序不同，结果不同（这是必须让运营看见的事实） ===");
        Order mid = new Order(new BigDecimal("150.00"), true, false, 0);
        System.out.println("订单 150.00，会员，券：满 100 减 20 + 会员九折");
        System.out.println("  满减 → 九折：" + chainOrder(mid, true).toPlainString());
        System.out.println("  九折 → 满减：" + chainOrder(mid, false).toPlainString());
        System.out.println("差的 2 元来自「九折之后还满不满 100」。顺序本身是运营配置项，所以它必须出现在日志里。");
        System.out.println();

        System.out.println("=== 边界三：非法参数必须在装配期炸掉，而不是算出一个怪数 ===");
        reject("折扣比例 1.2（无折扣）", () -> new Percentage(new BigDecimal("1.2")));
        reject("折扣比例 0（白送）", () -> new Percentage(BigDecimal.ZERO));
        reject("负数立减", () -> new FlatReduction(new BigDecimal("-5")));
        reject("负数封顶（单算法）", () -> new Capped(new FlatReduction(BigDecimal.ONE), "-1"));
        reject("负数封顶（整链）", () -> DiscountChain.builder()
                .stage("立减 1", new FlatReduction(BigDecimal.ONE), Requirement.always())
                .cap("-1"));
        reject("负数门槛", () -> Requirement.atLeast("-100"));
        System.out.println("以上全部在构造 / 装配阶段失败，不会流到计算里变成负数或 NaN 式的怪结果。");
        System.out.println();

        System.out.println("=== 边界四：门槛刚好踩线 —— 等号属于「满」 ===");
        Order edge = new Order(new BigDecimal("100.00"), false, false, 0);
        BigDecimal exact = DiscountChain.builder()
                .stage("满 100 减 20", new FlatReduction(new BigDecimal("20")), Requirement.atLeast("100"))
                .build().calculate(edge).price;
        System.out.println("订单 100.00，「满 100 减 20」→ 命中，应付 " + exact.toPlainString());
        System.out.println("  atLeast 写的是 >=，不是 >。边界值算不算「满足」落在 test 的实现里，不再靠注释约定。");
        System.out.println();

        System.out.println("=== 边界五：券面值超过当前价，兑底保证「只减不增、永不为负」 ===");
        Order tiny = new Order(new BigDecimal("30.00"), false, false, 0);
        ChainState floored = DiscountChain.builder()
                .stage("立减 50", new FlatReduction(new BigDecimal("50")), Requirement.always())
                .stage("再立减 60", new FlatReduction(new BigDecimal("60")), Requirement.always())
                .build().calculate(tiny);
        floored.log.forEach(System.out::println);
        System.out.println("  订单 30.00，券面值合计 110.00 → 应付 " + floored.price.toPlainString());
        System.out.println("  兑底在 ChainState.apply：每一步都 reduction.min(price)。价格单调递减，下限 0。");
        System.out.println();

        System.out.println("=== 边界六：两种封顶各管一段 ===");
        BigDecimal afterPoints = DiscountChain.builder()
                .stage("积分抵扣", new Capped(new PointsDeduction(new BigDecimal("0.01"), 500), "3"),
                        Requirement.hasPoints(1))
                .build().calculate(new Order(new BigDecimal("100.00"), false, false, 800)).price;
        System.out.println("积分 800 × 0.01 = 8.00，单算法封顶 3.00 → 实际减 "
                + new BigDecimal("100.00").subtract(afterPoints).toPlainString());
        ChainState overCapped = DiscountChain.builder()
                .stage("立减 100", new FlatReduction(new BigDecimal("100")), Requirement.always())
                .stage("再立减 100", new FlatReduction(new BigDecimal("100")), Requirement.always())
                .cap("50")
                .build().calculate(new Order(new BigDecimal("200.00"), false, false, 0));
        overCapped.log.forEach(System.out::println);
        System.out.println("  订单 200.00，券面值合计 200.00，整单封顶 50 → 应付 " + overCapped.price.toPlainString());
        System.out.println("  Capped 管单个算法，链上的 cap 管总账；两层都在日志里留了痕迹。");
        System.out.println();

        System.out.println("=== 边界七：策略在运行期返回负数，链节点当场拦截 ===");
        DiscountPolicy broken = new DiscountPolicy() {
            public String name() {
                return "坏策略";
            }

            public BigDecimal discount(BigDecimal price, Order order) {
                return BigDecimal.ONE.negate();
            }
        };
        try {
            DiscountChain.builder()
                    .stage("坏策略", broken, Requirement.always())
                    .build().calculate(new Order(BigDecimal.TEN, false, false, 0));
            System.out.println("  未拦截（错误）：负数返回值流进了价格");
        } catch (IllegalStateException e) {
            System.out.println("  拦截 → " + e.getMessage());
        }
        System.out.println("  构造期校验管参数，运行期 valid 管返回值：两层各拦一类错。");
    }

    /** 同一张订单、同一个「满 100」，只换门槛口径，看结果差多少。 */
    private static BigDecimal chainPrice(Order order, boolean byCurrentPrice) {
        Requirement threshold = byCurrentPrice ? Requirement.atLeast("100") : Requirement.atLeastOriginal("100");
        return DiscountChain.builder()
                .stage("立减 30", new FlatReduction(new BigDecimal("30")), Requirement.always())
                .stage("满 100 减 20", new FlatReduction(new BigDecimal("20")), threshold)
                .build()
                .calculate(order)
                .price
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 同一批规则、两种装配顺序各建一条链，验证「顺序敏感」是事实而不是猜测。 */
    private static BigDecimal chainOrder(Order order, boolean reductionFirst) {
        DiscountChain.Builder builder = DiscountChain.builder();
        if (reductionFirst) {
            builder.stage("满 100 减 20", new FlatReduction(new BigDecimal("20")), Requirement.atLeast("100"))
                    .exclusive("会员九折", "折扣券", new Percentage(new BigDecimal("0.9")),
                            Requirement.memberOnly());
        } else {
            builder.exclusive("会员九折", "折扣券", new Percentage(new BigDecimal("0.9")),
                            Requirement.memberOnly())
                    .stage("满 100 减 20", new FlatReduction(new BigDecimal("20")), Requirement.atLeast("100"));
        }
        return builder.build()
                .calculate(order)
                .price
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 期望「构造即失败」的检查：没抛异常才算问题。 */
    private static void reject(String what, Runnable action) {
        try {
            action.run();
            System.out.println("  未拦截（错误）：" + what);
        } catch (IllegalArgumentException e) {
            System.out.println("  拒绝 " + what + " → " + e.getMessage());
        }
    }

    private OrderDiscountSolution() {
    }
}
