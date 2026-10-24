package com.patterns.creational.abstractfactory.factory;

import com.patterns.creational.abstractfactory.Button;
import com.patterns.creational.abstractfactory.Image;
import com.patterns.creational.abstractfactory.TextBox;
import com.patterns.creational.abstractfactory.UIFactory;
import com.patterns.creational.abstractfactory.android.AndroidButton;
import com.patterns.creational.abstractfactory.android.AndroidImage;
import com.patterns.creational.abstractfactory.android.AndroidTextBox;

/**
 * 具体工厂：Android UI 工厂。
 *
 * <p>负责创建"Android 产品族"的全部组件（Button / TextBox / Image）。
 *
 * @author design-patterns-pro
 */
public class AndroidUIFactory implements UIFactory {

    @Override
    public Button createButton() {
        return new AndroidButton();
    }

    @Override
    public TextBox createTextBox() {
        return new AndroidTextBox();
    }

    @Override
    public Image createImage() {
        return new AndroidImage();
    }
}
