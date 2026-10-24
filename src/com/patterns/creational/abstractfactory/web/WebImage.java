package com.patterns.creational.abstractfactory.web;

import com.patterns.creational.abstractfactory.Image;

/**
 * 具体产品：Web 风格图片组件。
 *
 * @author design-patterns-pro
 */
public class WebImage implements Image {
    @Override
    public void render() {
        System.out.println("[Web] 渲染 <img> 标签图片");
    }
}
