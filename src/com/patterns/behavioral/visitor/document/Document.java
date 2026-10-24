package com.patterns.behavioral.visitor.document;

/**
 * 元素接口（Element）—— 访问者模式中的「被访问者」。
 *
 * <p>整个模式里只有这一个方法，但它是双重分派的第一跳：
 * 调用方只知道自己是 {@code Document}，运行时由具体实现决定走哪个 {@code accept}，
 * 再由 {@code accept} 里的 {@code visitor.visit(this)} 完成第二跳。
 * 两跳合起来，才能在不改任何 Document 实现的前提下新增一种操作。</p>
 *
 * <p><b>为什么用 sealed？</b>
 * 访问者模式的经典代价是「加新元素类型要改所有访问者」。
 * sealed 把这个代价变成编译期可见：只要新增一个 permits 子类型，
 * 所有对 {@code Document} 做穷尽 switch 的地方都会立刻编译失败，
 * 而不是等到线上才发现某个访问者漏了一个分支。</p>
 *
 * <p><b>为什么 accept 是泛型方法而不是泛型接口？</b>
 * 如果写成 {@code interface Document<R>}，那么「文档」这个数据类型的泛型参数
 * 会跟着文档本身一辈子，而 R 其实是「这一次访问要产出什么」，
 * 属于访问者的属性，不属于文档。把 R 放在方法上（{@code <R> R accept}），
 * 同一份文档才能先后被 {@code DocumentVisitor<String>} 和
 * {@code DocumentVisitor<DocumentStatistics>} 访问。</p>
 *
 * @see DocumentVisitor
 */
public sealed interface Document permits PdfDocument, WordDocument, SpreadsheetDocument {

    /**
     * 文档标题，三种文档都有，且都不能为空白。
     *
     * @return 非空白的标题
     */
    String title();

    /**
     * 接受一个访问者，并把「我是哪种文档」这个运行时信息交给它。
     *
     * <p>实现必须是 {@code return visitor.visit(this);}，不要写成
     * {@code visitor.visit((Document) this)} —— 后者会找不到重载而编译失败，
     * 这正好说明了为什么访问者接口要对每个具体类型各写一个 visit。</p>
     *
     * @param visitor 访问者，不能为 null
     * @param <R>     访问者这次产出的结果类型
     * @return 访问者针对本类型文档给出的结果
     */
    <R> R accept(DocumentVisitor<R> visitor);
}
