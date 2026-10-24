package com.patterns.behavioral.visitor.document;

import java.util.List;

/**
 * 文档统计访问者（具体访问者，ConcreteVisitor）。
 *
 * <p><b>它返回的是"一份文档的贡献"，不是"全部文档的总和"。</b>
 * 这是本重构最重要的设计决定之一。
 * 原项目的访问者习惯于自己累加（{@code totalPrice += ...}），
 * 结果是访问者变成有状态的累加器，复用即出错（探针 V3）。</p>
 *
 * <p>这里把"逐份统计"和"求和"拆开：</p>
 * <ul>
 *   <li>访问者只回答"这一份文档有几分、几页、几字"；</li>
 *   <li>{@link DocumentBundle#acceptAndFold} 负责把答案折叠起来。</li>
 * </ul>
 * <p>好处是这个访问者永远无状态，跑一百遍结果都一样，
 * 而且同一批统计结果还能拿去做别的事（求平均、取最大值、
 * 与上一次快照比较增量）。</p>
 *
 * <p><b>它不打印任何东西。</b>
 * 打印是调用方的事。访问者一旦打印，就无法在测试里断言，
 * 也无法同时输出到控制台和日志文件。</p>
 */
public final class StatisticsVisitor implements DocumentVisitor<DocumentStatistics> {

    @Override
    public DocumentStatistics visit(PdfDocument document) {
        return new DocumentStatistics(
                1,  // documents
                1,  // pdfs
                0,  // words
                0,  // spreadsheets
                document.pageCount(),
                document.bodyLength(),
                0);
    }

    @Override
    public DocumentStatistics visit(WordDocument document) {
        return new DocumentStatistics(
                1,
                0,
                1,
                0,
                0,
                document.bodyLength(),
                0);
    }

    @Override
    public DocumentStatistics visit(SpreadsheetDocument document) {
        List<List<String>> rows = document.rows();
        int characters = 0;
        for (List<String> row : rows) {
            for (String cell : row) {
                characters += cell.length();
            }
        }
        return new DocumentStatistics(
                1,
                0,
                0,
                1,
                0,
                characters,
                document.cellCount());
    }
}
