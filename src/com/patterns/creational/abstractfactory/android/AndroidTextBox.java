package com.patterns.creational.abstractfactory.android;

import com.patterns.creational.abstractfactory.TextBox;

/**
 * 具体产品：Android 风格文本框。
 *
 * @author design-patterns-pro
 */
public class AndroidTextBox implements TextBox {
    @Override
    public void render() {
        System.out.println("[Android] 渲染 Material Design 风格文本框");
    }
}
