package com.patterns.creational.abstractfactory.factory;

import com.patterns.creational.abstractfactory.Button;
import com.patterns.creational.abstractfactory.Image;
import com.patterns.creational.abstractfactory.TextBox;
import com.patterns.creational.abstractfactory.UIFactory;
import com.patterns.creational.abstractfactory.web.WebButton;
import com.patterns.creational.abstractfactory.web.WebImage;
import com.patterns.creational.abstractfactory.web.WebTextBox;

/**
 * 具体工厂：Web UI 工厂。
 *
 * <p>负责创建"Web 产品族"的全部组件。
 *
 * @author design-patterns-pro
 */
public class WebUIFactory implements UIFactory {

    @Override
    public Button createButton() {
        return new WebButton();
    }

    @Override
    public TextBox createTextBox() {
        return new WebTextBox();
    }

    @Override
    public Image createImage() {
        return new WebImage();
    }
}
