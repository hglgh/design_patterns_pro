package com.patterns.creational.abstractfactory.web;

import com.patterns.creational.abstractfactory.Button;

/**
 * 具体产品：Web 风格按钮。
 *
 * @author design-patterns-pro
 */
public class WebButton implements Button {
    @Override
    public void render() {
        System.out.println("[Web] 渲染 HTML/CSS 风格按钮");
    }
}
