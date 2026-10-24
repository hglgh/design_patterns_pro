package com.patterns.behavioral.visitor.document;

import java.util.List;

/**
 * HTML 导出访问者（具体访问者，ConcreteVisitor）。
 *
 * <p><b>它和原项目同名类最大的不同：不打印。</b>
 * 原实现每个分支都是 {@code System.out.println(...)}，
 * 这个类因此无法被单元测试、无法导出到文件、无法嵌入 HTTP 响应。
 * 现在它只负责「把一种文档翻译成 HTML」，返回
 * {@link HtmlFragment}，由调用方决定写到哪里。</p>
 *
 * <p><b>它是无状态的。</b>
 * 类里没有任何字段，所以可以安全地被复用、被并发共享。
 * 原项目的 {@code PriceCalculatorVisitor} 用实例字段累加总价，
 * 同一个对象跑两次结果翻倍（探针 V3）；这里从根本上没有这个可能。</p>
 *
 * <p><b>它做了 HTML 转义。</b>
 * 原项目把单元格内容直接拼进标签，一个含 {@code <} 的单元格
 * 就能破坏页面结构。这里统一走 {@link DocumentText#escapeHtml}。</p>
 */
public final class HtmlExportVisitor implements DocumentVisitor<HtmlFragment> {

    private static final String TABLE_STYLE = "border='1' cellspacing='0' cellpadding='4'";

    @Override
    public HtmlFragment visit(PdfDocument document) {
        StringBuilder html = new StringBuilder(256);
        html.append("<section class='pdf'>\n");
        html.append("  <h2>").append(DocumentText.escapeHtml(document.title())).append("</h2>\n");
        html.append("  <p class='meta'>PDF · ").append(document.pageCount()).append(" 页</p>\n");
        html.append("  <pre>").append(DocumentText.escapeHtml(document.body())).append("</pre>\n");
        html.append("</section>\n");
        return new HtmlFragment(html.toString());
    }

    @Override
    public HtmlFragment visit(WordDocument document) {
        StringBuilder html = new StringBuilder(256);
        html.append("<section class='word'>\n");
        html.append("  <h2>").append(DocumentText.escapeHtml(document.title())).append("</h2>\n");
        html.append("  <p class='meta'>Word · ").append(document.bodyLength()).append(" 字</p>\n");
        html.append("  <p>").append(DocumentText.escapeHtml(document.body())).append("</p>\n");
        html.append("</section>\n");
        return new HtmlFragment(html.toString());
    }

    @Override
    public HtmlFragment visit(SpreadsheetDocument document) {
        StringBuilder html = new StringBuilder(512);
        html.append("<section class='sheet'>\n");
        html.append("  <h2>").append(DocumentText.escapeHtml(document.title())).append("</h2>\n");
        html.append("  <p class='meta'>表格 · ").append(document.rowCount())
                .append(" 行 × ").append(document.columnCount()).append(" 列</p>\n");
        html.append("  <table ").append(TABLE_STYLE).append(">\n");

        List<List<String>> rows = document.rows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            // 第一行当表头用 th，其余用 td —— 这是唯一需要「知道行号」的地方，
            // 放在访问者里比放在文档里合适：别的访问者可能根本不关心表头。
            String cellTag = (rowIndex == 0) ? "th" : "td";
            html.append("    <tr>");
            for (String cell : rows.get(rowIndex)) {
                html.append('<').append(cellTag).append('>')
                        .append(DocumentText.escapeHtml(cell))
                        .append("</").append(cellTag).append('>');
            }
            html.append("</tr>\n");
        }

        html.append("  </table>\n");
        html.append("</section>\n");
        return new HtmlFragment(html.toString());
    }
}
