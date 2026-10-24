package com.patterns.combined.orderservice;

import com.patterns.util.Money;

import java.io.PrintStream;
import java.math.BigDecimal;

/**
 * 装饰器：<b>把每次求值的前后价格打印出来，而且可以随时关掉</b>。
 *
 * <p>这个类直接对应原项目的 {@code LoggingDiscountDecorator}，
 * 但它修掉了原版的三个问题：</p>
 * <table border="1">
 *   <caption>原版与现在的对比</caption>
 *   <tr><th>维度</th><th>原项目 {@code LoggingDiscountDecorator}</th><th>本类 {@code TracePolicy}</th></tr>
 *   <tr><td>能不能关</td><td>不能。{@code println} 写死在 {@code applyDiscount} 里</td>
 *       <td>构造时传入 {@code enabled=false} 即完全不打印，<b>零开销</b></td></tr>
 *   <tr><td>输出到哪里</td><td>{@code System.out} 硬编码</td>
 *       <td>注入 {@link PrintStream}，可以重定向到文件、内存缓冲或测试捕获器</td></tr>
 *   <tr><td>带不带身份</td><td>只有「执行策略前价格」，不知道是哪个策略</td>
 *       <td>每行带策略名，多个折扣叠加时能分清是谁</td></tr>
 *   <tr><td>有没有结论</td><td>只打前后价格，读者要自己相减</td>
 *       <td>直接给出「减了多少」</td></tr>
 * </table>
 *
 * <p><b>为什么不用 {@code record}？</b></p>
 * record 适合「全部状态都是数据」的类。本类持有 {@link PrintStream} ——
 * 一个明确的可变外部资源，而且它的 {@code equals} 没有业务意义
 * （两个追踪装饰器不会因为指向同一个流就被认为相等）。
 * <b>record 应该只用于值对象</b>：{@code FullReductionPolicy} 是值对象，
 * 本类是「带外部资源的工具」，所以写成 final class。</p>
 *
 * <p><b>为什么默认构造要传 {@code enabled}，而不是默认打开？</b></p>
 * 演示的输出需要<b>逐字节可复现</b>：如果追踪默认打开，
 * 那么每次调用都会多出几行，Client 的真实输出就不再是稳定的 134 行。
 * 更重要的是，生产环境的默认值应该是「关」——
 * 日志级别由配置决定，而不是由某个装饰器擅自决定。<b>默认安静、需要时显式打开</b>
 * 是基础设施类的基本礼貌。</p>
 *
 * <p><b>为什么这个装饰器不改变 {@link #name()}？</b></p>
 * 与 {@link CappedPolicy} 相反：封顶改变了<b>计算结果</b>，
 * 所以名字必须变；追踪<b>不改变任何计算结果</b>，它只是旁观者。
 * 如果旁观者改了名字，审计轨迹里就会出现
 * 「追踪（满 100 减 20）」这种把观测手段混进业务名称的噪音。
 * <b>装饰器该不该改名字，取决于它改不改变行为。</b></p>
 *
 * @author design-patterns-pro
 */
public final class TracePolicy implements DiscountPolicy {

    /** 被装饰的策略。 */
    private final DiscountPolicy delegate;

    /** 是否开启追踪。关闭时 {@link #discount} 直接转发，不产生任何字符串拼接。 */
    private final boolean enabled;

    /** 输出去向。 */
    private final PrintStream out;

    /** 行首缩进，让追踪行在 Demo 输出里不与正文混淆。 */
    private final String indent;

    /**
     * 创建一个关闭状态的追踪装饰器，输出到 {@code System.out}。
     *
     * @param delegate 被装饰的策略
     */
    public TracePolicy(DiscountPolicy delegate) {
        this(delegate, false, System.out, "    ");
    }

    /**
     * 创建一个可配置的追踪装饰器。
     *
     * @param delegate 被装饰的策略，非 null
     * @param enabled  是否打印
     * @param out      输出流，非 null
     * @param indent   行首缩进，非 null
     * @throws IllegalArgumentException 任一参数为 null
     */
    public TracePolicy(DiscountPolicy delegate, boolean enabled, PrintStream out, String indent) {
        if (delegate == null) {
            throw new IllegalArgumentException("被装饰的策略不能为 null");
        }
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为 null");
        }
        if (indent == null) {
            throw new IllegalArgumentException("缩进不能为 null");
        }
        this.delegate = delegate;
        this.enabled = enabled;
        this.out = out;
        this.indent = indent;
    }

    /**
     * 返回一个打开追踪的同款装饰器。
     *
     * <p>提供这个方法而不是让调用方改字段，是因为本类是不可变的：
     * 「打开」意味着创建一个新实例，而不是修改已有的那个。
     * 这样同一个策略可以同时有「安静版」和「追踪版」两个包装，
     * 互不影响。</p>
     *
     * @return 开启追踪的新实例
     */
    public TracePolicy enabled() {
        return new TracePolicy(delegate, true, out, indent);
    }

    /**
     * 名称与被装饰者完全一致 —— 观测手段不该出现在业务名称里。
     *
     * @return 被装饰者的名称
     */
    @Override
    public String name() {
        return delegate.name();
    }

    /**
     * 转发求值，可选地打印一行追踪。
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @return 被装饰者的计算结果，本类不做任何修改
     */
    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        if (!enabled) {
            return delegate.discount(price, context);
        }
        BigDecimal reduction = delegate.discount(price, context);
        BigDecimal after = DiscountPolicy.money(price.subtract(reduction));
        out.println(indent + "[追踪] " + delegate.name()
                + "  入 " + Money.yuan(price)
                + "  出 " + Money.yuan(after)
                + "  减 " + Money.yuan(reduction));
        return reduction;
    }
}
