package com.patterns.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 金额的构造与显示。
 *
 * <p><b>这不是设计模式的一部分</b>，只是多个 Demo 共用的三个小工具：
 * 把字符串变成合法的金额、把金额变成人能读的字符串、把金额拼进规则名称。
 *
 * <p><b>它为什么在 {@code com.patterns.util} 而不是某个模式包里？</b>
 * 最初本文件放在 {@code chainofresponsibility} 包下，因为责任链是第一个需要金额的模式。
 * 一旦状态模式、观察者模式也要用，其他包就得
 * {@code import com.patterns.behavioral.chainofresponsibility.Money} ——
 * 让"状态"去依赖"责任链"，这是纯粹由文件位置造成的假耦合。
 * <b>工具类不该寄居在任何一个模式包里</b>，所以它被上移到这里。
 * 这本身也是一条经验：包结构要按"被谁使用"划分，而不是按"第一次被谁写下"划分。
 *
 * <p><b>为什么金额一律用 {@link BigDecimal}？</b>
 * 原项目的订单金额用的是 {@code double}，跑 Demo 时看不出问题，
 * 但下面这些算式在 double 下都是错的（本项目在 JDK 25 下实测）：
 * <pre>
 *   0.1 + 0.2 = 0.30000000000000004
 *   99.9 * 0.7 = 69.92999999999999
 * </pre>
 * 折扣是乘法、满减是减法、叠加是连续加减乘——三步下来误差会累积。
 * 详细的论证见策略模式 README，这里不重复展开，只保证一件事：
 * <b>凡是钱，从构造到比较到显示，全程 BigDecimal。</b>
 *
 * <p><b>为什么格式化不用 {@code DecimalFormat}？</b>
 * 因为它<b>不是线程安全的</b>。{@code DecimalFormat} 内部有可变的
 * {@code DigitList} 缓冲，多线程共用一个实例会算出乱七八糟的格式，
 * 而且这种 bug 只在并发下偶发，极难复现。要用的话必须每次 new 一个（性能差）
 * 或放进 {@code ThreadLocal}（复杂）。
 * {@code String.format} 每次调用都在内部新建 Formatter，天然线程安全，
 * 代价是稍微慢一点——在"打印日志"这个量级上完全不值一提。
 *
 * @author design-patterns-pro
 */
public final class Money {

    private Money() {
        // 工具类不需要实例
    }

    /**
     * 从字符串构造金额，并统一收敛到 2 位小数（四舍五入到分）。
     *
     * <p>用字符串而不是 {@code new BigDecimal(0.1)}：
     * 后者会把 double 的二进制误差一起带进来，
     * 得到 {@code 0.1000000000000000055511151231257827}。
     *
     * @param value 金额的字面值，例如 {@code "200"}
     * @return 保留 2 位小数的金额
     */
    public static BigDecimal of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 显示成带货币符号和千分位的金额，例如 {@code ￥8,000.00}。
     *
     * <p>{@link Locale#ROOT} 是刻意的：它让千分位和括号的写法在所有机器上一致，
     * 不受操作系统区域设置影响。否则同一段代码在中文 Windows 和英文 Linux 上
     * 可能打印出不同的符号，而 Demo 的输出应当是<b>可复现的</b>。
     *
     * @param value 金额
     * @return 形如 {@code ￥8,000.00} 的字符串
     */
    public static String yuan(BigDecimal value) {
        return "￥" + String.format(Locale.ROOT, "%,.2f", value);
    }

    /**
     * 去掉无意义的尾随零，用于拼进规则名称，例如把 {@code 200.00} 显示成 {@code 200}。
     *
     * <p>必须用 {@code toPlainString()}：{@code stripTrailingZeros()} 之后
     * {@code 200.00} 会变成 {@code 2E+2}，直接 {@code toString()} 会打出科学计数法。
     *
     * @param value 金额
     * @return 不带尾随零的普通十进制字符串
     */
    public static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
