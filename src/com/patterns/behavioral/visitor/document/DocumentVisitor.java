package com.patterns.behavioral.visitor.document;

/**
 * 访问者接口（Visitor）—— 每种「操作」对应一个实现类。
 *
 * <p><b>和原项目的第一个区别：有返回值。</b>
 * 原项目的 {@code DocumentVisitor} 三个方法都是 void，
 * 于是导出型访问者只能在内部 {@code System.out.println}，
 * 调用方拿不到生成的字符串，既不能断言也无法二次处理（见探针 V1）。
 * 这里把结果类型提成接口参数 R：{@code HtmlExportVisitor} 实现
 * {@code DocumentVisitor<HtmlFragment>}，{@code StatisticsVisitor} 实现
 * {@code DocumentVisitor<DocumentStatistics>}，各类操作各取所需。</p>
 *
 * <p><b>为什么不用一个 {@code Object visit(Document)}？</b>
 * 那样调用方要强转，等于把「访问者知道自己要处理哪种元素」
 * 这个编译期约束退化成了运行期 {@code ClassCastException}。
 * 三个重载方法是访问者模式的核心语法糖：{@code accept} 里传的是具体类型
 * （{@code this}），编译器会自动选中正确的重载，不需要 instanceof。</p>
 *
 * <p><b>新增一个操作要做什么？</b>
 * 只新增一个实现类，{@code Document} 及其三个实现一字不动。
 * 这就是访问者模式存在的唯一理由。</p>
 *
 * @param <R> 这个访问者对所有文档类型统一产出的结果类型
 * @see Document
 */
public interface DocumentVisitor<R> {

    /**
     * 访问 PDF 文档。
     *
     * @param document PDF 文档，不为 null
     * @return 本类型文档的访问结果
     */
    R visit(PdfDocument document);

    /**
     * 访问 Word 文档。
     *
     * @param document Word 文档，不为 null
     * @return 本类型文档的访问结果
     */
    R visit(WordDocument document);

    /**
     * 访问表格文档。
     *
     * @param document 表格文档，不为 null
     * @return 本类型文档的访问结果
     */
    R visit(SpreadsheetDocument document);
}
