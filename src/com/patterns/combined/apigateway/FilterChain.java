package com.patterns.combined.apigateway;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 过滤器链：负责按顺序把请求交给一个个 {@link GatewayFilter}，
 * 全部放行后再交给装饰器栈的末端 {@link UpstreamHandler}。
 *
 * <p><b>为什么把「走到第几个过滤器」这个可变状态放进一个每次调用都新建的
 * {@link Cursor} 里？</b></p>
 * <p>最容易写错的版本是让 {@code FilterChain} 自己持有 {@code int cursor}：</p>
 * <pre>
 *   // 错误写法：链对象被所有请求共享，cursor 也被共享
 *   public class FilterChain {
 *       private int cursor;           // ← 线程 A 和线程 B 抢同一个变量
 *       public GatewayResponse handle(req) { ... }
 *   }
 * </pre>
 * <p>单线程 Demo 完全看不出问题；一旦并发，A 的请求会从 B 停下的位置开始，
 * 于是「跳过认证」「重复限流」这类灾难就出现了——而且只在压力测试里复现。
 * 本项目的享元模块用探针实测过同类的共享可变状态问题
 * （{@code HashMap} + {@code computeIfAbsent} 在 16 线程下有 5/3000 轮重复创建）。</p>
 *
 * <p>把游标关进 {@code Cursor}，而 {@code Cursor} 由 {@link #newCall} 每次新建：
 * <b>共享的部分（过滤器列表、末端处理器）保持无状态，会变的部分（游标、上下文）
 * 收敛到请求作用域。</b>这条规则可以迁移到任何「链 + 上下文」的实现里。</p>
 *
 * @author design-patterns-pro
 */
public final class FilterChain {

    private final List<GatewayFilter> filters;
    private final UpstreamHandler terminal;

    /**
     * @param filters  过滤器列表，按执行顺序排列
     * @param terminal 所有过滤器都放行后要执行的处理器（通常是装饰器栈）
     */
    public FilterChain(List<GatewayFilter> filters, UpstreamHandler terminal) {
        if (filters == null) {
            throw new IllegalArgumentException("filters 不能为空");
        }
        if (terminal == null) {
            throw new IllegalArgumentException("terminal 不能为空");
        }
        this.filters = List.copyOf(filters);
        this.terminal = terminal;
    }

    /**
     * 开始一次调用：为本次请求创建一个独立的游标。
     *
     * @param request 请求
     * @return 尚未开始执行游标
     */
    public Cursor newCall(GatewayRequest request) {
        return new Cursor(request, 0);
    }

    /**
     * @return 过滤器数量
     */
    public int size() {
        return filters.size();
    }

    /**
     * 链的完整结构，用于输出。
     *
     * @return 形如 {@code auth → rate → router → cache → Metrics(...)} 的字符串
     */
    public String describe() {
        String head = filters.stream().map(GatewayFilter::name).collect(Collectors.joining(" → "));
        return head + " → " + terminal.describe();
    }

    /**
     * 一次调用的游标。<b>不可跨请求复用</b>。
     */
    public final class Cursor {

        private final CallContext context;
        private int index;

        Cursor(GatewayRequest request, int index) {
            this.context = new CallContext(request);
            this.index = index;
        }

        /**
         * @return 本次调用的上下文
         */
        public CallContext context() {
            return context;
        }

        /**
         * @return 下一个要执行的过滤器下标
         */
        public int index() {
            return index;
        }

        /**
         * 继续往前走：还有过滤器就交给下一个，没有了就交给末端处理器。
         *
         * @return 响应
         */
        public GatewayResponse proceed() {
            if (index < filters.size()) {
                return filters.get(index++).handle(context, this);
            }
            return terminal.handle(context);
        }
    }
}
