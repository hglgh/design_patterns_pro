package com.patterns.structural.decorator;

/**
 * 具体装饰器 C：下划线。
 *
 * @author design-patterns-pro
 */
public class UnderlineDecorator extends TextDecorator {

    public UnderlineDecorator(TextComponent component) {
        super(component);
    }

    @Override
    public String render() {
        return "<u>" + super.render() + "</u>";
    }
}
