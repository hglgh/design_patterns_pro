package com.patterns.behavioral.visitor.document;

/**
 * 文档统计结果（不可变值对象），由 {@link StatisticsVisitor} 逐份产出、
 * 再由 {@link DocumentBundle#acceptAndFold} 累加成总数。
 *
 * <p><b>为什么要一个这么"扁"的 record？</b>
 * 因为访问者的返回值必须是「可以相加的半成品」。
 * 如果访问者直接返回一个最终字符串，那么折叠时只能做字符串拼接，
 * 数字就没法再参与计算（比如"平均每页多少字"）。
 * 把统计结果建模成可加的数值对象，聚合方式就掌握在调用方手里：
 * 可以求和、求平均、取最大，而访问者只负责"一份文档贡献多少"。</p>
 *
 * <p><b>这是一个典型的"结果对象比访问者活得久"的例子。</b>
 * 访问者本身无状态、可丢弃；结果对象被调用方持有、可继续加工。</p>
 *
 * @param documents       文档份数
 * @param pdfs            PDF 份数
 * @param words           Word 份数
 * @param spreadsheets    表格份数
 * @param totalPages      PDF 总页数
 * @param totalBodyChars  正文总字符数（表格按所有单元格字符数之和计）
 * @param totalCells      表格单元格总数
 */
public record DocumentStatistics(int documents,
                                 int pdfs,
                                 int words,
                                 int spreadsheets,
                                 int totalPages,
                                 int totalBodyChars,
                                 int totalCells) {

    /**
     * 紧凑构造器：统计值不允许为负。
     *
     * <p>这类校验看似多余，但它能在折叠逻辑写错（比如误用减法）时
     * 立刻抛出，而不是悄悄产出一份负数的报表。</p>
     */
    public DocumentStatistics {
        requireNonNegative(documents, "documents");
        requireNonNegative(pdfs, "pdfs");
        requireNonNegative(words, "words");
        requireNonNegative(spreadsheets, "spreadsheets");
        requireNonNegative(totalPages, "totalPages");
        requireNonNegative(totalBodyChars, "totalBodyChars");
        requireNonNegative(totalCells, "totalCells");
    }

    /**
     * 折叠的初始值：什么都没统计到。
     *
     * @return 全零统计
     */
    public static DocumentStatistics empty() {
        return new DocumentStatistics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * 逐项相加，用于把多份文档的统计合并。
     *
     * @param other 另一份统计，不能为 null
     * @return 新的合并结果
     */
    public DocumentStatistics plus(DocumentStatistics other) {
        if (other == null) {
            throw new IllegalArgumentException("other 不能为 null");
        }
        return new DocumentStatistics(
                documents + other.documents,
                pdfs + other.pdfs,
                words + other.words,
                spreadsheets + other.spreadsheets,
                totalPages + other.totalPages,
                totalBodyChars + other.totalBodyChars,
                totalCells + other.totalCells);
    }

    /**
     * 单行摘要，供 Demo 直接打印。
     *
     * @return 形如 "3 份文档（PDF 1 / Word 1 / 表格 1） · 合计 12 页 · 正文 46 字 · 单元格 6 个"
     */
    public String summary() {
        return documents + " 份文档（PDF " + pdfs + " / Word " + words + " / 表格 " + spreadsheets + "）"
                + " · 合计 " + totalPages + " 页"
                + " · 正文 " + totalBodyChars + " 字"
                + " · 单元格 " + totalCells + " 个";
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能为负，实际 " + value);
        }
    }
}
