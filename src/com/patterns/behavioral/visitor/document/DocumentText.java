package com.patterns.behavioral.visitor.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档模型的入参校验与通用文本工具。
 *
 * <p>原项目把校验完全交给调用方：{@code new ExcelDocument(null)} 能构造成功，
 * 直到某个访问者开始遍历时才抛 NPE（探针 V7）；单元格为 null 时
 * 直接渲染出字符串 "null"（探针 V7）。这里把校验收到构造器里，
 * 让非法对象根本建不出来。</p>
 *
 * <p><b>为什么是 package-private？</b>
 * 它不是模式的一部分，只是三个 record 共用的实现细节。
 * 对外暴露一个「工具类」会诱使调用方绕过构造器直接造数据结构，
 * 反而削弱封装。</p>
 */
final class DocumentText {

    /**
     * 校验必须存在且非空白的文本。
     *
     * @param value 待校验文本
     * @param field 字段名，用于异常消息定位
     * @return 原文本
     * @throws IllegalArgumentException 为 null 或全空白
     */
    static String requireText(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为 null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白字符串");
        }
        return value;
    }

    /**
     * 把调用方传进来的二维结构规范化成不可变的嵌套列表。
     *
     * <p>做三件事：逐格拒绝 null、逐行拒绝空行、逐层复制成不可变结构。
     * 第三步是关键：复制之后，调用方手里原来的 {@code List} 再怎么改，
     * 都影响不到文档内容（原项目探针 V2 正是因为没有这一步，
     * 外部改数组就把文档改了）。</p>
     *
     * @param rows 调用方给的表格，不能为 null 或空
     * @return 不可变的行列表，每行也是不可变列表
     * @throws IllegalArgumentException 结构非法
     */
    static List<List<String>> normalizeTable(List<List<String>> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("rows 不能为 null");
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows 至少要有一行");
        }
        List<List<String>> copy = new ArrayList<>(rows.size());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int humanRow = rowIndex + 1;
            if (row == null) {
                throw new IllegalArgumentException("第 " + humanRow + " 行为 null");
            }
            if (row.isEmpty()) {
                throw new IllegalArgumentException("第 " + humanRow + " 行没有任何单元格");
            }
            List<String> cells = new ArrayList<>(row.size());
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                String cell = row.get(columnIndex);
                if (cell == null) {
                    throw new IllegalArgumentException(
                            "第 " + humanRow + " 行第 " + (columnIndex + 1) + " 列为 null");
                }
                cells.add(cell);
            }
            copy.add(List.copyOf(cells));
        }
        return List.copyOf(copy);
    }

    /**
     * 取表格列数。行宽不一致时返回最大值，便于调用方发现数据问题。
     *
     * @param rows 已规范化的表格
     * @return 最大列数
     */
    static int columnCount(List<List<String>> rows) {
        int max = 0;
        for (List<String> row : rows) {
            max = Math.max(max, row.size());
        }
        return max;
    }

    /**
     * 判断表格是否为标准的矩形（每行列数一致）。
     *
     * @param rows 已规范化的表格
     * @return 每行列数都一样时返回 true
     */
    static boolean isRectangular(List<List<String>> rows) {
        int expected = rows.get(0).size();
        for (List<String> row : rows) {
            if (row.size() != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * HTML 转义。单元格里出现 {@code <script>} 时不能原样写进页面。
     *
     * <p>原项目没有做任何转义，Excel 里放一个 {@code <td>} 就能把页面结构打乱。</p>
     *
     * @param raw 原始文本
     * @return 转义后的文本
     */
    static String escapeHtml(String raw) {
        StringBuilder escaped = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private DocumentText() {
    }
}
