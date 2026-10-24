package com.patterns.behavioral.chainofresponsibility.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次"链上旅行"的上下文：<b>不变的请求 + 不断增长的审计轨迹</b>。
 *
 * <p>它的生命周期只有一个方法调用的长度：{@code ApprovalChain.submit()} 里被创建，
 * 跟着请求在链上传一圈，最后被某个节点（或兜底逻辑）快照进 {@link ApprovalDecision}。
 * 方法返回之后它就没有人再持有了。
 *
 * <p><b>为什么要有这个类？两个理由。</b>
 *
 * <p><b>理由一：让处理器可以"只接收一个参数"。</b>
 * 没有它的话，{@code handle} 就得写成 {@code handle(RefundRequest req, List<String> audit)}——
 * 每加一个横切关注点（审计、埋点、截止时间）就要改一次接口签名，
 * 而所有实现类都得跟着改。把"请求"和"这次调用的过程数据"打包成一个上下文，
 * 接口签名从此稳定。
 *
 * <p><b>理由二：把"结构化"贯彻到底。</b>
 * 审计轨迹存的是 {@link Step} 而不是拼好的字符串。原因是拼好的字符串
 * 只能整行打印，而结构化之后可以按节点过滤、按结果统计、按需对齐排版。
 * 命令模式 README 里那句"能用结构表达的信息，就不要只存在字符串里"，在这里同样成立。
 *
 * <p><b>它是可变的，为什么还安全？</b>
 * 因为它<b>从不跨线程共享</b>：一个 {@code submit} 调用创建一个上下文，
 * 整条链在同一个线程上同步走完。这比给 {@code Step} 列表加锁或换成
 * {@code CopyOnWriteArrayList} 简单得多，也快得多。
 * <b>先想清楚"谁会并发访问"，再决定要不要线程安全——而不是无脑加锁。</b>
 *
 * @author design-patterns-pro
 */
public final class ApprovalContext {

    /** 不变的请求。 */
    private final RefundRequest request;

    /** 审计轨迹。只在当前线程、当前这一次调用里增长。 */
    private final List<Step> steps = new ArrayList<>();

    /**
     * @param request 退款单，不能为 null
     */
    public ApprovalContext(RefundRequest request) {
        this.request = Objects.requireNonNull(request, "退款单不能为 null");
    }

    /** 被传递的请求。 */
    public RefundRequest request() {
        return request;
    }

    /**
     * 记录一个节点做出的判断。
     *
     * <p><b>注意：三个参数都是必填的，而且 {@code verdict} 必须能区分
     * "我不做决定"和"我同意了"。</b> 这是本 Demo 里最容易写错的地方：
     * 一个只写 {@code record(name, "ok")} 的实现，事后根本看不出
     * 这个节点是"批准了"还是"跳过了"。
     *
     * @param handler 节点名称
     * @param verdict 判断结果：批准 / 驳回 / 无权 / 通过（不否决）
     * @param detail  判断依据，必须包含具体数字，否则等于没写
     */
    public void record(String handler, String verdict, String detail) {
        steps.add(new Step(handler, verdict, detail));
    }

    /**
     * 当前已经走过的轨迹快照。
     *
     * <p>用 {@code List.copyOf} 而不是直接返回字段：后者会把内部可变列表交出去，
     * 拿到它的人一 {@code add} 就能往审计轨迹里塞假记录。
     * {@code copyOf} 是<b>真拷贝</b>，不是 {@code Collections.unmodifiableList} 那种视图
     * （视图只是禁止你改，字段本身还在变，你手里的"快照"会跟着一起变）。
     *
     * @return 不可变的轨迹列表
     */
    public List<Step> auditTrail() {
        return List.copyOf(steps);
    }

    /**
     * 一帧审计记录：谁、做了什么判断、依据是什么。
     *
     * @param handler 节点名称
     * @param verdict 判断结果
     * @param detail  判断依据
     */
    public record Step(String handler, String verdict, String detail) {
    }
}
