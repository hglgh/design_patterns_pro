import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 练习 05 参考实现：下单接口的请求处理管线。
 *
 * <p>运行方式（单文件源模式，不需要先编译）：</p>
 * <pre>java docs/practice/solutions/OrderApiSolution.java</pre>
 *
 * <p>这个练习要回答三个问题：</p>
 * <ol>
 *   <li>什么时候该用责任链，什么时候其实只要顺序调用几个方法？</li>
 *   <li>责任链<b>做不到</b>什么？做完你就会发现「埋点在失败时也要执行」这条需求，
 *       链根本表达不了，必须靠外层 try/finally。</li>
 *   <li>链的顺序为什么是一种<b>隐式契约</b>，以及为什么它是责任链最贵的代价。</li>
 * </ol>
 */
public final class OrderApiSolution {

    // ==================== 一、请求与结果 ====================

    /** 请求上下文。链上每个节点都可能往里写东西（userId、风险分、预占单号）。 */
    static final class OrderRequest {
        final String idempotencyKey;
        final String token;
        String userId;
        String sku;
        int quantity;
        long amountInCents;
        int riskScore;
        String stockReservationNo;
        final List<String> trace = new ArrayList<>();

        OrderRequest(String idempotencyKey, String token, String sku, int quantity, long amountInCents) {
            this.idempotencyKey = idempotencyKey;
            this.token = token;
            this.sku = sku;
            this.quantity = quantity;
            this.amountInCents = amountInCents;
        }

        void note(String message) {
            trace.add(message);
        }
    }

    /** 处理结果。要么放行到下一环，要么带一个错误码短路。 */
    record Outcome(boolean passed, String errorCode, String message) {
        static Outcome pass() {
            return new Outcome(true, null, null);
        }

        static Outcome reject(String errorCode, String message) {
            return new Outcome(false, errorCode, message);
        }
    }

    // ==================== 二、责任链骨架 ====================

    /**
     * 校验节点。
     *
     * <p>接口刻意只有 {@code check}，没有 {@code setNext} —— 链接由
     * {@link Pipeline.Builder} 统一装配。节点自己拼链会让依赖关系散落在
     * 各个类里，谁也说不清最终顺序。</p>
     */
    interface Guard {
        String name();

        Outcome check(OrderRequest request);
    }

    // ==================== 三、五个校验节点 ====================

    /** 幂等：同一个 key 只放过去一次。 */
    static final class IdempotencyGuard implements Guard {
        private final Set<String> seen = new LinkedHashSet<>();

        public String name() {
            return "幂等校验";
        }

        public Outcome check(OrderRequest request) {
            if (!seen.add(request.idempotencyKey)) {
                return Outcome.reject("DUPLICATE_SUBMIT", "重复提交，请勿重试");
            }
            request.note("幂等键 " + request.idempotencyKey + " 首次出现");
            return Outcome.pass();
        }

        /** 真实实现里这里还会在失败时释放 key，否则用户重试就永远被拒。 */
        void release(String key) {
            seen.remove(key);
        }
    }

    /** 登录：token 换成 userId。注意它<b>写</b>上下文，后面的节点依赖这个结果。 */
    static final class AuthGuard implements Guard {
        private final Map<String, String> tokenToUser = Map.of(
                "tok-alice", "U1001",
                "tok-bob", "U1002");

        public String name() {
            return "登录校验";
        }

        public Outcome check(OrderRequest request) {
            String userId = tokenToUser.get(request.token);
            if (userId == null) {
                return Outcome.reject("UNAUTHORIZED", "登录已失效");
            }
            request.userId = userId;
            request.note("识别用户 " + userId);
            return Outcome.pass();
        }
    }

    /** 参数：数量、金额、SKU。纯读，不改上下文。 */
    static final class ValidationGuard implements Guard {
        public String name() {
            return "参数校验";
        }

        public Outcome check(OrderRequest request) {
            if (request.quantity <= 0) {
                return Outcome.reject("INVALID_QUANTITY", "购买数量必须大于 0");
            }
            if (request.amountInCents <= 0) {
                return Outcome.reject("INVALID_AMOUNT", "订单金额必须大于 0");
            }
            if (request.sku == null || request.sku.isBlank()) {
                return Outcome.reject("INVALID_SKU", "商品编号不能为空");
            }
            return Outcome.pass();
        }
    }

    /**
     * 风控：需要 userId，所以<b>必须</b>排在 {@link AuthGuard} 之后。
     *
     * <p>这就是责任链的隐式契约：节点之间靠上下文传递数据，
     * 而「谁先谁后」只存在于装配代码里。顺序写错不会编译报错，
     * 只会在线上表现为「风控永远放行」（因为 userId 是 null 时直接跳过）。</p>
     */
    static final class RiskGuard implements Guard {
        private final int threshold;

        RiskGuard(int threshold) {
            this.threshold = threshold;
        }

        public String name() {
            return "风控";
        }

        public Outcome check(OrderRequest request) {
            if (request.userId == null) {
                // 防御式写法：宁可拒绝也不能默默放行。真实项目里这里常被写成 return pass()
                return Outcome.reject("RISK_NO_USER", "风控无法识别用户，拒绝交易");
            }
            request.riskScore = score(request);
            if (request.riskScore >= threshold) {
                return Outcome.reject("RISK_REJECTED", "风险分 " + request.riskScore + " 超过阈值");
            }
            request.note("风险分 " + request.riskScore + "，阈值 " + threshold);
            return Outcome.pass();
        }

        private static int score(OrderRequest request) {
            int score = 0;
            if (request.amountInCents > 500_00) {
                score += 40;
            }
            if (request.quantity > 10) {
                score += 30;
            }
            if ("SKU-HOT".equals(request.sku)) {
                score += 35;
            }
            return score;
        }
    }

    /**
     * 库存预占：<b>有副作用</b>，所以它必须知道自己可能被回滚。
     *
     * <p>链上节点分为两类：</p>
     * <ul>
     *   <li>纯校验（幂等、登录、参数、风控）—— 失败就失败，没有痕迹；</li>
     *   <li>有副作用（库存预占、优惠券锁定）—— 失败后必须补偿。</li>
     * </ul>
     * <p>把两类混在一条链里，就会出现「后面的节点拒绝了，前面的预占没释放」。
     * 正确做法是链只放纯校验，副作用放到 {@link OrderService} 里由它统一补偿。</p>
     */
    static final class StockGuard implements Guard {
        private final Map<String, Integer> stock = new LinkedHashMap<>(Map.of(
                "SKU-BOOK", 10,
                "SKU-HOT", 0));
        private int reservationSeq = 5000;

        public String name() {
            return "库存预占";
        }

        public Outcome check(OrderRequest request) {
            int available = stock.getOrDefault(request.sku, 0);
            if (available < request.quantity) {
                return Outcome.reject("OUT_OF_STOCK", request.sku + " 库存不足，剩 " + available);
            }
            stock.put(request.sku, available - request.quantity);
            request.stockReservationNo = "RSV" + (++reservationSeq);
            request.note("预占 " + request.quantity + " 件，单号 " + request.stockReservationNo);
            return Outcome.pass();
        }

        void rollback(OrderRequest request) {
            if (request.stockReservationNo != null) {
                stock.merge(request.sku, request.quantity, Integer::sum);
                request.note("回滚预占 " + request.stockReservationNo);
            }
        }

        int remaining(String sku) {
            return stock.getOrDefault(sku, 0);
        }
    }

    // ==================== 四、管线：链 + 外层 finally ====================

    /**
     * 一条校验链。
     *
     * <p>{@link #run} 不是简单的「跑完节点」，它还负责：</p>
     * <ol>
     *   <li>短路：任一节点拒绝就停，后续节点不执行；</li>
     *   <li>埋点：<b>无论成功失败</b>都要落一条审计日志 —— 这是链表达不了的，
     *       因为链失败时会跳过所有后续节点，包括"埋点节点"。</li>
     * </ol>
     */
    static final class Pipeline {
        private final List<Guard> guards;
        private final List<String> auditLog = new ArrayList<>();

        private Pipeline(List<Guard> guards) {
            this.guards = List.copyOf(guards);
        }

        static Builder builder() {
            return new Builder();
        }

        /** 只跑校验，不做业务。返回第一个拒绝的结论，全通则 pass。 */
        Outcome run(OrderRequest request) {
            try {
                for (Guard guard : guards) {
                    Outcome outcome = guard.check(request);
                    if (!outcome.passed()) {
                        return outcome;
                    }
                }
                return Outcome.pass();
            } finally {
                // 关键：埋点在外层，所以短路也照写
                auditLog.add(request.idempotencyKey + " | 链路 " + guardNames() + " | trace=" + request.trace);
            }
        }

        List<String> guardNames() {
            List<String> names = new ArrayList<>();
            for (Guard guard : guards) {
                names.add(guard.name());
            }
            return names;
        }

        List<String> auditLog() {
            return auditLog;
        }

        static final class Builder {
            private final List<Guard> guards = new ArrayList<>();

            Builder add(Guard guard) {
                guards.add(guard);
                return this;
            }

            Pipeline build() {
                if (guards.isEmpty()) {
                    throw new IllegalStateException("管线不能为空");
                }
                return new Pipeline(guards);
            }
        }
    }

    // ==================== 五、业务层：统一补偿 ====================

    /**
     * 下单服务。
     *
     * <p>它做三件事，顺序不能换：校验 → 业务 → 补偿。</p>
     *
     * <p>为什么补偿放在这里而不是链里：补偿的对象是<b>已经发生的副作用</b>，
     * 而副作用应该只有业务层知道。链只负责"能不能做"，不负责"做失败了怎么办"。</p>
     */
    static final class OrderService {
        private final Pipeline pipeline;
        private final StockGuard stock;
        private int orderSeq = 9000;

        OrderService(Pipeline pipeline, StockGuard stock) {
            this.pipeline = pipeline;
            this.stock = stock;
        }

        /** 返回「订单号」或 null（失败时 request.trace 里有原因）。 */
        String create(OrderRequest request, StringBuilder reason) {
            Outcome outcome = pipeline.run(request);
            if (!outcome.passed()) {
                reason.append(outcome.errorCode()).append(" ").append(outcome.message());
                return null;
            }
            try {
                String orderNo = "SO" + (++orderSeq);
                request.note("创建订单 " + orderNo);
                return orderNo;
            } catch (RuntimeException e) {
                stock.rollback(request);
                throw e;
            }
        }

        Pipeline pipeline() {
            return pipeline;
        }
    }

    // ==================== 六、跑一遍 ====================

    public static void main(String[] args) {
        // ---------------- 场景 1：正常下单 ----------------
        System.out.println("=== 场景 1：正常下单 ===");
        StockGuard stock = new StockGuard();
        IdempotencyGuard idem = new IdempotencyGuard();
        Pipeline pipeline = Pipeline.builder()
                .add(idem)
                .add(new AuthGuard())
                .add(new ValidationGuard())
                .add(new RiskGuard(50))
                .add(stock)
                .build();
        System.out.println("链路顺序：" + pipeline.guardNames());

        OrderService service = new OrderService(pipeline, stock);
        OrderRequest ok = new OrderRequest("key-1", "tok-alice", "SKU-BOOK", 2, 128_00);
        StringBuilder reason = new StringBuilder();
        String orderNo = service.create(ok, reason);
        System.out.println("结果：" + orderNo);
        ok.trace.forEach(line -> System.out.println("  " + line));
        System.out.println("审计条数：" + pipeline.auditLog().size() + "（短路也会写）");
        System.out.println();

        // ---------------- 场景 2：中途被拒，短路 ----------------
        System.out.println("=== 场景 2：风控拒绝，后续节点不再执行 ===");
        StockGuard stock2 = new StockGuard();
        Pipeline pipeline2 = Pipeline.builder()
                .add(new IdempotencyGuard())
                .add(new AuthGuard())
                .add(new ValidationGuard())
                .add(new RiskGuard(50))
                .add(stock2)
                .build();
        OrderService service2 = new OrderService(pipeline2, stock2);
        OrderRequest risky = new OrderRequest("key-2", "tok-alice", "SKU-HOT", 12, 600_00);
        reason.setLength(0);
        System.out.println("结果：" + service2.create(risky, reason) + "（" + reason + "）");
        risky.trace.forEach(line -> System.out.println("  " + line));
        System.out.println("注意：库存预占没有出现在 trace 里 —— 短路生效了。");
        System.out.println("注意：审计日志照样有 1 条 —— finally 生效了。");
        System.out.println();

        // ---------------- 场景 3：顺序错了会怎样 ----------------
        System.out.println("=== 场景 3：把风控排到登录之前 ===");
        Pipeline wrongOrder = Pipeline.builder()
                .add(new IdempotencyGuard())
                .add(new RiskGuard(50))     // ← 提前了
                .add(new AuthGuard())
                .add(new ValidationGuard())
                .build();
        System.out.println("链路顺序：" + wrongOrder.guardNames());
        OrderRequest anyOrder = new OrderRequest("key-3", "tok-bob", "SKU-BOOK", 1, 50_00);
        Outcome wrong = wrongOrder.run(anyOrder);
        System.out.println("结果：" + wrong.errorCode() + " " + wrong.message());
        System.out.println("风控本来只是想拦截高风险，结果把所有正常请求也拒了。");
        System.out.println("而编译器不会提示任何东西 —— 这就是责任链最贵的代价：");
        System.out.println("顺序是隐式契约，只存在于装配代码里，换个人重排就出事。");
        System.out.println();

        // ---------------- 场景 4：幂等 ----------------
        System.out.println("=== 场景 4：重复提交 ===");
        StockGuard stock4 = new StockGuard();
        IdempotencyGuard idem4 = new IdempotencyGuard();
        Pipeline pipeline4 = Pipeline.builder()
                .add(idem4)
                .add(new AuthGuard())
                .add(new ValidationGuard())
                .add(stock4)
                .build();
        OrderService service4 = new OrderService(pipeline4, stock4);
        OrderRequest first = new OrderRequest("key-4", "tok-alice", "SKU-BOOK", 1, 10_00);
        OrderRequest second = new OrderRequest("key-4", "tok-alice", "SKU-BOOK", 1, 10_00);
        reason.setLength(0);
        System.out.println("第一次：" + service4.create(first, reason));
        reason.setLength(0);
        System.out.println("第二次：" + service4.create(second, reason) + "（" + reason + "）");
        System.out.println("剩余库存：" + stock4.remaining("SKU-BOOK")
                + "（第一次扣了 1 件，第二次没扣）");
        System.out.println();

        // ---------------- 场景 5：副作用要能回滚 ----------------
        System.out.println("=== 场景 5：业务层抛异常，预占必须回滚 ===");
        StockGuard stock5 = new StockGuard();
        Pipeline pipeline5 = Pipeline.builder()
                .add(new IdempotencyGuard())
                .add(new AuthGuard())
                .add(new ValidationGuard())
                .add(stock5)
                .build();
        OrderRequest boom = new OrderRequest("key-5", "tok-alice", "SKU-BOOK", 3, 30_00);
        Outcome pre = pipeline5.run(boom);
        System.out.println("校验通过：" + pre.passed() + "，预占单号 " + boom.stockReservationNo);
        System.out.println("剩余库存：" + stock5.remaining("SKU-BOOK") + "（已扣 3 件）");
        // 真实场景：接下来写订单表时抛了异常，或者下游超时
        stock5.rollback(boom);
        System.out.println("调用 rollback 后剩余：" + stock5.remaining("SKU-BOOK") + "（已还回）");
        System.out.println();
        System.out.println("注意：rollback 的知识在 StockGuard 里，但调用它的位置在 OrderService。");
        System.out.println("链只回答「能不能做」，不回答「做失败了怎么办」—— 后者是业务层的职责。");
        System.out.println();

        // ---------------- 收尾：什么时候不该用链 ----------------
        System.out.println("=== 什么时候「不该」用责任链 ===");
        System.out.println("如果这三步永远固定、永远不会增删、顺序也永远不变：");
        System.out.println("  1. 判断用户是否登录；");
        System.out.println("  2. 判断余额是否足够；");
        System.out.println("  3. 扣款。");
        System.out.println("那么写三个方法顺序调用就够了 —— 链路带来的只有一层间接、");
        System.out.println("一份装配代码，以及「顺序被别人重排」的风险。");
        System.out.println("责任链真正值的场合是：节点集合会变（网关加中间件、");
        System.out.println("审批加节点），或者不同业务走不同子集（A 接口要风控，B 接口不要）。");
        System.out.println();

        System.out.println("=== 为什么这里不用装饰器 ===");
        System.out.println("装饰器必须调用被装饰者，语义是「前后加料，核心一定要跑」。");
        System.out.println("校验的语义是「不通过就停」，所以只能短路 —— 这是链不是装饰器。");
        System.out.println("一个简单判据：如果每个环节都必须执行，用装饰器；");
        System.out.println("如果有环节能终止整个流程，用链。");
    }

    private OrderApiSolution() {
    }
}
