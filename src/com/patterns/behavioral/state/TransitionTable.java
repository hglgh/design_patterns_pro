package com.patterns.behavioral.state;

import com.patterns.util.DisplayWidth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 状态转移表：同一套规则的<b>另一种写法</b>，用来回答"什么时候不该用状态模式"。
 *
 * <h2>它是什么</h2>
 *
 * <p>把"在什么状态下、哪个动作能到哪个状态"写成一张表：
 * <pre>
 *   待支付 + 支付   → 已支付
 *   待支付 + 取消   → 已取消
 *   已支付 + 发货   → 已发货
 *   ...
 * </pre>
 * 查询只需 {@code TABLE.get(from).get(action)}，二十行代码就能跑。
 *
 * <h2>为什么专门写一个"另一种实现"？</h2>
 *
 * <p>因为讲设计模式最容易犯的错，是<b>只讲它好</b>，不讲"什么时候不该用它"。
 * 状态模式不是免费的：五个状态类、五次 {@code switch}、二十个分支。
 * 如果一张表就能解决，用五个类就是<b>过度设计</b>。
 *
 * <p>所以本项目刻意把两者放在一起，让读者自己对比：
 *
 * <table border="1">
 *   <caption>表驱动 vs 状态模式</caption>
 *   <tr><th></th><th>转移表（本类）</th><th>状态模式</th></tr>
 *   <tr><td>代码量</td><td>71 行代码（一个类）</td><td>141 行代码（接口 + 五个类）</td></tr>
 *   <tr><td>规则可见性</td><td><b>极好</b>，一眼看全</td><td>要打开五个文件</td></tr>
 *   <tr><td>加一个状态</td><td>改表 + 加一个枚举值</td><td>加一个类 + 改 {@code permits}</td></tr>
 *   <tr><td>加一个动作</td><td>改表</td><td><b>改五个类的 {@code switch}</b></td></tr>
 *   <tr><td>能表达副作用吗</td><td><b>不能</b></td><td>能</td></tr>
 *   <tr><td>能被编译器检查吗</td><td>不能，漏填就是 {@code null}</td><td>能，{@code switch} 穷尽</td></tr>
 * </table>
 *
 * <h2>它表达不了什么——这是本节的重点</h2>
 *
 * <p>注释再多，不如一次实证。本项目在 {@code Client} 的 Demo 4 里用本类
 * 跑了一遍"待支付 → 已支付 → 已发货 → 已完成"，得到了<b>完全相同的状态序列</b>，
 * 但得到了一个不同的结局：<b>物流单号是空的</b>。
 *
 * <p>原因很直接：发货时要把单号写进订单，这属于<b>副作用</b>，
 * 而表里的值只有"目标状态"这一个信息，<b>没有任何地方可以挂副作用</b>。
 * 想加上它，只能回到调用方写：
 * <pre>
 *   OrderState next = TABLE.get(from).get(action);
 *   if (action == SHIP) {
 *       order.setTrackingNo(generateNo());     // ← 又要 setter
 *   }
 *   if (action == CANCEL) {
 *       order.setCancelReason(...);            // ← 每个动作一个 if
 *   }
 *   order.setState(next);                      // ← 又要 setter
 * </pre>
 * 于是表退化成"查表 + 一长串 {@code if}"，比状态模式更乱，
 * 而且又把 {@code setState} 请了回来——那是本模式最该删掉的东西。
 *
 * <blockquote>
 * <b>判据</b>：状态转移<b>只有"转到哪"</b>，没有任何副作用 →
 * 用表，别用状态模式。<br>
 * 转移<b>还要顺手做点事</b>（记账、发单号、释放库存、发通知）→
 * 用状态模式，表会逼着你把副作用堆回调用方。
 * </blockquote>
 *
 * <p>还有一条更细的判据：<b>需不需要拒绝</b>。
 * 表查不到就是 {@code null}，{@code null} 的含义要靠调用方约定
 * （是"不允许"还是"表漏填了？）。状态模式里，
 * "不允许"是 {@code allowedActions()} 里<b>少了一个元素</b>，
 * 语义明确，而且能在编译期/启动期被覆盖率测试查出来。
 *
 * @author design-patterns-pro
 */
public final class TransitionTable {

    /**
     * 转移表本体。
     *
     * <p><b>为什么用 {@link LinkedHashMap} 而不是 {@link Map#of}？</b>
     * 两个原因：
     * <ol>
     *   <li>{@code Map.of} 的迭代顺序<b>不保证</b>，而本类的
     *       {@link #describeAsText()} 要打印整张表，顺序必须稳定；</li>
     *   <li>表的键是 {@code enum} 实例，{@code Map.of} 内部按
     *       {@code hashCode} 散列，而 {@code enum} 的默认 {@code hashCode}
     *       是 <b>identity hash</b>——每次 JVM 启动都可能不同。
     *       如果用它来迭代，同一份代码两次运行的输出顺序会不一样，
     *       文档里的"真实输出"立刻变成假的。</li>
     * </ol>
     *
     * <p>外层不可变（{@code unmodifiableMap}）防止有人往表里加新状态；
     * 内层每张子表同样是不可变的，原因相同。
     */
    private static final Map<OrderState, Map<OrderAction, OrderState>> TABLE = build();

    private TransitionTable() {
        // 纯静态工具，不允许实例化
    }

    private static Map<OrderState, Map<OrderAction, OrderState>> build() {
        Map<OrderState, Map<OrderAction, OrderState>> table = new LinkedHashMap<>();

        // 待支付：付钱 / 取消
        Map<OrderAction, OrderState> pending = new LinkedHashMap<>();
        pending.put(OrderAction.PAY, PaidState.INSTANCE);
        pending.put(OrderAction.CANCEL, CancelledState.INSTANCE);
        table.put(PendingState.INSTANCE, Collections.unmodifiableMap(pending));

        // 已支付：发货 / 取消（退款）
        Map<OrderAction, OrderState> paid = new LinkedHashMap<>();
        paid.put(OrderAction.SHIP, ShippedState.INSTANCE);
        paid.put(OrderAction.CANCEL, CancelledState.INSTANCE);
        table.put(PaidState.INSTANCE, Collections.unmodifiableMap(paid));

        // 已发货：只能确认收货
        Map<OrderAction, OrderState> shipped = new LinkedHashMap<>();
        shipped.put(OrderAction.CONFIRM, CompletedState.INSTANCE);
        table.put(ShippedState.INSTANCE, Collections.unmodifiableMap(shipped));

        // 两个终态：没有任何出路
        table.put(CompletedState.INSTANCE, Map.of());
        table.put(CancelledState.INSTANCE, Map.of());

        return Collections.unmodifiableMap(table);
    }

    /**
     * 全部状态，按业务流程的固定顺序排列。
     *
     * <p>不用 {@code TABLE.keySet()}：虽然本类用了 {@code LinkedHashMap}、
     * 顺序恰好稳定，但把"顺序"这件事实托付给一个 Map 的实现细节，
     * 是很脆弱的依赖——某天有人把它换成 {@code HashMap}，
     * 输出就会悄悄变。这里<b>显式列出</b>，顺序才有保证。
     *
     * @return 不可变的状态列表，顺序固定
     */
    public static List<OrderState> states() {
        return List.of(
                PendingState.INSTANCE,
                PaidState.INSTANCE,
                ShippedState.INSTANCE,
                CompletedState.INSTANCE,
                CancelledState.INSTANCE);
    }

    /**
     * 查表：从某个状态执行某个动作，会转到哪个状态。
     *
     * @param from   起始状态
     * @param action 动作
     * @return 目标状态；表中没有这个组合时为空
     */
    public static Optional<OrderState> next(OrderState from, OrderAction action) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(action));
    }

    /**
     * 判断某个状态下是否允许某个动作。
     *
     * <p>等价于 {@code next(from, action).isPresent()}，但语义更直白，
     * 调用方不用去理解"查不到就是不允许"这条约定。
     *
     * @param from   起始状态
     * @param action 动作
     * @return 允许返回 {@code true}
     */
    public static boolean allows(OrderState from, OrderAction action) {
        return next(from, action).isPresent();
    }

    /**
     * 把整张表渲染成多行文本，供演示打印。
     *
     * <p>用 {@link DisplayWidth#padRight} 对齐而不是 {@code String.format("%-12s")}：
     * 后者按<b>字符数</b>补空格，而中文字符在终端里占<b>两列</b>。
     * 用 {@code %-12s} 排中文表格，列会歪得看不出是表格——
     * 这是中文技术文档里最常见的一个排版 bug。
     *
     * <p><b>每个行的最后一个单元格不补空格。</b>
     * 如果无条件 {@code padRight}，每一行末尾都会多出几个无意义的空格，
     * 放到 Git diff 里就是一堆看不见的改动，复制到 Markdown 里也会被
     * linter 报“行尾空白”。<b>排版只在需要对齐的地方补空格，行尾不是这种地方。</b>
     *
     * @return 形如表格的多行字符串
     */
    public static String describeAsText() {
        int stateWidth = 10;
        int cellWidth = 12;
        OrderAction[] actions = OrderAction.values();
        StringBuilder out = new StringBuilder();

        // ---- 表头 ----
        StringBuilder header = new StringBuilder(DisplayWidth.padRight("状态", stateWidth));
        for (int i = 0; i < actions.length; i++) {
            header.append(i == actions.length - 1
                    ? actions[i].label()
                    : DisplayWidth.padRight(actions[i].label(), cellWidth));
        }
        out.append(header).append('\n');

        // ---- 分隔线：按“每格都补满”的逻辑宽度画，比内容略宽一点，视觉上包住整张表 ----
        out.append(DisplayWidth.line('-', stateWidth + cellWidth * actions.length)).append('\n');

        // ---- 五行数据 ----
        for (OrderState state : states()) {
            StringBuilder row = new StringBuilder(
                    DisplayWidth.padRight(state.displayName(), stateWidth));
            for (int i = 0; i < actions.length; i++) {
                String cell = next(state, actions[i])
                        .map(target -> "→" + target.displayName())
                        .orElse("—");
                row.append(i == actions.length - 1
                        ? cell
                        : DisplayWidth.padRight(cell, cellWidth));
            }
            out.append(row).append('\n');
        }
        return out.toString();
    }
}
