package com.patterns.creational.abstractfactory.web;

import com.patterns.creational.abstractfactory.TextBox;

/**
 * 具体产品：Web 风格文本框。
 *
 * @author design-patterns-pro
 */
public class WebTextBox implements TextBox {
    @Override
    public void render() {
        System.out.println("[Web] 渲染 HTML input 文本框");
    }
}
