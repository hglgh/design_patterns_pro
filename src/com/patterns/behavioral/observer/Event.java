package com.patterns.behavioral.observer;

import java.util.Objects;

/**
 * 一条内容事件：谁、发了什么类型、什么标题、正文是什么。
 *
 * <p><b>为什么它是一个不可变 {@code record}？</b>
 * 因为一条事件会被<b>广播给 N 个观察者</b>。如果它是可变的，
 * 第一个观察者改了标题，第二个观察者收到的就是被改过的内容——
 * 这种 bug 只在"某个观察者顺手修改了事件对象"时出现，极难排查。
 *
 * <p>{@code record} 天然不可变，而且所有字段在构造时就能校验。
 * 事件对象就该像一封信：写好了，只读。
 *
 * <p><b>为什么没有"事件 ID"和"发布时间"？</b>
 * 真实系统当然需要，本示例故意不加——加了就必然要用时间戳或 UUID，
 * 而这两样都会让 Demo 的输出<b>不可复现</b>。
 * 本项目所有示例都遵守同一条纪律：<b>输出可复现优先于字段完整</b>。
 *
 * <p><b>为什么 {@code authorName} 和 {@code authorId} 都存？</b>
 * 因为通知文案要显示名字，而路由/去重要用 ID。
 * 只存其中一个都会让某一方去查一次库——而事件对象本来就在手里，
 * 顺手带上比让每个观察者各查一次更省。这是"事件应该自包含"的实践：
 * 观察者拿到事件就能干活，不需要再回头问发布者。
 *
 * @param type       事件类型
 * @param authorId   作者 ID，非空
 * @param authorName 作者显示名，非空
 * @param title      标题，非空
 * @param content    正文摘要，非空
 * @author design-patterns-pro
 */
public record Event(
        EventType type,
        String authorId,
        String authorName,
        String title,
        String content
) {

    /**
     * 紧凑构造器：五个字段全部非空，字符串还不允许是空白。
     *
     * <p><b>为什么连 {@code title} 都要查空白？</b>
     * 因为通知文案里会出现"《》"这样的书名号，
     * 一个空标题会打出"《》"——看起来像是排版 bug，实际是数据问题。
     * 在构造处拦住，比在五个观察者里各判一次划算。
     */
    public Event {
        Objects.requireNonNull(type, "事件类型不能为 null");
        requireText(authorId, "作者 ID");
        requireText(authorName, "作者名");
        requireText(title, "标题");
        content = (content == null) ? "" : content;
    }

    /**
     * 一行式描述，用于日志与提示文案。
     *
     * <p>格式：{@code [直播][实时] hgl：《今晚八点见》}。
     * 实时标记只对直播出现——这个判断用的是 {@link EventType#realtime()}，
     * 不是 {@code type == LIVE}，将来加别的实时类型不用改这里。
     *
     * @return 形如 {@code [文章] hgl：《标题》} 的描述
     */
    public String describe() {
        String realtimeMark = type.realtime() ? "[实时]" : "";
        return "[" + type.label() + "]" + realtimeMark + " "
                + authorName + "：《" + title + "》";
    }

    /**
     * 通知正文的一行摘要：{@code 内容}，空内容时显示 {@code （无正文）}。
     *
     * @return 摘要文本
     */
    public String summary() {
        return content.isEmpty() ? "（无正文）" : content;
    }

    /**
     * 校验一个字符串既非 null 也非空白。
     *
     * @param value 待校验值
     * @param name  字段名，用于报错信息
     */
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
