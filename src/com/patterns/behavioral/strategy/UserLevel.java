package com.patterns.behavioral.strategy;

import java.math.BigDecimal;

/**
 * 会员等级枚举：<b>把"等级 → 折扣配置"的映射关系集中在一处</b>。
 *
 * <p><b>为什么需要这个枚举？</b>
 *
 * <p>原项目的 {@code Client} 是这样用策略的：
 * <pre>
 *   calculator.setStrategy(new FullReductionStrategy());     // 调用方自己决定用哪个
 *   calculator.setStrategy(new PercentageDiscountStrategy());
 *   calculator.setStrategy(new VipDiscountStrategy());
 * </pre>
 * 也就是说——<b>客户端手动 new 出具体策略</b>。这在 Demo 里没问题，但在真实系统里是个大漏洞：
 * <ul>
 *   <li>客户端凭什么知道"VIP 用户该用 VipDiscountStrategy"？这个业务规则被<b>散落在每个调用点</b>。
 *       下单页写一遍、购物车写一遍、订单预览接口再写一遍。</li>
 *   <li>规则一变（金卡从九折改成八八折），要<b>全局搜索</b>所有 new 策略的地方，漏一处就是价格不一致。</li>
 *   <li>客户端能 new 出<b>任何</b>策略——普通用户可以自己 {@code new VipDiscountStrategy()} 享受八折。
 *       这在真实系统里就是<b>越权漏洞</b>。</li>
 * </ul>
 *
 * <p>正确的做法是把"什么样的用户享受什么样的折扣"这条业务规则<b>收敛到一处</b>。
 * 本枚举承载配置数据，{@link DiscountStrategyFactory} 负责根据配置创建策略对象。
 *
 * <p><b>为什么用 enum 承载配置，而不是用 properties 文件或数据库？</b>
 * 教学示例用 enum 是为了：无外部依赖、编译期类型安全、可以 IDE 跳转、跑起来就能看。
 * 真实系统里这些配置<b>应该放在数据库或配置中心</b>，因为运营要能随时改，不能每次都发版。
 * 但无论配置存在哪，<b>"读取配置 → 创建对应策略对象"这一步都是工厂的职责</b>，
 * 这个结构是不变的。把 enum 换成 {@code Map<String, DiscountConfig>} 从数据库读，代码骨架完全一样。
 *
 * <p><b>为什么字段用 BigDecimal 而不是 double？</b>
 * 见 {@link DiscountStrategy} 类注释里的实测数据——金额和折扣率一旦用 double，精度就永久丢失。
 * 枚举常量在类加载时初始化一次，用 {@code new BigDecimal("0.80")} 的成本可以忽略。
 *
 * @author design-patterns-pro
 * @see DiscountStrategyFactory 根据本枚举创建策略对象
 */
public enum UserLevel {

    /** 普通会员：无折扣。 */
    NORMAL("普通会员", null, null),

    /** 银卡会员：九五折，不封顶。 */
    SILVER("银卡会员", new BigDecimal("0.95"), null),

    /** 金卡会员：九折，不封顶。 */
    GOLD("金卡会员", new BigDecimal("0.90"), null),

    /** 钻石会员：八折，但单笔最多优惠 200 元（风控封顶）。 */
    VIP("钻石会员", new BigDecimal("0.80"), new BigDecimal("200"));

    /** 等级的中文名，用于界面展示。 */
    private final String displayName;

    /** 折扣率；{@code null} 表示该等级不打折。 */
    private final BigDecimal rate;

    /** 单笔优惠封顶金额；{@code null} 表示不封顶。 */
    private final BigDecimal maxDiscount;

    UserLevel(String displayName, BigDecimal rate, BigDecimal maxDiscount) {
        this.displayName = displayName;
        this.rate = rate;
        this.maxDiscount = maxDiscount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    /** 该等级是否享受折扣。 */
    public boolean hasDiscount() {
        return rate != null;
    }
}
