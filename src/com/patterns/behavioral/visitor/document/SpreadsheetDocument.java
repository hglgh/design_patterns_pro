package com.patterns.behavioral.visitor.document;

import java.util.List;

/**
 * 表格文档（具体元素，ConcreteElement）。
 *
 * <p><b>它是原项目问题最集中的地方。</b>
 * 原实现是 {@code record ExcelDocument(String[][] table)}：
 * record 的访问器直接返回内部数组引用，调用方拿到之后
 * {@code table()[0][0] = "改掉"} 就把文档内容改了（探针 V2 已复现）。
 * 构造函数也不校验 null，{@code new ExcelDocument(null)} 一路通过，
 * 直到访问者遍历时才 NPE（探针 V7）。</p>
 *
 * <p><b>修法：在构造器里把二维结构规范化成不可变嵌套列表。</b>
 * 这样有两个好处：</p>
 * <ol>
 *   <li>不存在「内部数组」，也就没有泄漏引用的可能；
 *       调用方手里原来的 List 与文档彻底脱钩。</li>
 *   <li>读取 {@code rows()} 是零成本的 —— 返回的就是那个不可变列表，
 *       不需要每次防御性拷贝。这比「访问器里 clone 一份」更优：
 *       拷贝方案每次读都 O(n)，而不可变方案一次成型、永远安全。</li>
 * </ol>
 *
 * <p><b>为什么不用 {@code String[][]}？</b>
 * 数组是可变对象，且没有 {@code equals}（按引用比较），
 * 存进 record 后 {@code equals} 语义就错了。{@code List<List<String>>}
 * 配合 {@code List.copyOf} 既是值语义，又能保证不可变。</p>
 *
 * @param title 标题，非空白
 * @param rows  表格内容，至少一行一列，单元格不能为 null
 */
public record SpreadsheetDocument(String title, List<List<String>> rows) implements Document {

    /**
     * 紧凑构造器：校验标题，并把表格复制成不可变结构。
     *
     * <p>注意这里是给参数重新赋值，record 的紧凑构造器会把最终值写回字段。
     * 写成 {@code this.rows = ...} 反而编译不过。</p>
     *
     * @throws IllegalArgumentException 表格为空、含 null 行/单元格，或标题非法
     */
    public SpreadsheetDocument {
        title = DocumentText.requireText(title, "title");
        rows = DocumentText.normalizeTable(rows);
    }

    /**
     * 行数。
     *
     * @return 行数，至少 1
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * 列数，取各行列数的最大值。
     *
     * @return 列数
     */
    public int columnCount() {
        return DocumentText.columnCount(rows);
    }

    /**
     * 是否为规则矩形表。真实 Excel 常出现参差不齐的行，
     * 导出前先判断一下能让访问者决定怎么补齐。
     *
     * @return 每行列数一致时返回 true
     */
    public boolean isRectangular() {
        return DocumentText.isRectangular(rows);
    }

    /**
     * 单元格总数。
     *
     * @return 所有行单元格个数之和
     */
    public int cellCount() {
        int total = 0;
        for (List<String> row : rows) {
            total += row.size();
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(DocumentVisitor<R> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor 不能为 null");
        }
        return visitor.visit(this);
    }
}
