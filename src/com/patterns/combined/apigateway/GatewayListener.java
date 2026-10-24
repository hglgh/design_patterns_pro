package com.patterns.combined.apigateway;

/**
 * 网关事件监听器：<b>这是观察者模式（Observer）</b>。
 *
 * <p><b>为什么网关需要它？</b>
 * 因为「请求处理完了」这件事，有太多东西想知道：
 * 指标系统要计数、告警系统要看错误率、审计要留痕、链路追踪要上报。
 * 如果让 {@link Gateway} 依次调用它们，网关就从「处理请求」变成
 * 「处理请求 + 知道所有下游系统」，每接一个新系统就要改一次网关。</p>
 *
 * <p>观察者把方向反过来：<b>网关只负责广播「发生了什么」，谁关心谁自己订阅。</b>
 * 新增一个订阅者不需要改网关一行代码。</p>
 *
 * <p><b>为什么事件对象是不可变的 {@code record}？</b>
 * 因为它会被同时投递给多个订阅者。只要有一个订阅者能改它，
 * 后面的人看到的就是被改过的数据——<b>广播的东西必须只读</b>。
 * 这和 {@link GatewayResponse} 必须不可变是同一个理由。</p>
 *
 * <p><b>和 Spring 的 {@code ApplicationEvent} 是什么关系？</b>
 * 同一件事。Spring 只是把「订阅者列表 + 广播」做成了容器级别的能力，
 * 并且默认<b>同步</b>调用（加了 {@code @Async} 才异步）。
 * 本项目手写一遍，是为了让「观察者不是魔法，就是一份订阅者列表」这件事显式化。</p>
 *
 * @author design-patterns-pro
 */
public interface GatewayListener {

    /**
     * 接收一个事件。
     *
     * <p><b>约定：实现必须是「快且不抛异常」的。</b>
     * 观察者是串行调用的，一个慢订阅者会拖慢所有请求；
     * 一个抛异常的订阅者会毁掉整次调用。真实实现要么异步，
     * 要么在 {@code onEvent} 内部把异常全部吞掉并自行上报。</p>
     *
     * @param event 事件
     */
    void onEvent(Event event);

    /**
     * 一次请求处理完成后广播出去的事实。
     *
     * @param requestId 请求编号
     * @param path      请求路径
     * @param status    HTTP 状态码
     * @param servedBy  谁产生的响应（{@code gateway} 或上游实例名）
     * @param instance  命中的上游实例，可能为 {@code null}
     * @param cacheHit  是否由缓存返回
     * @param attempts  真实打到上游的次数
     */
    record Event(String requestId, String path, int status, String servedBy,
                 String instance, boolean cacheHit, int attempts) {

        /**
         * @return 状态码为 2xx 时返回 {@code true}
         */
        public boolean success() {
            return status >= 200 && status < 300;
        }

        /**
         * 一行描述，便于订阅者打印。
         *
         * @return 形如 {@code r-1 GET /api/order → 200 (order-1, 1 次上游)} 的字符串
         */
        public String describe() {
            return requestId + " " + path + " → " + status
                    + " (" + servedBy + ", " + attempts + " 次上游"
                    + (cacheHit ? ", 缓存命中" : "") + ")";
        }
    }
}
