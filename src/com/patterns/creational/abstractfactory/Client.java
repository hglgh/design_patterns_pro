package com.patterns.creational.abstractfactory;

import com.patterns.creational.abstractfactory.factory.AndroidUIFactory;
import com.patterns.creational.abstractfactory.factory.WebUIFactory;

/**
 * 抽象工厂模式客户端演示。
 *
 * <p>通过切换不同的 {@link UIFactory}，让同一段客户端代码渲染出
 * 不同风格（Android / Web）的 UI 组件族。
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        System.out.println("========== 抽象工厂模式演示 ==========");
        System.out.println();

        System.out.println("--- 场景 1：Android 应用 ---");
        renderUI(new AndroidUIFactory());

        System.out.println();
        System.out.println("--- 场景 2：Web 应用 ---");
        renderUI(new WebUIFactory());

        System.out.println();
        System.out.println("========== 扩展：新增 iOS 产品族 ==========");
        renderUI(new IOSUIFactory());
    }

    /**
     * 使用方：只依赖抽象工厂，不关心具体产品族。
     *
     * @param factory UI 工厂
     */
    private static void renderUI(UIFactory factory) {
        Button button = factory.createButton();
        TextBox textBox = factory.createTextBox();
        Image image = factory.createImage();

        button.render();
        textBox.render();
        image.render();
    }
}
