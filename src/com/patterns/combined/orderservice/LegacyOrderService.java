package com.patterns.combined.orderservice;

import java.util.ArrayList;
import java.util.List;

/**
 * 原项目组合示例的<b>忠实复现</b>：只用它来对照，不参与任何新流程。
 *
 * <p>这个类把 {@code e:\codeFile\codeFather\design_patterns} 里
 * {@code CombineApplicationExamples} 的 9 个文件、217 行代码
 * 压缩成一个类，<b>行为逐字节保持一致</b>：</p>
 * <ul>
 *   <li>金额用 {@code double}；</li>
 *   <li>三个策略的参数写死（满 100 减 20、打九折、固定减 10）；</li>
 *   <li>责任链<b>无条件执行、无条件下传</b>；</li>
 *   <li>日志装饰器直接 {@code System.out.println}，且不带策略名；</li>
 *   <li>没有任何上限、条件、终止与结果对象。</li>
 * </ul>
 *
 * <p><b>为什么要保留一个"明知有问题"的类？</b></p>
 * 单看新实现，读者只会觉得"类挺多"。只有把同一条输入同时喂给两套实现，
 * 才能看清这些类究竟换来了什么。本类在 {@code Client} 里被调用四次，
 * 依次暴露四个真实缺陷（全部实测，不是推演）：</p>
 * <table border="1">
 *   <caption>四个可复现的缺陷</caption>
 *   <tr><th>场景</th><th>原项目输出</th><th>缺陷</th></tr>
 *   <tr><td>11.0 元 + 打折 + 积分</td><td>{@code -0.09999999999999964}</td><td>应付为负，平台倒找钱</td></tr>
 *   <tr><td>58.8 元 + 打折 + 积分</td><td>{@code 42.919999999999995}</td><td>用户看到一串小数</td></tr>
 *   <tr><td>150 元，折扣顺序互换</td><td>107 与 105</td><td>结果取决于调用方传参顺序，无规则</td></tr>
 *   <tr><td>无积分用户</td><td>仍然减 10 元</td><td>策略不看余额，无条件执行</td></tr>
 * </table>
 *
 * <p><b>这个类为什么用 {@code @Deprecated}？</b></p>
 * 不是为了让人别用（它本来就不该被用），而是让 IDE 在所有调用点画上删除线 ——
 * <b>被删除线的代码，读者一眼就知道"这是反面教材"</b>，
 * 不需要先读三百行注释才反应过来。</p>
 *
 * @author design-patterns-pro
 * @deprecated 仅用于与旧实现做行为对照，新代码请使用
 *             {@link DiscountPipeline} + {@link PolicyFactory} + {@link PipelineFactory}
 */
@Deprecated
public final class LegacyOrderService {

    private LegacyOrderService() {
    }

    /**
     * 旧版策略：返回<b>折后价</b>，参数是 {@code double}。
     *
     * <p>保留原始签名，包括那个 {@code double} ——
     * 它是四个缺陷里两个的直接来源。</p>
     */
    @Deprecated
    private interface LegacyStrategy {

        /**
         * 计算折后价。
         *
         * @param originalPrice 原价
         * @return 折后价
         */
        double applyDiscount(double originalPrice);
    }

    /**
     * 旧版日志装饰器：前后各打一行，<b>不带策略名、不能关闭</b>。
     */
    @Deprecated
    private static final class LegacyLoggingDecorator implements LegacyStrategy {

        private final LegacyStrategy delegate;

        LegacyLoggingDecorator(LegacyStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public double applyDiscount(double originalPrice) {
            System.out.println("【日志】执行策略前价格：" + originalPrice);
            double result = delegate.applyDiscount(originalPrice);
            System.out.println("【日志】执行策略后价格：" + result);
            return result;
        }
    }

    /**
     * 旧版满减：门槛与额度写死为 100 / 20。
     */
    @Deprecated
    private static final class LegacyFullReduction implements LegacyStrategy {

        @Override
        public double applyDiscount(double originalPrice) {
            if (originalPrice >= 100) {
                System.out.println("【满减】满100减20，已减20");
                return originalPrice - 20;
            }
            return originalPrice;
        }
    }

    /**
     * 旧版打折：固定九折，且用 {@code double} 计算。
     */
    @Deprecated
    private static final class LegacyPercentage implements LegacyStrategy {

        @Override
        public double applyDiscount(double originalPrice) {
            double discount = originalPrice * 0.1;
            System.out.println("【打折】打九折，已减：" + discount);
            return originalPrice - discount;
        }
    }

    /**
     * 旧版积分抵扣：不看用户有多少积分，固定减 10 元。
     */
    @Deprecated
    private static final class LegacyPoints implements LegacyStrategy {

        @Override
        public double applyDiscount(double originalPrice) {
            System.out.println("【积分抵扣】已减10");
            return originalPrice - 10;
        }
    }

    /**
     * 旧版工厂：{@code switch} + 无参构造 + 错误消息不含可用列表。
     *
     * @param type 折扣类型
     * @return 策略
     */
    @Deprecated
    private static LegacyStrategy create(String type) {
        return switch (type) {
            case "full" -> new LegacyFullReduction();
            case "percent" -> new LegacyPercentage();
            case "points" -> new LegacyPoints();
            default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
        };
    }

    /**
     * 旧版下单：复现原项目 {@code OrderService.placeOrder} 的完整行为。
     *
     * <p>原项目的结构是「策略 → 日志装饰器 → 责任节点 → 串成链」，
     * 而那条链的 {@code apply} 是「无条件执行自己，再无条件调用下一个」。
     * 这与「按顺序循环执行」在数学上完全等价，所以这里用递归复现，
     * 确保输出逐字一致（包括每一行日志的顺序）。</p>
     *
     * @param originalPrice 原价
     * @param discountTypes 折扣类型，按调用方给定顺序执行
     * @return 最终支付金额
     */
    public static double placeOrder(double originalPrice, String... discountTypes) {
        System.out.println("原始价格：" + originalPrice);
        List<LegacyStrategy> chain = new ArrayList<>();
        for (String type : discountTypes) {
            chain.add(new LegacyLoggingDecorator(create(type)));
        }
        double finalPrice = applyChain(chain, 0, originalPrice);
        System.out.println("最终支付金额：" + finalPrice);
        return finalPrice;
    }

    /**
     * 按顺序执行，等价于原项目责任链的递归 {@code apply}。
     *
     * @param chain 策略链
     * @param index 当前下标
     * @param price 当前价格
     * @return 执行完剩余全部策略后的价格
     */
    @Deprecated
    private static double applyChain(List<LegacyStrategy> chain, int index, double price) {
        if (index >= chain.size()) {
            return price;
        }
        return applyChain(chain, index + 1, chain.get(index).applyDiscount(price));
    }

    /**
     * 只计算、不打印：供 Client 做数值对照，不污染输出。
     *
     * <p>与 {@link #placeOrder} 使用完全相同的算式，只是把
     * {@code println} 换成了 {@code PrintStream.nullOutputStream()} 之外的
     * 直接计算。把它单独列出来，是为了让「数值相同、输出不同」这件事
     * 在 Demo 里可以被清楚展示。</p>
     *
     * @param originalPrice 原价
     * @param discountTypes 折扣类型
     * @return 最终支付金额
     */
    public static double silentPrice(double originalPrice, String... discountTypes) {
        double price = originalPrice;
        for (String type : discountTypes) {
            // 直接内联旧算式，不经过任何会打印的策略对象
            price = switch (type) {
                case "full" -> price >= 100 ? price - 20 : price;
                case "percent" -> price - price * 0.1;
                case "points" -> price - 10;
                default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
            };
        }
        return price;
    }
}
