package com.patterns.creational.prototype;

import java.util.ArrayList;
import java.util.List;

/**
 * 原型模式客户端演示。
 *
 * <p>展示三个核心能力：
 * <ol>
 *   <li>克隆 + 深拷贝（避免共享引用）。</li>
 *   <li>原型注册表（按 key 取原型并克隆）。</li>
 *   <li>对比"浅拷贝的坑"与"深拷贝的正确姿势"。</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        System.out.println("========== 原型模式演示 ==========\n");

        // ===== 场景 1：直接克隆 =====
        System.out.println("--- 场景 1：直接克隆 ---");
        List<String> images = new ArrayList<>();
        images.add("cover.png");

        WordDocument template = new WordDocument("招聘简历模板", "请填写个人信息", images);
        System.out.println("== 原始模板 ==");
        template.show();

        WordDocument doc1 = (WordDocument) template.cloneDocument();
        doc1.setTitle("鱼皮的简历");
        doc1.setContent("鱼皮，5 年全栈开发经验");
        doc1.addImage("yupi_avatar.png");

        WordDocument doc2 = (WordDocument) template.cloneDocument();
        doc2.setTitle("Yes 哥的简历");
        doc2.setContent("Yes 哥，8 年 Java 开发经验");
        doc2.addImage("yes_avatar.png");

        System.out.println("\n== 克隆出的鱼皮的简历 ==");
        doc1.show();
        System.out.println("\n== 克隆出的 Yes 哥的简历 ==");
        doc2.show();
        System.out.println("\n== 原始模板（未被污染）==");
        template.show();

        // ===== 场景 2：原型注册表 =====
        System.out.println("\n--- 场景 2：原型注册表 ---");
        DocumentRegistry registry = DocumentRegistry.withDefaults();

        WordDocument myResume = (WordDocument) registry.get("resume");
        myResume.setTitle("张三的简历");
        myResume.setContent("张三，3 年后端经验");
        myResume.show();

        WordDocument myReport = (WordDocument) registry.get("report");
        myReport.setTitle("第 42 周周报");
        myReport.setContent("完成设计模式重构");
        myReport.show();

        // ===== 场景 3：深拷贝验证 =====
        System.out.println("\n--- 场景 3：深拷贝验证 ---");
        System.out.println("加图前 template：" + template.getImages());
        System.out.println("加图前 doc1    ：" + doc1.getImages());

        doc1.addImage("new_image.png");

        System.out.println("加图后 doc1    ：" + doc1.getImages());
        System.out.println("加图后 template：" + template.getImages());
        System.out.println("（template 仍为 [cover.png]，说明两者的 images 互不影响）");

        // 为什么不用 template.getImages() == doc1.getImages() 来判断？
        // 因为 getImages() 返回的是只读副本，每次调用都是新对象，
        // 那样比出来永远是 false——断言永远成立，就证明不了任何事。
        // 真正的证据是行为：改一个，另一个不变。
    }
}
