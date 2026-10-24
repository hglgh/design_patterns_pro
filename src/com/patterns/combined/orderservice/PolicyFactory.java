package com.patterns.combined.orderservice;

import com.patterns.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 策略工厂：<b>把一段配置文本翻译成策略对象</b>。
 *
 * <p>配置语法只有一种：{@code 折扣码:参数1:参数2}。</p>
 * <table border="1">
 *   <caption>支持的折扣码</caption>
 *   <tr><th>配置</th><th>创建的对象</th><th>说明</th></tr>
 *   <tr><td>{@code full:100:20}</td><td>{@code FullReductionPolicy}</td><td>满 100 减 20</td></tr>
 *   <tr><td>{@code percent:0.9}</td><td>{@code PercentagePolicy}</td><td>九折</td></tr>
 *   <tr><td>{@code points:0.01:500}</td><td>{@code PointsDeductionPolicy}</td><td>每分抵 1 分钱，单笔最多 500 分</td></tr>
 * </table>
 *
 * <p><b>为什么原项目的 {@code switch (type)} 不够？</b></p>
 * 原项目的工厂是这样的：
 * <pre>
 *   public static DiscountStrategy getStrategy(String type) {
 *       return switch (type) {
 *           case "full" -&gt; new FullReductionStrategy();
 *           case "percent" -&gt; new PercentageOffStrategy();
 *           case "points" -&gt; new PointsDeductionStrategy();
 *           default -&gt; throw new IllegalArgumentException("未知的折扣类型: " + type);
 *       };
 *   }
 * </pre>
 * <p>它有三个问题，都是「组合场景」下才会暴露的：</p>
 * <ol>
 *   <li><b>不能传参</b>：{@code new FullReductionStrategy()} 内部写死 100/20。
 *       一个运营想配「满 300 减 50」，只能新增一个类，或者改代码。
 *       本项目的 {@code full:300:50} 直接解决；</li>
 *   <li><b>新增要改 switch</b>：每加一种折扣都要动这个类。虽然不可避免要"注册"，
 *       但本项目的注册是一行 {@code map.put}，且与参数解析逻辑分离；</li>
 *   <li><b>错误消息不可操作</b>：{@code "未知的折扣类型: fulll"} 只说了错，
 *       没说「可用的有哪些、各自的格式是什么」。运营只能去问开发。
 *       本项目的异常里带完整的可用列表与示例。</li>
 * </ol>
 *
 * <p><b>为什么用 {@code LinkedHashMap} 而不是 {@code Map.of}？</b></p>
 * {@code Map.of} 的迭代顺序是<b>未指定的</b>（每次 JVM 启动可能不同）。
 * 而本类的两个方法都要按固定顺序列出折扣码：
 * {@link #supportedCodes()} 供程序使用，{@link #helpText()} 会出现在异常消息里。
 * 只要顺序不固定，Demo 输出和错误消息就无法逐字节复现。
 * <b>凡是会出现在输出里的集合，都必须用有序实现</b> —— 这是本项目的一条硬规则。</p>
 *
 * <p><b>为什么工厂方法都是静态的？</b></p>
 * 工厂本身没有任何状态（注册表是编译期常量，运行期只读）。
 * 做成实例只会让调用方多写一行 {@code new PolicyFactory()}，
 * 还得考虑「要不要单例」。静态方法在这里更直接。</p>
 *
 * @author design-patterns-pro
 */
public final class PolicyFactory {

    /** 折扣码到创建函数的映射，顺序固定。 */
    private static final Map<String, Creator> REGISTRY;

    static {
        Map<String, Creator> map = new LinkedHashMap<>();
        map.put("full", PolicyFactory::fullReduction);
        map.put("percent", PolicyFactory::percentage);
        map.put("points", PolicyFactory::points);
        REGISTRY = Collections.unmodifiableMap(map);
    }

    private PolicyFactory() {
        // 工具类不需要实例
    }

    /**
     * 创建函数：把参数列表变成策略对象。
     *
     * <p>用函数式接口而不是抽象类，是因为每个创建函数只有几行，
     * 用方法引用（{@code PolicyFactory::fullReduction}）注册最省。</p>
     */
    @FunctionalInterface
    public interface Creator {

        /**
         * 根据参数创建策略。
         *
         * @param params 冒号后的参数，已按出现顺序排列
         * @return 策略对象
         */
        DiscountPolicy create(List<String> params);
    }

    /**
     * 根据配置文本创建策略。
     *
     * @param spec 配置，例如 {@code "full:100:20"}
     * @return 策略对象
     * @throws IllegalArgumentException 文本为空、折扣码未知或参数不合法
     */
    public static DiscountPolicy create(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("折扣配置不能为空；" + helpText());
        }
        String[] parts = spec.trim().split(":", -1);
        String code = parts[0].trim();
        Creator creator = REGISTRY.get(code);
        if (creator == null) {
            throw new IllegalArgumentException(
                    "未知的折扣码 [" + code + "]。\n" + helpText());
        }
        List<String> params = new ArrayList<>();
        for (int index = 1; index < parts.length; index++) {
            params.add(parts[index].trim());
        }
        try {
            return creator.create(params);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "折扣配置 [" + spec + "] 不合法：" + e.getMessage(), e);
        }
    }

    /**
     * 全部可用的折扣码，按注册顺序。
     *
     * @return 不可变集合
     */
    public static Set<String> supportedCodes() {
        return REGISTRY.keySet();
    }

    /**
     * 帮助文本：列出每个折扣码的格式与示例。
     *
     * <p>这段文本会被拼进"未知折扣码"的异常消息里。
     * 一条好的错误消息应该让看到它的人<b>不需要再问第二个人</b>，
     * 所以这里把「有哪些码、每个码要几个参数、参数什么含义」全部写出来。</p>
     *
     * @return 多行帮助文本
     */
    public static String helpText() {
        return """
                支持的折扣码：
                  full:门槛:额度         满减，例如 full:100:20 表示满 100 减 20
                  percent:实付比例       打折，例如 percent:0.9 表示九折（必须在 0 和 1 之间）
                  points:每分抵扣:上限   积分抵扣，例如 points:0.01:500 表示 500 分以内、每分抵 1 分钱""";
    }

    /**
     * {@code full:门槛:额度}。
     *
     * @param params 参数
     * @return 满减策略
     */
    private static DiscountPolicy fullReduction(List<String> params) {
        requireCount("full", params, 2, "full:门槛:额度，例如 full:100:20");
        BigDecimal threshold = number("full", params.get(0), "门槛");
        BigDecimal reduction = number("full", params.get(1), "额度");
        return new FullReductionPolicy(
                "满 " + Money.plain(threshold) + " 减 " + Money.plain(reduction),
                threshold, reduction);
    }

    /**
     * {@code percent:实付比例}。
     *
     * @param params 参数
     * @return 打折策略
     */
    private static DiscountPolicy percentage(List<String> params) {
        requireCount("percent", params, 1, "percent:实付比例，例如 percent:0.9");
        BigDecimal rate = number("percent", params.get(0), "实付比例");
        return new PercentagePolicy(nameOfRate(rate), rate);
    }

    /**
     * {@code points:每分抵扣:上限}。
     *
     * @param params 参数
     * @return 积分策略
     */
    private static DiscountPolicy points(List<String> params) {
        requireCount("points", params, 2, "points:每分抵扣:上限，例如 points:0.01:500");
        BigDecimal perPoint = number("points", params.get(0), "每分抵扣金额");
        int maxPoints = integer("points", params.get(1), "单笔积分上限");
        return new PointsDeductionPolicy("积分抵扣", perPoint, maxPoints);
    }

    /**
     * 把实付比例翻译成人话：{@code 0.9} → {@code "九折"}，{@code 0.85} → {@code "八五折"}。
     *
     * <p>名称要给人看，所以这里做了一次「数值 → 中文」的翻译。
     * 不翻译的话，审计轨迹里会出现「0.9」这种运营看不懂的名字。</p>
     *
     * @param rate 实付比例
     * @return 中文折扣名
     */
    private static String nameOfRate(BigDecimal rate) {
        String plain = Money.plain(rate);
        if (plain.startsWith("0.")) {
            String digits = plain.substring(2);
            StringBuilder name = new StringBuilder();
            String[] cn = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
            for (int index = 0; index < digits.length(); index++) {
                char ch = digits.charAt(index);
                if (ch >= '0' && ch <= '9') {
                    name.append(cn[ch - '0']);
                }
            }
            if (name.length() > 0) {
                return name + "折";
            }
        }
        return "折扣 " + plain;
    }

    /**
     * 校验参数个数。
     *
     * @param code    折扣码
     * @param params  实际参数
     * @param expected 期望个数
     * @param example 正确写法示例
     * @throws IllegalArgumentException 个数不符
     */
    private static void requireCount(String code, List<String> params, int expected, String example) {
        if (params.size() != expected) {
            throw new IllegalArgumentException(
                    "需要 " + expected + " 个参数，实际给了 " + params.size()
                            + " 个；正确写法：" + example);
        }
    }

    /**
     * 解析金额参数。
     *
     * @param code   折扣码
     * @param text   文本
     * @param what   参数含义
     * @return 金额
     * @throws IllegalArgumentException 不是合法数字
     */
    private static BigDecimal number(String code, String text, String what) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException(what + " 不能为空（折扣码 " + code + "）");
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    what + " 不是合法数字：[" + text + "]（折扣码 " + code + "）", e);
        }
    }

    /**
     * 解析整数参数。
     *
     * @param code 折扣码
     * @param text 文本
     * @param what 参数含义
     * @return 整数
     * @throws IllegalArgumentException 不是合法整数
     */
    private static int integer(String code, String text, String what) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException(what + " 不能为空（折扣码 " + code + "）");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    what + " 不是合法整数：[" + text + "]（折扣码 " + code + "）", e);
        }
    }
}
