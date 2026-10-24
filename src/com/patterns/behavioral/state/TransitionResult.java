package com.patterns.behavioral.state;

import java.util.Objects;

/**
 * 一次状态转移尝试的结果：成功 / 被拒绝，以及<b>为什么</b>。
 *
 * <p><b>为什么不用异常表达"非法转移"？</b>
 * 这是本模式最容易做错的一个选择，值得掰开说。
 *
 * <p>先看原项目的做法——什么也不做：
 * <pre>
 *   class CompletedState implements OrderState {
 *       public void handleRequest(Order order) {
 *           System.out.println("订单已完成。");     // 静默，返回 void
 *       }
 *   }
 * </pre>
 * 已完成订单再调一次，打印一行"订单已完成"就结束了。调用方拿到的返回类型是
 * {@code void}，<b>无法区分</b>"操作成功"和"什么也没发生"。
 *
 * <p>另一种常见做法是抛异常：
 * <pre>
 *   if (state != PENDING) throw new IllegalStateException("已支付的订单不能再次支付");
 * </pre>
 * 看起来更严格，但它把两件性质完全不同的事混在了一起：
 * <table border="1">
 *   <caption>异常与业务分支的区别</caption>
 *   <tr><th></th><th>用异常</th><th>用返回值</th></tr>
 *   <tr><td>含义</td><td>"程序出错了"</td><td>"这个操作现在不允许"</td></tr>
 *   <tr><td>调用方预期</td><td>没预期，所以会崩</td><td>有预期，所以会分支处理</td></tr>
 *   <tr><td>典型场景</td><td>数据库连不上、参数为 null</td><td>用户点了灰掉的按钮</td></tr>
 *   <tr><td>日志级别</td><td>ERROR + 告警</td><td>INFO / 直接返回给前端</td></tr>
 * </table>
 *
 * <p>用户在"已完成"订单上再点一次"确认收货"，这是<b>正常业务分支</b>，
 * 不是程序故障。如果是异常，那么：
 * <ul>
 *   <li>Web 层必须 try-catch 把它翻成 400，否则变成 500；</li>
 *   <li>批量任务里一张单子的正常拒绝会中断整批；</li>
 *   <li>异常栈在日志里刷屏，把真正的故障淹掉。</li>
 * </ul>
 *
 * <blockquote>
 * <b>判据</b>：这个"失败"是调用方<b>应该预料并处理</b>的吗？
 * 是 → 返回值；不是（程序写错了）→ 异常。
 * </blockquote>
 *
 * <p><b>为什么用 record？</b>
 * 它天然不可变、自带 {@code equals/hashCode/toString}，而且
 * <b>紧凑构造器</b>可以把"成功必须有目标状态、失败必须没有"这条不变量
 * 钉死在创建处——任何人都构造不出一个自相矛盾的结果对象。
 *
 * @param success  这次尝试是否真的改变了状态
 * @param action   被尝试的动作（永远非空，失败时也要知道是哪个动作失败了）
 * @param from     尝试前的状态（永远非空）
 * @param to       成功时为转移后的状态；失败时必须是 {@code null}
 * @param message  成功时说明"发生了什么"，失败时说明"为什么不行"
 * @author design-patterns-pro
 */
public record TransitionResult(
        boolean success,
        OrderAction action,
        OrderState from,
        OrderState to,
        String message
) {

    /**
     * 紧凑构造器：把三条不变量钉死在对象创建的那一刻。
     *
     * <p>这三条检查看起来啰嗦，但它们让 {@code TransitionResult} 的
     * <b>任何一个实例都必然自洽</b>。后续所有使用它的代码（打印、写库、
     * 前端渲染）都不需要再考虑"万一 success 是 true 但 to 是 null 怎么办"。
     */
    public TransitionResult {
        Objects.requireNonNull(action, "动作不能为 null：失败的结果也要能说清是哪个动作失败了");
        Objects.requireNonNull(from, "起始状态不能为 null");
        Objects.requireNonNull(message, "结果说明不能为 null：没有说明的失败等于没说");
        if (success && to == null) {
            throw new IllegalArgumentException("转移成功必须给出目标状态");
        }
        if (!success && to != null) {
            throw new IllegalArgumentException("转移失败不应该有目标状态，实际给出：" + to.displayName());
        }
    }

    /**
     * 构造一次成功的转移。
     *
     * @param action  动作
     * @param from    起始状态
     * @param to      目标状态
     * @param message 说明发生了什么
     * @return 成功的结果
     */
    public static TransitionResult ok(OrderAction action, OrderState from,
                                      OrderState to, String message) {
        return new TransitionResult(true, action, from, to, message);
    }

    /**
     * 构造一次被拒绝的转移。
     *
     * <p>注意这里<b>没有</b> {@code to} 参数——拒绝就是"状态不变"，
     * 从签名上就不给你传错的机会。
     *
     * @param action 动作
     * @param from   当前状态
     * @param reason 为什么不允许
     * @return 失败的结果
     */
    public static TransitionResult rejected(OrderAction action, OrderState from, String reason) {
        return new TransitionResult(false, action, from, null, reason);
    }

    /**
     * 一行式描述，用于日志和演示输出。
     *
     * <p>成功与失败用不同的箭头：{@code →} 表示真的动了，
     * {@code ⊣} 表示被挡住了。视觉上就能一眼分辨，
     * 不必去读那句中文说明。
     *
     * <p><b>失败时不拼接 {@link #message()}。</b>
     * 因为拒绝的原因通常很长（"「已支付」下不能「支付」；当前允许：发货、取消"），
     * 拼进来会让一行变得非常长，打出来的轨迹一条比三条还宽，
     * 根本没法扫。原因更适合单独一行展示——
     * <b>一行只放得下一件事，不要把两件事塑进一行。</b>
     *
     * @return 形如 {@code 支付：待支付 → 已支付} 或 {@code 支付 ⊣ 已支付} 的描述
     */
    public String describe() {
        if (success) {
            return action.label() + "：" + from.displayName() + " → " + to.displayName();
        }
        return action.label() + " ⊣ " + from.displayName();
    }
}
