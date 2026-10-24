package com.patterns.combined.apigateway;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 演示入口：<b>一个 API 网关，六个模式，十段场景</b>。
 *
 * <p>组合案例最容易讲成「六个模式的名词堆在一起」。所以每一段 Demo 都遵循同一个结构：</p>
 * <ol>
 *   <li>先说要证明什么（一句话）；</li>
 *   <li>发真实请求，打印<b>响应 + 真实打到上游的次数 + 逐步轨迹</b>；</li>
 *   <li>最后一行点明「这条轨迹里哪个模式起了作用」。</li>
 * </ol>
 *
 * <p><b>为什么一定要打印「轨迹」而不是只打印响应？</b>
 * 因为响应会骗人：一个 200 可能是缓存给的、可能是重试才拿到的、也可能是上游第一把就成功。
 * 三者对系统的影响完全不同（缓存命中不消耗上游容量），但响应体一模一样。
 * 只有轨迹能区分它们——<b>这正是可观测性的意义</b>。</p>
 *
 * <p><b>为什么所有时间都是「tick」而不是毫秒？</b>
 * 因为本项目的所有输出都要求两次运行逐字节一致。真实实现用
 * {@code System.nanoTime()}，这里用 {@link Gateway#advance(long)} 手动推进逻辑时钟，
 * 把「等 3 秒」压缩成「推进 3 个 tick」。</p>
 *
 * <p>运行方式（Windows，JDK 25）：</p>
 * <pre>{@code
 * javac --release 25 -encoding UTF-8 -Xlint:all -sourcepath src -d out src\com\patterns\combined\apigateway\*.java
 * java -Dstdout.encoding=UTF-8 -cp out com.patterns.combined.apigateway.Client
 * }</pre>
 *
 * @author design-patterns-pro
 */
public final class Client {

    private static final String TOKEN_1001 = "Bearer t-1001";
    private static final String TOKEN_1002 = "Bearer t-1002";

    private Client() {
    }

    /**
     * 程序入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        // 一个网关实例贯穿 Demo 1-6：它的缓存、限流计数、熔断器状态会持续累积，
        // 这正是「网关是有状态的中间件」这一事实的体现。
        Gateway gateway = Gateway.createDefault(Map.of("order-2", 1));

        MetricsCollector metrics = new MetricsCollector();
        AlertListener alerts = new AlertListener(2);
        gateway.addListener(metrics);
        gateway.addListener(alerts);

        demo1NormalRequest(gateway);
        demo2CacheHit(gateway);
        demo3CacheIsolation(gateway);
        demo4PostNotCached(gateway);
        demo5AuthBranches(gateway);
        demo6RateLimit(gateway);
        demo7BalancerComparison();
        demo8CircuitBreaker();
        demo9Observer();
        demo10Summary(gateway, metrics, alerts);
    }

    // ==================================================================
    // Demo 1：一条请求，六个模式全部参与
    // ==================================================================

    /**
     * 正常 GET 请求：从认证一路走到上游，再回填缓存。
     *
     * @param gateway 主网关
     */
    private static void demo1NormalRequest(Gateway gateway) {
        section("Demo 1 正常请求：一条请求把六个模式全走一遍");
        System.out.println("  完整拓扑（过滤器链 + 装饰器栈）：");
        System.out.println("    " + gateway.describeTopology());
        System.out.println();

        Gateway.Result result = gateway.handle(
                new GatewayRequest("r-1", "GET", "/api/order", TOKEN_1001, "10.0.0.1"));
        System.out.println("  请求：" + result.context().request().describe());
        printResult(result);
        System.out.println("  熔断器：" + gateway.breaker().describeStates());
        System.out.println();
        System.out.println("  六个模式的分工：");
        System.out.println("    责任链 — auth / rate / router / cache 依次放行");
        System.out.println("    策略   — router 里用 least-connections 选中 order-1");
        System.out.println("    装饰器 — Metrics(Retry(CircuitBreaker(Service))) 逐层包裹");
        System.out.println("    状态   — 熔断器此刻处于 CLOSED，放行并开始数连续失败");
        System.out.println("    享元   — order:v1 的路由配置在池里只创建一次，被所有请求共享");
        System.out.println("    观察者 — 请求结束后把 Event 广播给指标与告警两个订阅者");
    }

    // ==================================================================
    // Demo 2：同一个用户再打一次 —— 缓存命中，上游零调用
    // ==================================================================

    /**
     * 同一主体、同一路径的第二次请求，应由缓存直接返回。
     *
     * @param gateway 主网关
     */
    private static void demo2CacheHit(Gateway gateway) {
        section("Demo 2 缓存命中：同样的请求，上游一次都没被打");
        Gateway.Result result = gateway.handle(
                new GatewayRequest("r-2", "GET", "/api/order", TOKEN_1001, "10.0.0.1"));
        printResult(result);
        System.out.println();
        System.out.println("  注意到轨迹在 cache 这一层就结束了：route 已经解析出来，");
        System.out.println("  但装饰器栈（含熔断器、重试、上游）一次都没有进入，attempts 为 0。");
        System.out.println("  这就是「短路」——后面的环节根本不执行，不是「执行了但返回缓存」。");
    }

    // ==================================================================
    // Demo 3：换一个用户打同一路径 —— 不许命中别人的缓存
    // ==================================================================

    /**
     * 换主体打同一路径，用来证明缓存 key 里含有身份。
     *
     * @param gateway 主网关
     */
    private static void demo3CacheIsolation(Gateway gateway) {
        section("Demo 3 缓存隔离：换个用户打同一路径，绝不能命中");
        Gateway.Result result = gateway.handle(
                new GatewayRequest("r-3", "GET", "/api/order", TOKEN_1002, "10.0.0.3"));
        printResult(result);
        System.out.println();
        System.out.println("  如果缓存 key 只写 path，r-3 会拿到 r-1 的 data#1 ——");
        System.out.println("  用户 B 看到用户 A 的订单。这类漏洞在真实系统里叫「缓存越权」，");
        System.out.println("  根因几乎都是「共享了公共缓存，却忘了 key 里要区分调用方」。");
    }

    // ==================================================================
    // Demo 4：POST 不缓存 —— 第二次下单必须真的下单
    // ==================================================================

    /**
     * 同一个 POST 打两次，两次都必须真的打到上游。
     *
     * @param gateway 主网关
     */
    private static void demo4PostNotCached(Gateway gateway) {
        section("Demo 4 POST 不缓存：打两次，就真的下两次单");
        Gateway.Result first = gateway.handle(
                new GatewayRequest("r-4", "POST", "/api/pay", TOKEN_1001, "10.0.0.4"));
        printResult(first);
        System.out.println();
        Gateway.Result second = gateway.handle(
                new GatewayRequest("r-5", "POST", "/api/pay", TOKEN_1001, "10.0.0.4"));
        printResult(second);
        System.out.println();
        System.out.println("  上游被调用到第 2 次（pay-1 第 1 次 / 第 2 次），说明 POST 没有被缓存短路。");
        System.out.println("  HTTP 语义里 GET 是幂等的、POST 不是；把 POST 缓存住等于");
        System.out.println("  「第二次付款没有真的付款」，这是语义级错误，不是性能问题。");
    }

    // ==================================================================
    // Demo 5：401 的三个分支 —— 而且不消耗任何人的限流配额
    // ==================================================================

    /**
     * 三个认证失败分支，并证明它们没有消耗限流配额。
     *
     * @param gateway 主网关
     */
    private static void demo5AuthBranches(Gateway gateway) {
        section("Demo 5 认证三分支：401 必须短路，且不该消耗配额");
        gateway.rollRateWindow();
        System.out.println("  401 之前的限流快照：" + gateway.rateLimit().snapshot());
        System.out.println();

        Gateway.Result missing = gateway.handle(
                new GatewayRequest("r-6", "GET", "/api/order", null, "10.0.0.5"));
        System.out.println("  [1] 没有 Authorization 头");
        printResult(missing);
        System.out.println();

        Gateway.Result expired = gateway.handle(
                new GatewayRequest("r-7", "GET", "/api/order", "Bearer expired", "10.0.0.5"));
        System.out.println("  [2] token 已过期");
        printResult(expired);
        System.out.println();

        Gateway.Result forged = gateway.handle(
                new GatewayRequest("r-8", "GET", "/api/order", "Bearer t-9999", "10.0.0.5"));
        System.out.println("  [3] 未知 token");
        printResult(forged);
        System.out.println();

        System.out.println("  401 之后的限流快照：" + gateway.rateLimit().snapshot());
        System.out.println("  快照里没有 10.0.0.5 —— 三个 401 一次配额都没消耗，");
        System.out.println("  因为它们都在 auth 这一环就返回了，rate 根本没被执行。");
        System.out.println("  三种 401 在线上必须打不同标签：客户端的处理动作完全不同");
        System.out.println("  （补头 / 刷新 token / 疑似伪造并告警）。");
    }

    // ==================================================================
    // Demo 6：限流在缓存之前 —— 命中缓存也算一次请求
    // ==================================================================

    /**
     * 同一 IP 连打 4 次（配额 3），第 4 次被 429；换窗口后恢复。
     *
     * @param gateway 主网关
     */
    private static void demo6RateLimit(Gateway gateway) {
        section("Demo 6 限流在缓存之前：命中缓存也算一次请求");
        gateway.rollRateWindow();
        String ip = "10.0.0.6";
        System.out.println("  配额：" + gateway.rateLimit().capacity() + " 次 / 窗口，客户端 IP：" + ip);
        System.out.println();
        for (int i = 0; i < 4; i++) {
            Gateway.Result result = gateway.handle(
                    new GatewayRequest("r-" + (9 + i), "GET", "/api/user", TOKEN_1002, ip));
            System.out.println("  第 " + (i + 1) + " 次 → " + result.event().describe());
        }
        System.out.println();
        System.out.println("  第 2、3 次是缓存命中（attempts=0），但它们仍然占用了配额。");
        System.out.println("  如果缓存排在限流前面，攻击者只要反复请求同一个热门路径就能无限打穿网关；");
        System.out.println("  而且每次都很快——「便宜的操作」恰恰不能当作「免检通道」。");
        System.out.println();
        System.out.println("  快照：" + gateway.rateLimit().snapshot()
                + "（计数涨到 4，但只有前 3 次被放行——超限请求也会被计入）");
        System.out.println();
        gateway.rollRateWindow();
        System.out.println("  --- rollWindow()：进入下一个固定窗口 ---");
        Gateway.Result afterRoll = gateway.handle(
                new GatewayRequest("r-13", "GET", "/api/user", TOKEN_1002, ip));
        System.out.println("  第 5 次 → " + afterRoll.event().describe());
        System.out.println("  新窗口的配额重新从 1 开始，缓存依然命中，所以 attempts 仍是 0。");
    }

   // ==================================================================
    // Demo 7：同一份故障脚本，换一个选路策略，结果完全不同
    // ==================================================================

    /**
     * 轮询 vs 最少连接：同样两台实例、同样的故障脚本，行为不一样。
     */
    private static void demo7BalancerComparison() {
        section("Demo 7 策略对比：同样的实例和故障，换个选路算法结果就变了");
        System.out.println("  故障脚本：order-2 的前 1 次调用返回 503，order-1 完全正常。");
        System.out.println();

        Gateway roundRobin = new Gateway(LoadBalancer.roundRobin(), Map.of("order-2", 1), 2, 2, 3, 3);
        Gateway leastConn = new Gateway(LoadBalancer.leastConnections(), Map.of("order-2", 1), 2, 2, 3, 3);

        System.out.println("  [轮询] round-robin：第 1 台 order-1，第 2 台 order-2");
        Gateway.Result rr1 = roundRobin.handle(
                new GatewayRequest("rr-1", "GET", "/api/order", TOKEN_1001, "10.1.0.1"));
        System.out.println("  第 1 次 → " + rr1.event().describe());
        Gateway.Result rr2 = roundRobin.handle(
                new GatewayRequest("rr-2", "GET", "/api/order", TOKEN_1002, "10.1.0.2"));
        System.out.println("  第 2 次 → " + rr2.event().describe());
        printResult(rr2);
        System.out.println("  轮询网关：order-1 被调用 " + roundRobin.service().callCount("order-1")
                + " 次，order-2 被调用 " + roundRobin.service().callCount("order-2") + " 次");
        System.out.println("  → 轮询不知道机器坏没坏，它只负责平均分配；这一次是重试救了它。");
        System.out.println();

        System.out.println("  [最少连接] least-connections：同样的两个请求");
        Gateway.Result lc1 = leastConn.handle(
                new GatewayRequest("lc-1", "GET", "/api/order", TOKEN_1001, "10.1.1.1"));
        System.out.println("  第 1 次 → " + lc1.event().describe());
        Gateway.Result lc2 = leastConn.handle(
                new GatewayRequest("lc-2", "GET", "/api/order", TOKEN_1002, "10.1.1.2"));
        System.out.println("  第 2 次 → " + lc2.event().describe());
        System.out.println("  最少连接网关：order-1 被调用 " + leastConn.service().callCount("order-1")
                + " 次，order-2 被调用 " + leastConn.service().callCount("order-2") + " 次");
        System.out.println();
        System.out.println("  ⚠ 这里必须说实话：最少连接之所以等于「永远选第一台」，是因为本 Demo");
        System.out.println("    是单线程的，每次选路时 inFlight 都已经归零。真实网关里请求会重叠，");
        System.out.println("    第一台的 inFlight 涨到 5 时，选路会自动跳到 inFlight 为 0 的第二台。");
        System.out.println("    换句话说：这个「退化」是演示环境的产物，不是算法的缺陷；");
        System.out.println("    但如果你在线上看到「最少连接只打一台」，先怀疑的是 inFlight 没被正确释放。");
    }

    // ==================================================================
    // Demo 8：熔断器的三态循环（关掉重试，才看得清状态机自己）
    // ==================================================================

    /**
     * 完整演练 CLOSED → OPEN → HALF_OPEN → OPEN → HALF_OPEN → CLOSED。
     */
    private static void demo8CircuitBreaker() {
        section("Demo 8 熔断三态：CLOSED → OPEN → HALF_OPEN → OPEN → HALF_OPEN → CLOSED");
        // maxAttempts = 1：关掉重试。重试会把失败「藏起来」，让人看不清熔断器自己的状态机。
        Gateway cb = new Gateway(LoadBalancer.leastConnections(), Map.of("report-1", 3), 1, 2, 3, 3);
        System.out.println("  配置：/api/report 只有 report-1 一台，关闭重试（maxAttempts=1），");
        System.out.println("        连续失败 2 次打开熔断，冷却 3 tick；故障脚本：report-1 前 3 次调用失败。");
        System.out.println();

        System.out.println("  [tick " + cb.breaker().now() + "] c-1 第一次失败（未到阈值 2）");
        printResult(cb.handle(new GatewayRequest("c-1", "GET", "/api/report", TOKEN_1001, "10.2.0.1")));
        printBreaker(cb);
        System.out.println();

        System.out.println("  [tick " + cb.breaker().now() + "] c-2 第二次连续失败 → 达到阈值");
        printResult(cb.handle(new GatewayRequest("c-2", "GET", "/api/report", TOKEN_1001, "10.2.0.2")));
        printBreaker(cb);
        System.out.println();

        System.out.println("  [tick " + cb.breaker().now() + "] c-3 熔断器打开期间的请求");
        printResult(cb.handle(new GatewayRequest("c-3", "GET", "/api/report", TOKEN_1001, "10.2.0.3")));
        System.out.println("  ↑ 注意这两行：servedBy 是 gateway（不是 report-1），attempts 是 0。");
        System.out.println("    这个 503 是网关自己拒的，不是上游吐的——排查时必须能分清。");
        printBreaker(cb);
        System.out.println();

        cb.advance(3);
        System.out.println("  --- advance(3)：逻辑时钟推进到 tick " + cb.breaker().now() + "，冷却结束 ---");
        System.out.println("  [tick " + cb.breaker().now() + "] c-4 半开状态放行一次探路，但上游仍然失败");
        printResult(cb.handle(new GatewayRequest("c-4", "GET", "/api/report", TOKEN_1001, "10.2.0.4")));
        printBreaker(cb);
        System.out.println();

        cb.advance(3);
        System.out.println("  --- advance(3)：逻辑时钟推进到 tick " + cb.breaker().now() + " ---");
        System.out.println("  [tick " + cb.breaker().now() + "] c-5 再次半开探路，这次上游恢复");
        printResult(cb.handle(new GatewayRequest("c-5", "GET", "/api/report", TOKEN_1001, "10.2.0.5")));
        printBreaker(cb);
        System.out.println();
        System.out.println("  上游真实被调用 " + cb.service().callCount("report-1") + " 次，而网关收到了 5 次请求：");
        System.out.println("  第 3 次被熔断器挡在门外，一次上游都没打——这就是熔断和重试的分工：");
        System.out.println("  重试是「再试一次」，熔断是「别再试了，先让下游喘口气」。");
    }

    /**
     * 打印 report-1 的熔断器状态。
     *
     * @param gateway 网关
     */
    private static void printBreaker(Gateway gateway) {
        CircuitBreaker breaker = gateway.breaker().breakerOf("report-1");
        System.out.println("  熔断器：report-1=" + breaker.stateName()
                + "，连续失败 " + breaker.consecutiveFailures()
                + "，切换 " + breaker.transitionCount() + " 次");
    }

    // ==================================================================
    // Demo 9：观察者 —— 指标看分布，告警看连续性
    // ==================================================================

    /**
     * 独立的「故障网关」，用来看指标与告警两个订阅者各自看到什么。
     */
    private static void demo9Observer() {
        section("Demo 9 观察者：同一批事件，指标看分布，告警看连续性");
        Gateway chaos = new Gateway(LoadBalancer.leastConnections(),
                Map.of("report-1", 3, "order-1", 1), 1, 2, 3, 3);
        MetricsCollector metrics = new MetricsCollector();
        AlertListener alerts = new AlertListener(2);
        chaos.addListener(metrics);
        chaos.addListener(alerts);

        System.out.println("  故障脚本：report-1 前 3 次失败、order-1 前 1 次失败；关闭重试。");
        System.out.println("  每次用不同的客户端 IP，避开限流，让注意力集中在观察者上。");
        System.out.println();
        String[] ips = {"10.3.0.1", "10.3.0.2", "10.3.0.3", "10.3.0.4", "10.3.0.5"};
        String[] paths = {"/api/report", "/api/report", "/api/report", "/api/order", "/api/order"};
        for (int i = 0; i < paths.length; i++) {
            Gateway.Result result = chaos.handle(
                    new GatewayRequest("o-" + (i + 1), "GET", paths[i], TOKEN_1001, ips[i]));
            System.out.println("  " + result.event().describe());
        }
        System.out.println();
        System.out.println("  指标订阅者看到的是「分布」：");
        System.out.println("    总请求 " + metrics.total() + "，成功 " + metrics.success()
                + "，失败 " + metrics.failed()
                + "，成功率 " + String.format(Locale.ROOT, "%.1f%%", metrics.successRate()));
        System.out.println("    状态码分布 " + metrics.statusCounts());
        System.out.println("    谁产生的响应 " + metrics.servedByCounts());
        System.out.println();
        System.out.println("  告警订阅者看到的是「连续性」（阈值 " + alerts.streakThreshold()
                + " 次，触发后归零以免刷屏）：");
        List<String> fired = alerts.alerts();
        for (String alert : fired) {
            System.out.println("    " + alert);
        }
        System.out.println("    共 " + fired.size() + " 条告警，历史最长连续失败 " + alerts.maxStreak() + " 次");
        System.out.println();
        System.out.println("  两个订阅者消费的是同一批 Event，但关注点完全不同：");
        System.out.println("  指标要「全量分布」（加一个图表不用改网关），告警要「异常连续性」");
        System.out.println("  （总量阈值会被流量放大，连续失败才说明现在真的出问题了）。");
    }

    // ==================================================================
    // Demo 10：汇总 —— 13 个请求之后，网关内部留下了什么
    // ==================================================================

    /**
     * 打印主网关在 Demo 1-6 之后累积下来的全部状态。
     *
     * @param gateway 主网关
     * @param metrics 指标订阅者
     * @param alerts  告警订阅者
     */
    private static void demo10Summary(Gateway gateway, MetricsCollector metrics, AlertListener alerts) {
        section("Demo 10 汇总：13 个请求之后，网关内部留下了什么");
        System.out.println("  已处理请求          " + gateway.served());
        System.out.println("  进入上游层          " + gateway.metrics().calls()
                + " 次（缓存命中 / 401 / 429 都不会进来）");
        System.out.println("  享元池              " + gateway.routeTable().describePool());
        System.out.println("  缓存                " + gateway.cache().describeStats());
        System.out.println("  熔断器              " + gateway.breaker().describeStates());
        System.out.println("  限流快照            " + gateway.rateLimit().snapshot());
        System.out.println("  上游真实调用        order-1=" + gateway.service().callCount("order-1")
                + "，order-2=" + gateway.service().callCount("order-2")
                + "，pay-1=" + gateway.service().callCount("pay-1")
                + "，user-1=" + gateway.service().callCount("user-1"));
        System.out.println();
        System.out.println("  指标订阅者：总 " + metrics.total() + "，成功 " + metrics.success()
                + "，失败 " + metrics.failed()
                + "，成功率 " + String.format(Locale.ROOT, "%.1f%%", metrics.successRate())
                + "，缓存命中 " + metrics.cacheHits());
        System.out.println("  状态码分布  " + metrics.statusCounts());
        System.out.println("  响应来源    " + metrics.servedByCounts());
        System.out.println();
        System.out.println("  告警订阅者：" + alerts.alerts().size() + " 条");
        for (String alert : alerts.alerts()) {
            System.out.println("    " + alert);
        }
        System.out.println();
        System.out.println("  ⚠ 这条告警是「噪声」的活标本：它由 r-6 / r-7 的 401 触发。");
        System.out.println("    401 是调用方的问题（没带 token），不是系统故障，不该进告警通道。");
        System.out.println("    真实告警的判定必须写成「5xx 数量 / 错误率突增」，而不是「非 2xx 就算失败」。");
        System.out.println("    这也是本 Demo 故意留下的瑕疵：把「失败」简化成「非 2xx」够用，但不正确。");
        System.out.println();
        System.out.println("  ⚠ order-2 的真实调用次数是 0：最少连接在单线程 Demo 里始终选中第一台，");
        System.out.println("    所以那份「order-2 失败 1 次」的故障脚本到最后也没被消耗。");
        System.out.println();
        System.out.println("  六个模式各自负责的那一小块：");
        System.out.println("    责任链  决定「能不能进来」：auth → rate → router → cache，任一环可短路");
        System.out.println("    策略    决定「进来后打哪台」：LoadBalancer 可换，RouterFilter 不知道算法细节");
        System.out.println("    装饰器  决定「打的时候怎么保护」：Metrics(Retry(CircuitBreaker(Service)))");
        System.out.println("    状态    决定「熔断器此刻能不能放行」：三个状态类各自回答三个问题");
        System.out.println("    享元    决定「共享什么」：一张路由配置被 13 个请求复用，只创建了 3 次");
        System.out.println("    观察者  决定「谁来消费结果」：新增一个订阅者不需要改网关一行代码");
    }

    /**
     * 打印一次调用的完整结果：响应、真实上游次数、逐步轨迹。
     *
     * @param result 调用结果
     */
    private static void printResult(Gateway.Result result) {
        System.out.println("  响应：" + result.response().describe());
        String cacheNote = result.context().cacheHit() ? "（缓存命中，未打上游）" : "";
        System.out.println("  真实打到上游：" + result.context().attempts() + " 次" + cacheNote);
        System.out.println("  轨迹：");
        List<String> trace = result.context().trace();
        for (int i = 0; i < trace.size(); i++) {
            System.out.println("    " + (i + 1) + ". " + trace.get(i));
        }
    }

    /**
     * 打印段落标题。
     *
     * @param title 标题
     */
    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(96));
        System.out.println(title);
        System.out.println("=".repeat(96));
    }
}
