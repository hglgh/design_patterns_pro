package com.patterns.structural.decorator;

import java.util.Set;

/**
 * 具体装饰器 D：字体颜色。<b>带参数（自身状态）的装饰器</b>。
 *
 * <p><b>这个类最能体现装饰器优于继承的地方。</b>
 * 如果用继承实现"红色加粗文本""蓝色加粗文本""绿色加粗文本"…
 * 颜色有 N 种就要 N 个类，再乘以其他样式的组合，类数量彻底失控。
 * 而装饰器只需<b>一个类 + 一个构造参数</b>：
 * <pre>{@code
 * new ColorDecorator(text, "red")
 * new ColorDecorator(text, "blue")
 * new ColorDecorator(text, "#336699")
 * }</pre>
 * 同一份代码，运行期产生无数种装饰效果。
 *
 * @author design-patterns-pro
 */
public class ColorDecorator extends TextDecorator {

    /**
     * 常用颜色名白名单，用于给学习者一个"参数校验"的示范。
     * 也支持十六进制色值（如 {@code "#ff0000"}）。
     */
    private static final Set<String> NAMED_COLORS =
            Set.of("red", "blue", "green", "black", "gray", "orange", "purple");

    /**
     * 装饰器自己的状态：颜色值。
     *
     * <p>注意它与 {@code component}（被装饰者）是两类不同的东西：
     * {@code component} 是"我要包装谁"，{@code color} 是"我用什么方式包装"。
     */
    private final String color;

    /**
     * @param component 被装饰对象
     * @param color     颜色名（如 "red"）或十六进制色值（如 "#ff0000"）
     */
    public ColorDecorator(TextComponent component, String color) {
        super(component);
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("颜色不能为空");
        }
        boolean isNamed = NAMED_COLORS.contains(color.toLowerCase());
        boolean isHex = color.matches("#[0-9a-fA-F]{6}");
        if (!isNamed && !isHex) {
            throw new IllegalArgumentException("不支持的颜色值：" + color
                    + "，请使用常用颜色名或 #RRGGBB 格式");
        }
        this.color = color;
    }

    @Override
    public String render() {
        return "<span style=\"color:" + color + "\">" + super.render() + "</span>";
    }
}
