package com.patterns.behavioral.state;

/**
 * 订单上可以发生的四个业务动作。
 *
 * <p><b>为什么要把动作抽成枚举，而不是让状态自己"往前走一步"？</b>
 * 原项目给 {@code Order} 只暴露了一个方法：
 * <pre>
 *   order.request();   // 没有参数，没有说是"支付"还是"发货"
 * </pre>
 * 结果是<b>调用方无法表达意图</b>——不管你想干什么，订单都只会机械地推进一格。
 * 用户只想查一下订单状态，调了 {@code request()}，订单就被"支付"了。
 *
 * <p>真实世界里，用户点的是<b>支付按钮</b>、<b>发货按钮</b>、<b>取消按钮</b>，
 * 每个按钮对应一个动作，动作是<b>语义</b>，不是"推进"。
 * 把动作显式建模出来之后，状态机才有可能回答一个真正重要的问题：
 * <pre>
 *   这个动作，在我当前这个状态下，意味着什么？
 * </pre>
 * 这句话正是状态模式的全部内容。
 *
 * <p><b>为什么用 {@code enum} 而不是字符串或 int？</b>
 * 因为动作集合是<b>封闭的</b>——订单上不会有第五种动作在运行时突然冒出来。
 * 用 enum 之后：
 * <ul>
 *   <li>写错名字编译不过（字符串常量写错只有运行时才发现）；</li>
 *   <li>{@code switch} 可以穷尽检查，将来加动作时，所有没处理的地方<b>编译失败</b>；</li>
 *   <li>{@code EnumSet} 表示"允许的动作集合"又小又快。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public enum OrderAction {

    /** 买家付款。 */
    PAY("支付"),

    /** 卖家发货。 */
    SHIP("发货"),

    /** 买家确认收货。 */
    CONFIRM("确认收货"),

    /** 买家或系统取消订单。 */
    CANCEL("取消");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于拼接人类可读的提示。
     *
     * <p>不直接 {@code name()}：枚举常量名是给编译器看的（{@code PAY}），
     * 中文名是给人看的（{@code 支付}）。两者分开，将来改文案不用改常量名，
     * 改常量名也不会影响已经发给用户的文案。
     *
     * @return 动作的中文名
     */
    public String label() {
        return label;
    }
}
