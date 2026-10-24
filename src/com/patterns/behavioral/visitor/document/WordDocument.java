package com.patterns.behavioral.visitor.document;

/**
 * Word 文档（具体元素，ConcreteElement）。
 *
 * <p>它和 {@link PdfDocument} 的差别只有「多了页数」这一个字段，
 * 但访问者里必须为它单独写一个 {@code visit} 重载 ——
 * 这不是冗余，而是访问者模式的机制本身：
 * 重载是靠编译期类型选中的，两个不同的具体类就必然需要两个重载。
 * 如果想省掉这个重载，就只能退化成 {@code visit(Document)} + instanceof，
 * 那就不是访问者模式了。</p>
 *
 * @param title 标题，非空白
 * @param body  正文，非空白
 */
public record WordDocument(String title, String body) implements Document {

    /**
     * 紧凑构造器：标题与正文都必须是有效文本。
     *
     * @throws IllegalArgumentException 任一字段非法
     */
    public WordDocument {
        title = DocumentText.requireText(title, "title");
        body = DocumentText.requireText(body, "body");
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
     */
    @Override
    public <R> R accept(DocumentVisitor<R> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor 不能为 null");
        }
        return visitor.visit(this);
    }
}
