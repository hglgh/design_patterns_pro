package com.patterns.behavioral.visitor.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额值对象，内部用「分」为单位存储。
 *
 * <p><b>为什么不用 double？</b>
 * 原项目的购物车全程用 double，于是
 * {@code 58.0 * 0.8} 算出来是 {@code 46.400000000000006}，
 * 并且这个值原封不动地打进了用户可见的订单明细（探针 V6 已复现）。
 * 更严重的是总价：{@code 99.0 + 46.400000000000006 + 6.0}
 * 的真实二进制值是 {@code 151.39999999999999...}，
 * 只是因为 {@code Double.toString} 会做最短表示，屏幕上才显示 151.4 ——
 * 也就是说"看起来对了"，但实际上每一分钱都在漂。</p>
 *
 * <p><b>为什么不用 BigDecimal 直接当字段？</b>
 * BigDecimal 是不可变对象，每次加减都产生新实例，且两个
 * "值相等"的 BigDecimal 用 {@code equals} 比较时会把精度算进去
 * （{@code 1.0} 与 {@code 1.00} 不 equals）。作为值对象到处传递，
 * 很容易在某个地方忘了 {@code setScale}，导致"同一笔钱在不同环节精度不同"。</p>
 *
 * <p><b>用 long 存分</b>：加减法天然精确、比较无歧义、
 * 可做 {@code Math.addExact} 溢出检查，是金融系统最常见的做法。
 * 只有打折（需要乘以比率）时才临时用 BigDecimal，
 * 算完立刻 {@code HALF_UP} 收回到分。</p>
 *
 * @param cents 金额（分）
 */
public record Money(long cents) implements Comparable<Money> {

    /**
     * 零元，用于"没有优惠"这类语义上正确的零值。
     */
    public static final Money ZERO = new Money(0);

    /**
     * 按「元」的小数形式构造，例如 "99"、"99.5"、"99.50"。
     *
     * <p>用字符串而不是 double 入参，是为了让调用方无法把
     * 已经带上浮点误差的值传进来。解析使用 BigDecimal，
     * 解析失败时错误信息里带上原始文本。</p>
     *
     * @param yuan 元金额文本，不能为 null 或负
     * @return 对应金额
     * @throws IllegalArgumentException 格式非法或为负数
     */
    public static Money ofYuan(String yuan) {
        if (yuan == null) {
            throw new IllegalArgumentException("金额文本不能为 null");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(yuan.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "金额格式非法: \"" + yuan + "\"（示例: 99 / 99.5 / 99.50）", e);
        }
        return ofYuan(value);
    }

    /**
     * 按 {@link BigDecimal} 元金额构造。
     *
     * <p>小数部分超过两位（例如 99.999）时按四舍五入收到分，
     * 而不是默默丢弃第三位。</p>
     *
     * @param yuan 元金额
     * @return 对应金额
     * @throws IllegalArgumentException 为负数或超出 long 范围
     */
    public static Money ofYuan(BigDecimal yuan) {
        if (yuan == null) {
            throw new IllegalArgumentException("金额不能为 null");
        }
        if (yuan.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负，实际 " + yuan.toPlainString());
        }
        BigDecimal scaled = yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        try {
            return new Money(scaled.longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("金额超出可表示范围: " + yuan.toPlainString(), e);
        }
    }

    /**
     * 按分构造。
     *
     * @param cents 分
     * @return 对应金额
     */
    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /**
     * 加法。用 {@link Math#addExact} 拒绝溢出，而不是悄悄回绕成负数。
     *
     * @param other 另一个金额，不能为 null
     * @return 两笔金额之和
     */
    public Money plus(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("other 不能为 null");
        }
        return new Money(Math.addExact(cents, other.cents));
    }

    /**
     * 减法。
     *
     * @param other 另一个金额，不能为 null
     * @return 差额，可能为负
     */
    public Money minus(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("other 不能为 null");
        }
        return new Money(Math.subtractExact(cents, other.cents));
    }

    /**
     * 乘以一个非负整数（数量）。
     *
     * @param quantity 数量，不能为负
     * @return 乘积
     */
    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity 不能为负，实际 " + quantity);
        }
        return new Money(Math.multiplyExact(cents, quantity));
    }

    /**
     * 乘以一个比率（折扣），结果四舍五入到分。
     *
     * <p>这里是唯一会进入"分数"的地方，也是原项目 double 误差的源头。
     * 用 BigDecimal 计算后立即 HALF_UP 收回整数分，
     * 可以让 58.00 × 0.8 精确得到 46.40。</p>
     *
     * @param rate 比率，不能为负
     * @return 折后金额
     */
    public Money times(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("rate 不能为 null");
        }
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("rate 不能为负，实际 " + rate.toPlainString());
        }
        BigDecimal result = BigDecimal.valueOf(cents)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP);
        try {
            return new Money(result.longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("折扣计算结果超出范围: cents=" + cents
                    + ", rate=" + rate.toPlainString(), e);
        }
    }

    /**
     * 是否为 0。
     *
     * @return 分值为 0 时返回 true
     */
    public boolean isZero() {
        return cents == 0;
    }

    /**
     * 是否为负数。正常业务里金额不应为负，
     * 这个方法主要是给 {@link #minus} 的结果做断言用。
     *
     * @return 小于 0 时返回 true
     */
    public boolean isNegative() {
        return cents < 0;
    }

    /**
     * 格式化成两位小数的元，例如 {@code 46.40}、{@code 99.00}。
     *
     * <p>刻意不用 {@code String.format}：格式化的行为依赖默认 Locale，
     * 而输出需要逐字节可复现（README 里的哈希校验依赖这一点）。
     * 手工拼整数部分和小数部分就不受 Locale 影响。</p>
     *
     * @return 形如 "46.40" 的文本
     */
    public String format() {
        long absolute = Math.abs(cents);
        long yuanPart = absolute / 100;
        long centPart = absolute % 100;
        String sign = cents < 0 ? "-" : "";
        return sign + yuanPart + "." + (centPart < 10 ? "0" : "") + centPart;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    /**
     * 与 {@link #format()} 一致，便于直接拼进字符串。
     *
     * @return 两位小数金额
     */
    @Override
    public String toString() {
        return format();
    }
}
