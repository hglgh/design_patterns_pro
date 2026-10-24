package com.patterns.creational.abstractfactory;

/**
 * 抽象工厂：UI 组件工厂。
 *
 * <p>定义"一族产品"的创建接口。每个具体工厂（Android / Web）
 * 负责创建属于自己产品族的全部组件。
 *
 * @author design-patterns-pro
 */
public interface UIFactory {
    Button createButton();
    TextBox createTextBox();
    Image createImage();
}
