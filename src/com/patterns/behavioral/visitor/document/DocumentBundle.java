package com.patterns.behavioral.visitor.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * 文档集合（对象结构，ObjectStructure）。
 *
 * <p>访问者模式里真正负责"遍历"的不是访问者，也不是元素，
 * 而是这个对象结构。原项目把遍历写在一个 for 循环里，
 * 每次都要自己写一遍；这里把它收进一个类，
 * 并提供两种调用方式。</p>
 *
 * <p><b>为什么遍历要放在对象结构里？</b>
 * 因为「有哪些文档」和「怎么遍历」是两件事，
 * 而访问者只应该关心「遇到一份文档该算什么」。
 * 一旦把 for 写在访问者里，访问者就同时承担了遍历职责，
 * 换成树形结构或分页加载时每个访问者都要改。</p>
 *
 * <p><b>方法为什么是泛型的？</b>
 * 因为同一个集合要被
 * {@code DocumentVisitor<HtmlFragment>}、
 * {@code DocumentVisitor<String>}、
 * {@code DocumentVisitor<DocumentStatistics>} 访问，
 * 返回的结果类型各不相同。泛型方法让这三种调用共用一份遍历代码。</p>
 */
public final class DocumentBundle {

    private final List<Document> documents;

    /**
     * 构造集合。
     *
     * <p>构造时立刻复制并拒绝 null 元素 —— 与 {@link SpreadsheetDocument}
     * 的做法一致：非法状态在构造期暴露，而不是等到遍历时抛出
     * 一个不知道第几个元素出问题的 NPE（对应探针 V8 的同一类问题）。</p>
     *
     * @param documents 文档列表，不能为 null
     * @throws IllegalArgumentException 列表含 null 元素
     */
    public DocumentBundle(List<Document> documents) {
        if (documents == null) {
            throw new IllegalArgumentException("documents 不能为 null");
        }
        List<Document> copy = new ArrayList<>(documents.size());
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            if (document == null) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 份文档为 null");
            }
            copy.add(document);
        }
        this.documents = List.copyOf(copy);
    }

    /**
     * 文档份数。
     *
     * @return 份数
     */
    public int size() {
        return documents.size();
    }

    /**
     * 只读的文档列表。
     *
     * @return 不可变列表
     */
    public List<Document> documents() {
        return documents;
    }

    /**
     * 让每个文档都接受同一个访问者，按顺序收集结果。
     *
     * <p>适合「每个元素产出一个独立结果」的场景，
     * 例如导出三份 HTML 片段，再决定怎么排版。</p>
     *
     * @param visitor 访问者，不能为 null
     * @param <R>     结果类型
     * @return 与文档顺序一致的不可变结果列表
     */
    public <R> List<R> acceptAll(DocumentVisitor<R> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        List<R> results = new ArrayList<>(documents.size());
        for (Document document : documents) {
            results.add(document.accept(visitor));
        }
        return List.copyOf(results);
    }

    /**
     * 让每个文档都接受同一个访问者，并立即把结果折叠成一个值。
     *
     * <p>适合「最终只要一个总数」的场景，例如统计全部文档的字数、
     * 把所有 HTML 片段拼成一页。</p>
     *
     * <p>注意折叠的方向：{@code folder.apply(累积值, 本份结果)}。
     * 累积值由调用方提供初值（{@link DocumentStatistics#empty()}、
     * {@link HtmlFragment#empty()}），所以调用方完全控制
     * 是「求和」「拼接」还是「取最大」。</p>
     *
     * @param visitor  访问者，不能为 null
     * @param identity 折叠初值
     * @param folder   折叠函数，不能为 null
     * @param <R>      结果类型
     * @return 折叠后的最终值
     */
    public <R> R acceptAndFold(DocumentVisitor<R> visitor, R identity, BinaryOperator<R> folder) {
        Objects.requireNonNull(visitor, "visitor");
        Objects.requireNonNull(folder, "folder");
        R accumulator = identity;
        for (Document document : documents) {
            accumulator = folder.apply(accumulator, document.accept(visitor));
        }
        return accumulator;
    }
}
