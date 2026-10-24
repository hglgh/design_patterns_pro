package com.patterns.behavioral.visitor.document;

import java.util.List;

/**
 * HTML 片段（不可变值对象）。
 *
 * <p><b>它为什么存在？</b>
 * 因为 {@link HtmlExportVisitor} 必须返回点东西，而不是往控制台打。
 * 原项目里 {@code HtmlExportVisitor.visit(...)} 直接
 * {@code System.out.println("<html>...")}，导致：</p>
 * <ul>
 *   <li>调用方拿不到结果，无法断言导出的 HTML 是否符合预期（探针 V1）；</li>
 *   <li>想导出到文件或 HTTP 响应，只能重定向 {@code System.out}；</li>
 *   <li>想把三份文档合成一页，没有任何可组合的中间物。</li>
 * </ul>
 *
 * <p>有了这个不可变片段，{@code DocumentBundle} 才能把三次 visit 的返回值
 * 用 {@link #concat} 折叠成一个完整页面 —— 这正是 void 版本做不到的事。</p>
 *
 * <p><b>为什么是 record 而不是 StringBuilder 包装？</b>
 * StringBuilder 是可变对象，传出去之后任何人都能改。
 * 用 {@code record HtmlFragment(String value)} 每次拼接都产生新实例，
 * 中间的半成品不会被后续操作污染。</p>
 *
 * @param value HTML 文本，不能为 null（可以为空串表示「空片段」）
 */
public record HtmlFragment(String value) {

    /**
     * 紧凑构造器：只拒绝 null，允许空串。
     */
    public HtmlFragment {
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
    }

    /**
     * 空片段，作为折叠的初始值。
     *
     * @return 内容为空串的片段
     */
    public static HtmlFragment empty() {
        return new HtmlFragment("");
    }

    /**
     * 顺序拼接到当前片段末尾。
     *
     * @param next 后一个片段，不能为 null
     * @return 新片段，{@code this} 与 {@code next} 都不会被修改
     */
    public HtmlFragment concat(HtmlFragment next) {
        if (next == null) {
            throw new IllegalArgumentException("next 不能为 null");
        }
        if (next.value.isEmpty()) {
            return this;
        }
        if (value.isEmpty()) {
            return next;
        }
        return new HtmlFragment(value + next.value);
    }

    /**
     * 把多个片段依次拼接成一个。
     *
     * <p>内部用一次 StringBuilder 预分配，避免连锁相加产生 O(n²) 拷贝。</p>
     *
     * @param fragments 片段列表，不能为 null
     * @return 合并后的片段
     */
    public static HtmlFragment join(List<HtmlFragment> fragments) {
        if (fragments == null) {
            throw new IllegalArgumentException("fragments 不能为 null");
        }
        int estimated = 0;
        for (HtmlFragment fragment : fragments) {
            if (fragment == null) {
                throw new IllegalArgumentException("fragments 中不能有 null 元素");
            }
            estimated += fragment.value.length();
        }
        StringBuilder joined = new StringBuilder(estimated);
        for (HtmlFragment fragment : fragments) {
            joined.append(fragment.value);
        }
        return new HtmlFragment(joined.toString());
    }

    /**
     * 片段长度，便于在 Demo 里展示规模。
     *
     * @return 字符数
     */
    public int length() {
        return value.length();
    }

    /**
     * 是否为空片段。
     *
     * @return 长度为 0 时返回 true
     */
    public boolean isEmpty() {
        return value.isEmpty();
    }
}
