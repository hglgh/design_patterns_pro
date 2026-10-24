package com.patterns.behavioral.visitor.document;

/**
 * PDF 文档（具体元素，ConcreteElement）。
 *
 * <p>原项目里它是 {@code record PDFDocument(String content)} —— 用类名
 * 大写缩写作为类型名，既不符合 Java 命名规范，也让 {@code visit(PDFDocument)}
 * 看起来像一个静态方法调用。这里改成 {@code PdfDocument}。</p>
 *
 * <p><b>record 在这里合适吗？</b>
 * 合适。文档是值：两个字段内容相同就是同一份文档，不需要身份标识，
 * 也不需要继承扩展。record 顺带给了 {@code equals/hashCode/toString}，
 * 让「同一批文档跑两次访问者结果是否相同」可以直接用 equals 断言。</p>
 *
 * @param title     标题，非空白
 * @param pageCount 页数，至少 1
 * @param body      正文，非空白
 */
public record PdfDocument(String title, int pageCount, String body) implements Document {

    /**
     * 紧凑构造器：在对象诞生前完成校验，非法参数对象建不出来。
     *
     * @throws IllegalArgumentException 任一字段非法
     */
    public PdfDocument {
        title = DocumentText.requireText(title, "title");
        body = DocumentText.requireText(body, "body");
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount 至少为 1，实际 " + pageCount);
        }
    }

    /**
     * 正文字符数，供统计型访问者使用。
     *
     * @return 正文字符数
     */
    public int bodyLength() {
        return body.length();
    }

    /**
     * {@inheritDoc}
     *
     * <p>这一行是双重分派的第二跳：{@code this} 的编译期类型就是
     * {@code PdfDocument}，所以 {@code visitor} 必然选中 {@code visit(PdfDocument)}，
     * 不需要任何 instanceof。</p>
     */
    @Override
    public <R> R accept(DocumentVisitor<R> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor 不能为 null");
        }
        return visitor.visit(this);
    }
}
