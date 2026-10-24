package com.patterns.combined.orderservice;

/**
 * 一个链节点对请求做的决定：<b>生效、跳过、终止</b>。
 *
 * <p>这是本组合示例对原项目最关键的一处修正。原项目的
 * {@code DiscountHandler.apply} 是这样的：</p>
 * <pre>
 *   public double apply(double price) {
 *       double discounted = handle(price);       // 无条件执行
 *       if (next != null) {
 *           return next.apply(discounted);       // 无条件继续
 *       }
 *       return discounted;
 *   }
 * </pre>
 * <p>两个「无条件」意味着：只要节点在链上，它就<b>一定</b>会改动价格，
 * 而且<b>一定</b>会继续往下传。这不是责任链，是一条管道 ——
 * 节点既没有「我不适用」的表达能力，也没有「到此为止」的表达能力。</p>
 *
 * <p><b>为什么需要三种状态而不是 boolean？</b></p>
 * <table border="1">
 *   <caption>三种决策的语义</caption>
 *   <tr><th>决策</th><th>含义</th><th>对价格</th><th>对链</th><th>审计里怎么写</th></tr>
 *   <tr><td>{@link Applied}</td><td>我处理了</td><td>减了若干元</td><td>继续往后</td><td>生效，减 ￥20.00</td></tr>
 *   <tr><td>{@link Skipped}</td><td>我不适用</td><td>不变</td><td>继续往后</td><td>跳过：需 Lv.3，当前 Lv.1</td></tr>
 *   <tr><td>{@link Stopped}</td><td>到此为止</td><td>不变</td><td><b>不再往后</b></td><td>终止：满减与折扣券互斥</td></tr>
 * </table>
 * <p>如果只用 {@code boolean}，「跳过」和「终止」就分不开 ——
 * 而这两种情况在业务上完全是两件事：前者是「这条规则不适用，看看下一条」，
 * 后者是「后面的规则都不许再生效了」。把它们混成一个 {@code false}，
 * 就是解释器模式里「未知字段静默返回 false」的同类错误：
 * <b>把语义不同的情况压进同一个值，让调用方无法区分。</b></p>
 *
 * <p><b>为什么 {@link Applied} 要携带金额，而不是让调用方自己算前后差？</b></p>
 * 因为「减免额」是这个决策的核心信息，不是副产品。让决策自己携带它，
 * 有两个好处：审计轨迹不需要再保存「前价」和「后价」两个字段去反推；
 * 装饰器（如 {@link CappedPolicy}）也能直接在金额上做文章，
 * 而不需要先把价格算出来再倒推。</p>
 *
 * <p><b>为什么把三个实现嵌在接口里？</b></p>
 * 与解释器模式的 {@code Value} 同理由：三个 record 各自只有一两个字段、
 * 两三行校验，独立成文件会让读者在「决策类型」和「决策实现」之间来回跳。
 * 嵌进来之后，三态的完整定义和公共契约在同一屏里，
 * sealed 的 permits 也可以省略。</p>
 *
 * @author design-patterns-pro
 */
public sealed interface DiscountDecision {

    /**
     * 生效：本节点减掉了 {@code amount} 元。
     *
     * @param amount 减免金额，必须为正
     */
    record Applied(java.math.BigDecimal amount) implements DiscountDecision {

        /**
         * 校验金额。
         *
         * @throws IllegalArgumentException 金额为空或非正
         */
        public Applied {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("生效决策必须携带正的减免金额：" + amount);
            }
            amount = DiscountPolicy.money(amount);
        }

        @Override
        public String label() {
            return "生效";
        }

        @Override
        public boolean continues() {
            return true;
        }
    }

    /**
     * 跳过：本节点对当前订单不适用，但链继续。
     *
     * @param reason 跳过原因，例如 {@code "需 Lv.3，当前 Lv.1"}
     */
    record Skipped(String reason) implements DiscountDecision {

        /**
         * 校验原因。
         *
         * @throws IllegalArgumentException 原因为空
         */
        public Skipped {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("跳过必须给出原因：否则审计轨迹只是一串 false");
            }
        }

        @Override
        public String label() {
            return "跳过";
        }

        @Override
        public boolean continues() {
            return true;
        }
    }

    /**
     * 终止：本节点要求整条链停止，后续节点不再执行。
     *
     * @param reason 终止原因，例如 {@code "满减与折扣券不可同享"}
     */
    record Stopped(String reason) implements DiscountDecision {

        /**
         * 校验原因。
         *
         * @throws IllegalArgumentException 原因为空
         */
        public Stopped {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("终止必须给出原因：否则没人知道为什么后面没执行");
            }
        }

        @Override
        public String label() {
            return "终止";
        }

        @Override
        public boolean continues() {
            return false;
        }
    }

    /**
     * 决策的中文标签，用于表格与审计输出。
     *
     * @return {@code "生效"}、{@code "跳过"} 或 {@code "终止"}
     */
    String label();

    /**
     * 链是否还要继续。
     *
     * @return {@code Applied} 与 {@code Skipped} 返回 true，{@code Stopped} 返回 false
     */
    boolean continues();

    /**
     * 便于阅读的完整描述，用于审计轨迹。
     *
     * @return 例如 {@code "生效 减 ￥20.00"}、{@code "跳过：需 Lv.3"}
     */
    default String describe() {
        return switch (this) {
            case Applied applied -> "生效 减 " + com.patterns.util.Money.yuan(applied.amount());
            case Skipped skipped -> "跳过：" + skipped.reason();
            case Stopped stopped -> "终止：" + stopped.reason();
        };
    }
}
