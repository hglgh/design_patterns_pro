package com.patterns.creational.abstractfactory.android;

import com.patterns.creational.abstractfactory.Image;

/**
 * 具体产品：Android 风格图片组件。
 *
 * @author design-patterns-pro
 */
public class AndroidImage implements Image {
    @Override
    public void render() {
        System.out.println("[Android] 渲染 Material Design 风格图片");
    }
}
