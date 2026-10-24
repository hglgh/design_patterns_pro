package com.patterns.structural.decorator;

/**
 * 具体装饰器 A：加粗。
 *
 * <p>装饰器的标准写法只有三步：
 * <ol>
 *   <li>继承 {@link TextDecorator}，构造器把 component 交给父类保存；</li>
 *   <li>覆盖 {@link #render()}；</li>
 *   <li>调用 {@code super.render()} 拿到<b>内层的渲染结果</b>，在它外面加自己的料。</li>
 * </ol>
 *
 * <p><b>第 3 步是灵魂</b>。如果写成 {@code return "<b>" + component + "</b>";}
 * 会调用 toString() 得到类名而不是渲染结果；
 * 如果写成 {@code return "<b>加粗文本</b>";} 则完全丢失了内层内容，装饰链在这里断掉。
 *
 * @author design-patterns-pro
 */
public class BoldDecorator extends TextDecorator {

    public BoldDecorator(TextComponent component) {
        super(component);
    }

    @Override
    public String render() {
        return "<b>" + super.render() + "</b>";
    }
}
