package com.patterns.behavioral.strategy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 通用策略适配器：用<b>一行 lambda</b> 创建一个折扣策略，无需新建类文件。
 *
 * <p><b>为什么需要这个类？</b>
 *
 * <p>{@link DiscountStrategy} 有<b>两个</b>抽象方法（{@code name()} 和 {@code apply()}），
 * 所以它<b>不是函数式接口</b>，不能直接用 lambda：
 * <pre>
 *   DiscountStrategy s = p -&gt; p.multiply(RATE);   // ✗ 编译错误：target type is not a functional interface
 * </pre>
 *
 * <p>这带来一个现实矛盾：
 * <ul>
 *   <li>运营临时要一个"员工内购五折"，逻辑就一行，为它建一个 20 行的类文件太重；</li>
 *   <li>但如果不建类，就没法给这个策略起名字，日志里会变成 {@code Lambda$$0x1234}。</li>
 * </ul>
 *
 * <p>本类用 record 把 lambda 和名字<b>打包成一个对象</b>，同时统一处理参数校验与结果规范化：
 * <pre>
 *   // 一行创建一个新策略，有名字、有校验、有规范化
 *   DiscountStrategy staff = new SimpleDiscountStrategy(
 *           "员工内购五折",
 *           p -&gt; p.multiply(new BigDecimal("0.50")));
 *
 *   DiscountStrategy thresholdFree = new SimpleDiscountStrategy(
 *           "满 500 直接减 100",
 *           p -&gt; p.compareTo(new BigDecimal("500")) &gt;= 0
 *                 ? p.subtract(new BigDecimal("100"))
 *                 : p);
 * </pre>
 *
 * <p><b>为什么用 record 而不是普通类？</b>
 * record 自动生成了：
 * <ul>
 *   <li>全字段构造器（配合<b>紧凑构造器</b>做校验，不需要写 {@code this.name = name}）；</li>
 *   <li>{@code name()} 访问器——<b>正好就是接口要求的那个方法</b>，不用手写；</li>
 *   <li>{@code equals()} / {@code hashCode()} / {@code toString()}，
 *       于是两个规则相同的策略对象可以直接比较、可以放进 {@code Set} 去重、
 *       可以直接打印出 {@code SimpleDiscountStrategy[name=员工内购五折, rule=...]}。</li>
 * </ul>
 * 一个 30 行的 record 顶得上一个 80 行的普通类。
 *
 * <p><b>什么时候该用它，什么时候该写具名类？</b>
 * <table border="1">
 *   <caption>选择标准</caption>
 *   <tr><th>情况</th><th>选择</th></tr>
 *   <tr><td>规则一行就能写完，且不需要额外方法</td><td>用本类（lambda）</td></tr>
 *   <tr><td>规则需要构造参数（如封顶金额）</td><td>写具名类，见 {@link VipDiscountStrategy}</td></tr>
 *   <tr><td>需要暴露额外方法（如 {@code isCapped}）</td><td>写具名类</td></tr>
 *   <tr><td>规则复杂到需要私有辅助方法</td><td>写具名类</td></tr>
 *   <tr><td>需要单元测试单独覆盖这条规则</td><td>写具名类（lambda 不好定位）</td></tr>
 * </table>
 *
 * <p><b>这就是策略模式在现代 Java 里的真实形态</b>：
 * 少数复杂策略用具名类，大量简单策略用 lambda + 适配器。
 * GoF 写这本书时是 1994 年（C++/Smalltalk），没有 lambda，所以书里每个策略都是一个类。
 * <b>照搬书上的类结构到 Java 8+ 项目里，会产生大量只有一行逻辑的类文件，这本身就是过度设计。</b>
 *
 * @param name 策略名称，会作为 {@link DiscountStrategy#name()} 的返回值
 * @param rule 折扣计算规则，接收原价、返回未规范化的折后价
 * @author design-patterns-pro
 */
public record SimpleDiscountStrategy(
        String name,
        UnaryOperator<BigDecimal> rule
) implements DiscountStrategy {

    /**
     * 紧凑构造器（compact constructor）：不写参数列表，只做校验和归一化。
     *
     * <p>这是 record 的语法特性——编译器会在赋值给字段<b>之前</b>插入这段代码。
     * 比传统构造器少了 {@code this.name = name;} 这种样板。
     */
    public SimpleDiscountStrategy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("策略名称不能为空——没有名字的策略在日志里无法辨认");
        }
        // Objects.requireNonNull 会返回原值，所以可以直接用在紧凑构造器里给字段赋值
        Objects.requireNonNull(rule, "折扣规则不能为 null");
    }

    /**
     * {@code name()} 由 record 自动生成，这里不需要写。
     * 只有 {@code apply()} 需要实现。
     */
    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        // lambda 只负责"怎么算"，校验和规范化由本类统一兜底。
        // 这意味着即使 lambda 写得粗糙（忘了处理负数、忘了保留小数位），
        // 最终结果依然符合 DiscountStrategy 的契约。
        return DiscountStrategy.normalize(rule.apply(originalPrice));
    }
}
