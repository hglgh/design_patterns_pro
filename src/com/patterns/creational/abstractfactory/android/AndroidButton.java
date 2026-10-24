package com.patterns.creational.abstractfactory.android;

import com.patterns.creational.abstractfactory.Button;

/**
 * 具体产品：Android 风格按钮。
 *
 * @author design-patterns-pro
 */
public class AndroidButton implements Button {
    @Override
    public void render() {
        System.out.println("[Android] 渲染 Material Design 风格按钮");
    }
}
