package com.patterns.combined.orderservice;

/**
 * 参与条件：<b>这条折扣对当前订单是否适用</b>。
 *
 * <p>这是本组合示例里最容易写错的一个概念，也是原项目完全没有的东西。
 * 原项目的三个策略<b>无条件执行</b>：只要出现在链上，就一定会对价格动手。
 * 于是「新客专享」「会员 3 级以上」「凭券参与」「仅会员日」这类规则
 * 只能塞进策略内部，让策略同时负责「判断我该不该生效」和「怎么算」两件事。</p>
 *
 * <p><b>为什么把「条件」从「算法」里拆出来？</b></p>
 * 因为两者的变化频率和变化原因完全不同：
 * <table border="1">
 *   <caption>条件与算法的区别</caption>
 *   <tr><th>维度</th><th>条件（Requirement）</th><th>算法（DiscountPolicy）</th></tr>
 *   <tr><td>回答问题</td><td>要不要执行</td><td>执行时减多少</td></tr>
 *   <tr><td>典型变化</td><td>「门槛从 3 级改成 4 级」</td><td>「九折改成八折」</td></tr>
 *   <tr><td>谁提出</td><td>运营的用户分层策略</td><td>运营的价格策略</td></tr>
 *   <tr><td>单元测试</td><td>给一组上下文断言真假</td><td>给一个价格断言金额</td></tr>
 * </table>
 * <p>如果两者揉在一个类里，就会出现「同一个满减策略要复制三份：
 * 新客版、会员版、会员日版，只有条件那一行不同」。
 * 拆开之后，条件是<b>可以自由组合到任意策略上</b>的 ——
 * 这正是 {@link PolicyHandler} 能在构造时接受一个条件的意义。</p>
 *
 * <p><b>为什么用接口而不是 {@code Predicate<OrderContext>}？</b></p>
 * 二者在功能上等价，但接口多了一个东西：<b>可读的名称</b>。
 * 当一条折扣被跳过时，审计轨迹里需要写清楚原因：
 * <pre>
 *   [跳过] 会员九折：需要 Lv.3，当前 Lv.1
 * </pre>
 * 如果只有 {@code Predicate}，只能输出 {@code false}，运营看到的是
 * 「为什么我的折扣没生效」这种永远无法排查的问题。
 * 带名称和原因的条件，把「静默跳过」变成了「可解释跳过」。</p>
 *
 * <p><b>为什么提供静态工厂而不是让调用方写 lambda？</b></p>
 * 静态工厂方法名本身就是文档：{@link #minLevel} 比
 * {@code ctx -> ctx.userLevel() >= 3} 更容易在代码审查时被理解，
 * 而且把「等级比较」这个逻辑集中一处，将来改成「等级或成长值」时只改一个地方。</p>
 *
 * @author design-patterns-pro
 */
@FunctionalInterface
public interface Requirement {

    /**
     * 判断当前订单是否满足条件。
     *
     * @param context 订单上下文，非 null
     * @return 满足时返回 true
     */
    boolean test(OrderContext context);

    /**
     * 条件的可读名称，用于审计轨迹和跳过原因。
     *
     * <p>默认用接口名会让日志毫无意义，所以这里是抽象方法，
     * 每个工厂方法都返回一个带正确名称的实例。</p>
     *
     * @return 非空白的条件描述
     */
    default String describe() {
        return "自定义条件";
    }

    /**
     * 无条件：任何订单都满足。
     *
     * <p>用作「没有条件」时的默认值，避免调用方到处判空。</p>
     *
     * @return 恒为 true 的条件
     */
    static Requirement always() {
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return true;
            }

            @Override
            public String describe() {
                return "无条件";
            }
        };
    }

    /**
     * 会员等级达到指定级别。
     *
     * @param level 最低等级（含），1-5
     * @return 条件
     * @throws IllegalArgumentException 等级越界
     */
    static Requirement minLevel(int level) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("最低等级必须在 1-5 之间：" + level);
        }
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return context.userLevel() >= level;
            }

            @Override
            public String describe() {
                return "需 Lv." + level + " 及以上";
            }
        };
    }

    /**
     * 仅限新客。
     *
     * @return 条件
     */
    static Requirement newCustomerOnly() {
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return context.newCustomer();
            }

            @Override
            public String describe() {
                return "仅限新客";
            }
        };
    }

    /**
     * 必须持有券码。
     *
     * @return 条件
     */
    static Requirement withCoupon() {
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return !context.couponCode().isEmpty();
            }

            @Override
            public String describe() {
                return "需持券";
            }
        };
    }

    /**
     * 仅限会员日。
     *
     * @return 条件
     */
    static Requirement memberDayOnly() {
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return context.memberDay();
            }

            @Override
            public String describe() {
                return "仅会员日";
            }
        };
    }

    /**
     * 两个条件同时满足。
     *
     * @param other 另一个条件
     * @return 组合条件
     */
    default Requirement and(Requirement other) {
        Requirement self = this;
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return self.test(context) && other.test(context);
            }

            @Override
            public String describe() {
                return self.describe() + " 且 " + other.describe();
            }
        };
    }

    /**
     * 两个条件满足其一。
     *
     * @param other 另一个条件
     * @return 组合条件
     */
    default Requirement or(Requirement other) {
        Requirement self = this;
        return new Requirement() {
            @Override
            public boolean test(OrderContext context) {
                return self.test(context) || other.test(context);
            }

            @Override
            public String describe() {
                return "(" + self.describe() + " 或 " + other.describe() + ")";
            }
        };
    }
}
