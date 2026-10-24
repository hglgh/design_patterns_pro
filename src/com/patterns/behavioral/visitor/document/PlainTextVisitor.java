package com.patterns.behavioral.visitor.document;

import java.util.List;

/**
 * 纯文本导出访问者（具体访问者，ConcreteVisitor）。
 *
 * <p><b>它的存在是为了证明一件事：加一种导出格式 = 加一个类。</b>
 * 新增本类时，{@code Document}、{@link PdfDocument}、
 * {@link WordDocument}、{@link SpreadsheetDocument} 都没有任何改动，
 * 也没有为了「支持纯文本」而在元素里加一个 {@code toPlainText()} 方法。
 * 如果换成「在每个文档类里加一个方法」的做法，那么每加一种格式，
 * 三个文档类都要改一遍，而且改完还得重新测试已经稳定的 PDF 解析逻辑。</p>
 *
 * <p><b>返回 String 而不是自定义类型，合适吗？</b>
 * 合适。纯文本就是字符串，没有更多结构可封装；
 * 强行包一个 {@code PlainText} record 只会增加一层无意义的解包。
 * 对比 {@link HtmlFragment}：它需要 {@code concat/join} 这类组合能力，
 * 才值得独立成类型。判断标准是「有没有行为要挂上去」。</p>
 */
public final class PlainTextVisitor implements DocumentVisitor<String> {

    @Override
    public String visit(PdfDocument document) {
        return "[PDF] " + document.title() + " (" + document.pageCount() + " 页)\n"
                + document.body() + "\n";
    }

    @Override
    public String visit(WordDocument document) {
        return "[Word] " + document.title() + " (" + document.bodyLength() + " 字)\n"
                + document.body() + "\n";
    }

    @Override
    public String visit(SpreadsheetDocument document) {
        StringBuilder text = new StringBuilder(256);
        text.append("[表格] ").append(document.title())
                .append(" (").append(document.rowCount()).append(" 行 × ")
                .append(document.columnCount()).append(" 列)\n");

        List<List<String>> rows = document.rows();
        for (List<String> row : rows) {
            // 用 " | " 分隔而不是空格对齐：中文字符在等宽字体里占两个宽度，
            // 按 String.length() 补空格反而会错位。分隔符方案在任何字体下都稳定。
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                if (columnIndex > 0) {
                    text.append(" | ");
                }
                text.append(row.get(columnIndex));
            }
            text.append('\n');
        }
        return text.toString();
    }
}
