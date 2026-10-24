package com.patterns.structural.decorator;

/**
 * 抽象组件（Component）：定义"文本"的统一契约。
 *
 * <p><b>为什么需要这个接口？</b>
 * 装饰器模式的成立前提是：装饰器与被装饰者<b>类型相同</b>。
 * 只有 {@code BoldDecorator} 也实现 {@code TextComponent}，
 * 才能写出 {@code new BoldDecorator(new ColorDecorator(new PlainText("hi")))}
 * 这样层层嵌套的结构，而客户端拿到的仍然是 {@code TextComponent}，
 * 调用 {@code render()} 时毫无感知。
 *
 * <p><b>如果用继承会怎样？</b>
 * 想支持"加粗 + 变色 + 斜体"的所有组合，需要
 * BoldText、ColorText、ItalicText、BoldColorText、BoldItalicText、
 * ColorItalicText、BoldColorItalicText… 2³-1 = 7 个类。
 * 再加一种下划线样式，变成 2⁴-1 = 15 个类。<b>组合数呈指数爆炸</b>。
 * 装饰器只需 4 个装饰器类，且可以任意顺序、任意次数叠加。
 *
 * @author design-patterns-pro
 * @see PlainText     具体组件：被装饰的原始对象
 * @see TextDecorator 装饰器基类：所有具体装饰器的父类
 */
public interface TextComponent {

    /**
     * 渲染文本为 HTML 片段。
     *
     * @return 渲染结果，永不为 null
     */
    String render();
}
