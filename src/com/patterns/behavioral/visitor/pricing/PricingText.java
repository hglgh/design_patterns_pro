package com.patterns.behavioral.visitor.pricing;

import java.math.BigDecimal;

/**
 * 购物车入参校验工具（包内可见）。
 *
 * <p>和 {@code document} 包的 {@code DocumentText} 一样，
 * 它不是模式的一部分，只是三个商品 record 共用的校验逻辑。
 * 原项目所有商品 record 都不做校验：数量为 0、单价为负、
 * 折扣率写成 1.2 都能构造成功，错误要等到算钱时才以
 * 一个莫名其妙的金额暴露出来。</p>
 */
final class PricingText {

    /**
     * 校验商品名。
     *
     * @param name 商品名
     * @return 原商品名
     * @throws IllegalArgumentException 为 null 或空白
     */
    static String requireName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("商品名不能为 null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("商品名不能为空白字符串");
        }
        return name;
    }

    /**
     * 校验单价。
     *
     * @param price 单价
     * @return 原单价
     * @throws IllegalArgumentException 为 null 或负数
     */
    static Money requirePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("单价不能为 null");
        }
        if (price.isNegative()) {
            throw new IllegalArgumentException("单价不能为负，实际 " + price.format());
        }
        return price;
    }

    /**
     * 校验数量。数量至少为 1 —— 购物车里出现 0 件商品本身就没有意义，
     * 允许它只会让「应付 0 元」这种脏数据流进报表。
     *
     * @param quantity 数量
     * @return 原数量
     * @throws IllegalArgumentException 小于 1
     */
    static int requireQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("数量至少为 1，实际 " + quantity);
        }
        return quantity;
    }

    /**
     * 校验折扣率，必须落在 (0, 1] 区间。
     *
     * <p>原项目不校验，{@code new DiscountItem("书", 100, 1.2)}
     * 会让折后价比原价还贵，而打印出来只是"价格: 120.0"，
     * 看不出任何异常。</p>
     *
     * @param rate 折扣率
     * @return 原折扣率
     * @throws IllegalArgumentException 为 null 或不在 (0, 1]
     */
    static BigDecimal requireRate(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("折扣率不能为 null");
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("折扣率必须大于 0，实际 " + rate.toPlainString());
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("折扣率不能大于 1（1 表示不打折），实际 " + rate.toPlainString());
        }
        return rate;
    }

    private PricingText() {
    }
}
