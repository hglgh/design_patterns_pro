package com.patterns.structural.decorator;

/**
 * 具体装饰器 B：斜体。
 *
 * <p>注意它与 {@link BoldDecorator} 的代码几乎一模一样，只有 HTML 标签不同。
 * 这不是冗余，而是装饰器模式的正常形态——<b>每种功能一个独立的小类</b>，
 * 每个类只干一件事（单一职责），可以独立开发、独立测试、自由组合。
 *
 * @author design-patterns-pro
 */
public class ItalicDecorator extends TextDecorator {

    public ItalicDecorator(TextComponent component) {
        super(component);
    }

    @Override
    public String render() {
        return "<i>" + super.render() + "</i>";
    }
}
