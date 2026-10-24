package com.patterns.combined.orderservice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 抽象策略：<b>一种折扣怎么算</b>。
 *
 * <p>组合示例里的第一层分工。这个接口只回答一个问题：
 * 「在给定价格和订单上下文下，这种折扣能减多少钱」。
 * 它不负责顺序、不负责叠加、不负责记录、不负责创建自己 ——
 * 那四件事分别交给责任链、装饰器、工厂。</p>
 *
 * <p><b>为什么返回「减免金额」而不是原项目那样的「折后价」？</b></p>
 * 原项目的签名是：
 * <pre>
 *   double applyDiscount(double originalPrice);   // 返回折后价
 * </pre>
 * 两种签名都能算对，但组合场景下的代价完全不同：
 * <table border="1">
 *   <caption>两种签名的对比</caption>
 *   <tr><th>要做的事</th><th>返回折后价</th><th>返回减免额</th></tr>
 *   <tr><td>明细里写「减了多少」</td><td>得用前价减后价，再猜是谁减的</td><td>直接就是返回值</td></tr>
 *   <tr><td>给单个策略封顶</td><td>要反算：前价 - cap 才是新价，还要防止变成负数</td><td>{@code min(减免, cap)} 一行</td></tr>
 *   <tr><td>判断「这一步有没有生效」</td><td>比较前后价</td><td>看返回值是否大于 0</td></tr>
 *   <tr><td>累加总优惠</td><td>要保存每一步的前后价再相加</td><td>直接相加</td></tr>
 * </table>
 * <p>返回减免额还有一条隐性收益：<b>它把一个「上限」写进了类型契约</b> ——
 * 减免额天然不会小于 0，且被要求不超过当前价。于是「折后价为负」这种
 * 原项目真实存在的问题（见 {@link LegacyOrderService}）在契约层面就被堵死了。</p>
 *
 * <p><b>为什么契约要求 {@code 0 ≤ 减免 ≤ 当前价}？</b></p>
 * 三条边各有理由：
 * <ul>
 *   <li><b>不能为负</b>：负减免等于「加价」，没有任何业务场景允许一个折扣把价格算高；</li>
 *   <li><b>不能超过当前价</b>：超过就会让折后价变成负数，等于平台倒找钱给用户 ——
 *       原项目的 {@code 19.9} 元订单叠加三个折扣后算出 {@code -20.1}，
 *       这不是夸张，是 {@link LegacyOrderService} 能复现的真实输出；</li>
 *   <li><b>保留 2 位小数</b>：人民币最小单位是分，第三位小数没有意义，
 *       留着只会让「两个金额是否相等」这类比较变得不可靠。</li>
 * </ul>
 *
 * <p><b>为什么策略不允许有副作用？</b></p>
 * {@link PointsDeductionPolicy} 需要读取用户的可用积分，但它<b>不扣积分</b>。
 * 扣积分是「确认下单」这个动作的副作用，必须由订单服务在整条链算完之后
 * 统一执行一次。如果让策略自己扣，会出现两种事故：
 * <ol>
 *   <li>链算到一半抛异常，积分已经扣了，订单没下成；</li>
 *   <li>同一次下单为了「预览」，把链跑了两遍，积分被扣两次。</li>
 * </ol>
 * 纯函数的策略可以被反复求值、可以被并发共享、可以被单元测试固定输入输出 ——
 * 这三条性质是它在组合场景里能被安全复用的前提。
 *
 * @author design-patterns-pro
 * @see CappedPolicy 装饰器：给任意策略加「单次上限」，不改算法
 * @see DiscountHandler 责任链：决定多个策略按什么顺序叠加、何时停止
 * @see PolicyFactory 工厂：把「配置文本」翻译成策略对象
 */
public interface DiscountPolicy {

    /** 金额为零时的规范写法：保留 2 位小数，避免 {@code 0} 与 {@code 0.00} 混用。 */
    BigDecimal ZERO = new BigDecimal("0.00");

    /**
     * 策略的可读名称，例如 {@code "满 100 减 20"}、{@code "九折"}。
     *
     * <p>名称属于策略自身，而不是调用方临时拼的字符串 ——
     * 这是本项目对原项目的一处修正。原项目在 {@code println} 里写死了
     * {@code "【满减】"}，导致同一个策略在不同调用点可能出现不同叫法，
     * 也无法在审计轨迹里可靠地区分是哪个策略减的钱。</p>
     *
     * @return 非空白的名称
     */
    String name();

    /**
     * 计算本次能减免多少钱。
     *
     * <p><b>实现契约</b>（每个实现都在自己的 {@code discount} 里保证）：</p>
     * <ol>
     *   <li>纯函数：不改 {@code price}、不改 {@code context}、不打印、不写库；</li>
     *   <li>返回值 {@code >= 0}，且 {@code <= price}；</li>
     *   <li>返回值保留 2 位小数（{@link RoundingMode#HALF_UP}）；</li>
     *   <li>不满足条件时返回 {@link #ZERO}，而不是 {@code null} 或抛异常 ——
     *       「不适用」是正常业务分支，不是错误。</li>
     * </ol>
     *
     * @param price   当前价格（可能已经被前面的折扣减过），非 null 且非负
     * @param context 订单上下文，非 null
     * @return 本次减免金额，范围 {@code [0, price]}
     * @throws IllegalArgumentException 参数为 null 或价格为负
     */
    BigDecimal discount(BigDecimal price, OrderContext context);

    /**
     * 当 {@link #discount} 返回 0 时，用一句话解释原因。
     *
     * <p><b>为什么把「原因」也放进策略接口？</b></p>
     * 链节点需要知道「这一策略为什么没生效」。两种做法：
     * <ul>
     *   <li>节点自己推断：{@code if (reduction == 0) reason = "策略未生效"} ——
     *       对运营来说等于没有信息，他们看到的是「满减没生效」，但不知道为什么；</li>
     *   <li>策略自己回答：{@code "未满 ￥100.00（当前 ￥80.00）"} ——
     *       原因由最了解规则的那个对象给出，节点只负责把它记进审计。</li>
     * </ul>
     * <p>这正是把「判断」和「解释」都交给策略、把「流程」留在链上的分工。
     * 默认实现给了句保底文案，针对满减、积分这类有明确门槛的策略
     * 会在各自类里覆盖它。</p>
     *
     * @param price   当前价格
     * @param context 订单上下文
     * @return 非空的说明文本
     */
    default String unavailableReason(BigDecimal price, OrderContext context) {
        return "策略未产生减免";
    }

    /**
     * 校验价格入参，供各实现复用。
     *
     * <p>把校验写成接口里的静态方法，而不是每个实现各抄一遍，
     * 是为了让<b>契约的表述只存在一处</b>。原项目的三个策略里
     * 没有任何一个校验价格，于是 {@code -100} 元的订单能一路算到底。</p>
     *
     * @param price 待校验的价格
     * @throws IllegalArgumentException 为 null 或为负
     */
    static void requireValidPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("价格不能为 null");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("价格不能为负：" + price.toPlainString());
        }
    }

    /**
     * 把任意金额收敛到「分」：2 位小数，四舍五入。
     *
     * @param value 原始金额
     * @return 保留 2 位小数的金额
     */
    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 把计算结果夹到合法区间 {@code [0, price]}。
     *
     * <p>这是所有实现最后都要过的一道闸门。它同时处理三种情况：
     * 算出来是负数（当成 0）、算出来超过当前价（截到当前价）、
     * 小数位数过多（收敛到分）。</p>
     *
     * @param reduction 策略算出的减免额
     * @param price     当前价格
     * @return 合法的减免额
     */
    static BigDecimal legal(BigDecimal reduction, BigDecimal price) {
        BigDecimal rounded = money(reduction);
        if (rounded.signum() <= 0) {
            return ZERO;
        }
        return rounded.min(price);
    }
}
