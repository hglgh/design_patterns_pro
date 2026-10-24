package com.patterns.combined.orderservice;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 管道工厂：<b>把「配置」翻译成一条装配好的责任链</b>。
 *
 * <p>它与 {@link PolicyFactory} 的分工是本组合示例最需要看清的一处：</p>
 * <table border="1">
 *   <caption>两层工厂的分工</caption>
 *   <tr><th></th><th>{@link PolicyFactory}</th><th>本类</th></tr>
 *   <tr><td>输入</td><td>一个字符串 {@code "full:100:20"}</td><td>若干「阶段」配置</td></tr>
 *   <tr><td>输出</td><td>一个策略对象</td><td>一条链 + 执行器</td></tr>
 *   <tr><td>回答</td><td>「这种折扣怎么算」</td><td>「这些折扣按什么顺序串起来」</td></tr>
 *   <tr><td>变化原因</td><td>新增一种折扣算法</td><td>调整优惠的叠加顺序、互斥关系、上限</td></tr>
 * </table>
 * <p>把两者合成一个「超级工厂」，会导致「运营只想调顺序」和
 * 「开发新增算法」这两件完全不同的事改同一个文件 ——
 * 协作冲突、代码审查互相阻塞。<b>工厂的粒度应该跟「谁提需求」对齐。</b></p>
 *
 * <p><b>为什么用 Builder 而不是一个 {@code List<String>}？</b></p>
 * 一条链上每个节点有四个可配置项：节点名、折扣配置、互斥组、适用条件。
 * 如果压成字符串（{@code "@券|full:100:20|new"}），解析代码会比业务代码还长，
 * 而且拼错一个竖线就是运行期异常。Builder 让配置在<b>编译期</b>就被类型检查，
 * 也让调用处读起来像一句配置说明：
 * <pre>
 *   PipelineFactory.builder()
 *       .stage("满减", "full:100:20")
 *       .exclusiveStage("折扣券", "券", "percent:0.9")
 *       .stage("积分", "points:0.01:500")
 *       .cap("50")
 *       .build();
 * </pre>
 *
 * <p><b>为什么装饰器和条件都在工厂里装配，而不是让调用方自己包？</b></p>
 * 这是「把复杂度锁在一个地方」的取舍。三种做法：
 * <ul>
 *   <li>调用方自己 {@code new CappedPolicy(new TracePolicy(new FullReductionPolicy(...)))}：
 *       每处调用都要写一层括号，且容易漏掉封顶；</li>
 *   <li>每个策略自己支持这些参数：策略类会被"风控""观测"污染；</li>
 *   <li><b>工厂统一装配</b>：调用方只说"我要封顶 50、要追踪"，
 *       具体包几层、按什么顺序包，由工厂决定。</li>
 * </ul>
 * <p>包装顺序是固定的：<b>先封顶、再追踪</b>。这样追踪打出来的是
 * 封顶之后的实际减免额，而不是被截断前的理论值 ——
 * 观测到的数字必须与真实生效的数字一致，否则日志就是在骗人。</p>
 *
 * @author design-patterns-pro
 */
public final class PipelineFactory {

    private PipelineFactory() {
        // 工具类不需要实例
    }

    /**
     * 创建 Builder。
     *
     * @return 新的构造器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 一个阶段：链上的一环。
     *
     * @param nodeName   节点名，会由工厂补上序号
     * @param policySpec 折扣配置
     * @param group      互斥组名；null 表示不属于任何组
     * @param requirement 适用条件
     */
    private record Step(String nodeName, String policySpec, String group, Requirement requirement) {
    }

    /**
     * 链构造器。
     */
    public static final class Builder {

        private final List<Step> steps = new ArrayList<>();
        private BigDecimal cap;
        private boolean trace;
        private PrintStream traceOut = System.out;

        private Builder() {
        }

        /**
         * 添加一个普通节点。
         *
         * @param nodeName   节点名
         * @param policySpec 折扣配置，例如 {@code "full:100:20"}
         * @return 本构造器
         */
        public Builder stage(String nodeName, String policySpec) {
            return stage(nodeName, policySpec, Requirement.always());
        }

        /**
         * 添加一个带条件的普通节点。
         *
         * @param nodeName    节点名
         * @param policySpec  折扣配置
         * @param requirement 适用条件
         * @return 本构造器
         */
        public Builder stage(String nodeName, String policySpec, Requirement requirement) {
            steps.add(new Step(nodeName, policySpec, null, requirement));
            return this;
        }

        /**
         * 添加一个互斥组成员（无额外条件）。
         *
         * @param nodeName   节点名
         * @param group      互斥组名
         * @param policySpec 折扣配置
         * @return 本构造器
         */
        public Builder exclusiveStage(String nodeName, String group, String policySpec) {
            return exclusiveStage(nodeName, group, policySpec, Requirement.always());
        }

        /**
         * 添加一个互斥组成员。
         *
         * @param nodeName    节点名
         * @param group       互斥组名
         * @param policySpec  折扣配置
         * @param requirement 适用条件
         * @return 本构造器
         */
        public Builder exclusiveStage(String nodeName, String group, String policySpec,
                                      Requirement requirement) {
            steps.add(new Step(nodeName, policySpec, group, requirement));
            return this;
        }

        /**
         * 给每个策略套一层「单步减免上限」。
         *
         * @param amount 上限金额文本，例如 {@code "50"}
         * @return 本构造器
         * @throws IllegalArgumentException 不是合法正数
         */
        public Builder cap(String amount) {
            BigDecimal value = new BigDecimal(amount);
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("封顶金额必须为正：" + amount);
            }
            this.cap = value;
            return this;
        }

        /**
         * 是否给每个策略套追踪装饰器。
         *
         * @param enabled true 表示打印追踪
         * @return 本构造器
         */
        public Builder trace(boolean enabled) {
            this.trace = enabled;
            return this;
        }

        /**
         * 追踪输出的去向。
         *
         * @param out 输出流，非 null
         * @return 本构造器
         */
        public Builder traceOut(PrintStream out) {
            if (out == null) {
                throw new IllegalArgumentException("追踪输出流不能为 null");
            }
            this.traceOut = out;
            return this;
        }

        /**
         * 组装。
         *
         * @return 执行器；没有任何阶段时返回空链
         */
        public DiscountPipeline build() {
            List<DiscountHandler> nodes = new ArrayList<>();
            int index = 1;
            for (Step step : steps) {
                DiscountPolicy policy = PolicyFactory.create(step.policySpec());
                if (cap != null) {
                    policy = new CappedPolicy(policy, cap);
                }
                if (trace) {
                    policy = new TracePolicy(policy, true, traceOut, "    ");
                }
                String name = circled(index++) + " " + step.nodeName();
                if (step.group() == null) {
                    nodes.add(new PolicyHandler(name, policy, step.requirement()));
                } else {
                    nodes.add(new ExclusiveGroupHandler(
                            name, step.group(), policy, step.requirement()));
                }
            }
            if (nodes.isEmpty()) {
                return DiscountPipeline.empty();
            }
            return DiscountPipeline.of(DiscountHandler.chain(nodes));
        }

        /**
         * 组装并立即对给定上下文求值。
         *
         * @param context 订单上下文
         * @return 结果
         */
        public DiscountResult run(OrderContext context) {
            return build().calculate(context);
        }
    }

    /**
     * 数字转圈码：1 → ①，20 → ⑳，21 → (21)。
     *
     * @param index 序号，从 1 开始
     * @return 圈码或括号序号
     */
    private static String circled(int index) {
        if (index >= 1 && index <= 20) {
            return String.valueOf((char) ('\u2460' + index - 1));
        }
        return "(" + index + ")";
    }
}
