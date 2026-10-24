package com.patterns.behavioral.observer;

/**
 * 内容事件的三种类型。
 *
 * <p><b>为什么原项目的通知只有一个 {@code String postContent}？</b>
 * 因为它只演示了"发动态"这一种场景。真实的内容平台至少有三种事件：
 * 发文章、发视频、开直播——它们的通知渠道、时效要求、失败重试策略都不一样。
 *
 * <p><b>{@code realtime} 是干什么的？</b>
 * 直播是"错过就没了"的事件，运营上通常要求实时推送（推送 + 短信）；
 * 文章和视频可以走站内信，晚几分钟无所谓。这个字段让
 * {@link Observer} 的实现能区分对待，而不需要自己去 {@code if (type == LIVE)}：
 *
 * <pre>
 *   if (event.type().realtime()) {
 *       // 立刻推，不合并
 *   }
 * </pre>
 *
 * <p>把判断做成数据（一个 boolean 字段），而不是让每个观察者各写一遍
 * {@code type == LIVE}——这条原则和状态模式里"能用数据描述的差异不要用类"是同一个。
 *
 * @author design-patterns-pro
 */
public enum EventType {

    /** 文章：可以合并推送，晚几分钟无所谓。 */
    ARTICLE("文章", false),

    /** 视频：同文章，但通常带封面。 */
    VIDEO("视频", false),

    /** 直播：错过就没了，必须实时推送。 */
    LIVE("直播", true);

    private final String label;

    /** 是否要求实时推送。 */
    private final boolean realtime;

    EventType(String label, boolean realtime) {
        this.label = label;
        this.realtime = realtime;
    }

    /**
     * 中文名，用于日志与界面文案。
     *
     * @return 事件类型的中文名
     */
    public String label() {
        return label;
    }

    /**
     * 是否要求实时推送。
     *
     * @return 直播返回 {@code true}，其余返回 {@code false}
     */
    public boolean realtime() {
        return realtime;
    }
}
