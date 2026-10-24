package com.patterns.structural.decorator;

/**
 * 具体组件（ConcreteComponent）：没有任何样式的纯文本，装饰链的<b>起点</b>。
 *
 * <p><b>为什么必须有这个类？</b>
 * 装饰器只是"包装纸"，包装纸里必须包着东西。
 * {@code PlainText} 就是那个"东西"——它提供真正的数据（content），
 * 是所有装饰器层层加工的最内层原料。
 * 如果删掉它，{@code new BoldDecorator(null)} 会在 render 时 NPE。
 *
 * <p><b>为什么用 record？</b>
 * 纯文本就是"一个不可变的字符串"，record 一行搞定，
 * 还自带 equals/hashCode/toString（便于测试与调试）。
 *
 * @param content 原始文本内容
 * @author design-patterns-pro
 */
public record PlainText(String content) implements TextComponent {

    public PlainText {
        if (content == null) {
            throw new IllegalArgumentException("文本内容不能为 null");
        }
    }

    /**
     * 直接返回原始内容，不加任何修饰。
     *
     * <p>这是<b>递归的终点</b>：装饰链一路向内委托，最终落到这里返回真实数据。
     */
    @Override
    public String render() {
        return content;
    }
}
