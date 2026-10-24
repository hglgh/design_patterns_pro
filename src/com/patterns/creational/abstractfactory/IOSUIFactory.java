package com.patterns.creational.abstractfactory;

/**
 * 扩展演示：iOS UI 工厂（新增产品族）。
 *
 * <p>用于演示"开闭原则"：新增一个产品族，只需新增
 * "具体工厂 + 一族具体产品"，不需要修改 {@link Client} 等使用方代码。
 *
 * @author design-patterns-pro
 */
class IOSUIFactory implements UIFactory {

    @Override
    public Button createButton() {
        return new IOSButton();
    }

    @Override
    public TextBox createTextBox() {
        return new IOSTextBox();
    }

    @Override
    public Image createImage() {
        return new IOSImage();
    }
}

/** iOS 风格按钮。 */
class IOSButton implements Button {
    @Override
    public void render() {
        System.out.println("[iOS] 渲染 UIKit 风格按钮");
    }
}

/** iOS 风格文本框。 */
class IOSTextBox implements TextBox {
    @Override
    public void render() {
        System.out.println("[iOS] 渲染 UITextField 文本框");
    }
}

/** iOS 风格图片。 */
class IOSImage implements Image {
    @Override
    public void render() {
        System.out.println("[iOS] 渲染 UIImageView 图片");
    }
}
